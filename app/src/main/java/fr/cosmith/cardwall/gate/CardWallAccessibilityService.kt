package fr.cosmith.cardwall.gate

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import fr.cosmith.cardwall.anki.AnkiRepository
import fr.cosmith.cardwall.anki.AnkiUnavailableException
import fr.cosmith.cardwall.data.CardWallPreferences
import fr.cosmith.cardwall.model.ReviewCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class CardWallAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: CardWallPreferences
    private lateinit var ankiRepository: AnkiRepository

    private val accessUntilElapsed = ConcurrentHashMap<String, Long>()
    private val expiryCallbacks = mutableMapOf<String, Runnable>()
    private var overlay: GateOverlayView? = null
    private var activeCard: ReviewCard? = null
    private var activeGatePackage: String? = null
    private var activeCardStartedElapsed = 0L
    private var completedReviews = 0
    private var loadJob: Job? = null
    private var currentForegroundPackage: String? = null
    private var screenReceiverRegistered = false

    internal val activeTargetPackage: String get() = activeGatePackage.orEmpty()

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) abandonGate()
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferences = CardWallPreferences(this)
        ankiRepository = AnkiRepository(this)
        if (!screenReceiverRegistered) {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()?.takeIf(String::isNotBlank) ?: return
        if (packageName in TRANSIENT_SYSTEM_PACKAGES || packageName == this.packageName) return
        currentForegroundPackage = packageName

        val gatePackage = activeGatePackage
        if (gatePackage != null) {
            if (packageName != gatePackage) abandonGate()
            return
        }

        val settings = preferences.load()
        if (packageName !in settings.targetPackages) return
        val unlockedUntil = accessUntilElapsed[packageName] ?: 0L
        if (SystemClock.elapsedRealtime() < unlockedUntil) return
        beginGate(packageName)
    }

    override fun onInterrupt() {
        abandonGate()
    }

    override fun onDestroy() {
        abandonGate()
        expiryCallbacks.values.forEach(mainHandler::removeCallbacks)
        expiryCallbacks.clear()
        accessUntilElapsed.clear()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        screenReceiverRegistered = false
        scope.cancel()
        super.onDestroy()
    }

    private fun beginGate(targetPackage: String) {
        if (overlay != null) return
        val settings = preferences.load()
        if (settings.selectedDeckIds.isEmpty()) {
            failOpen(targetPackage, "Select at least one Anki deck in CardWall")
            return
        }

        activeGatePackage = targetPackage
        completedReviews = 0
        activeCard = null
        val label =
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString()
            } catch (_: Throwable) {
                targetPackage
            }

        val view =
            GateOverlayView(
                context = this,
                appLabel = label,
                requiredReviews = settings.requiredReviews,
                skipEnabled = settings.skipEnabled,
                accessMinutes = settings.accessMinutes,
                onCloseApp = {
                    abandonGate()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                },
                onBack = {
                    abandonGate()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                },
                onReveal = { overlay?.revealAnswer() },
                onGrade = ::gradeScheduledCard,
                onReviewed = ::completeFallbackCard,
                onSkip = ::skipGate,
            )
        overlay = view
        windowManager.addView(view, overlayLayoutParams())
        loadNextCard()
    }

    private fun loadNextCard() {
        val packageName = activeGatePackage ?: return
        val settings = preferences.load()
        overlay?.showLoading(completedReviews)
        loadJob?.cancel()
        loadJob =
            scope.launch {
                try {
                    val card = withContext(Dispatchers.IO) { ankiRepository.nextCard(settings.selectedDeckIds) }
                    if (activeGatePackage != packageName) return@launch
                    if (card == null) {
                        failOpen(packageName, "No eligible cards are available in the selected decks")
                        return@launch
                    }
                    activeCard = card
                    activeCardStartedElapsed = SystemClock.elapsedRealtime()
                    overlay?.showCard(card, completedReviews)
                } catch (error: AnkiUnavailableException) {
                    failOpen(packageName, error.message ?: "AnkiDroid is unavailable")
                }
            }
    }

    private fun gradeScheduledCard(ease: Int) {
        val packageName = activeGatePackage ?: return
        val card = activeCard?.takeIf { it.scheduled } ?: return
        overlay?.showLoading(completedReviews)
        loadJob =
            scope.launch {
                try {
                    val timeTaken = SystemClock.elapsedRealtime() - activeCardStartedElapsed
                    val saved = withContext(Dispatchers.IO) { ankiRepository.submitGrade(card, ease, timeTaken) }
                    if (!saved) throw AnkiUnavailableException("AnkiDroid did not save the review")
                    countCompletedReview(packageName)
                } catch (error: AnkiUnavailableException) {
                    failOpen(packageName, error.message ?: "Could not save the Anki review")
                }
            }
    }

    private fun completeFallbackCard() {
        val packageName = activeGatePackage ?: return
        if (activeCard?.scheduled != false) return
        countCompletedReview(packageName)
    }

    private fun countCompletedReview(packageName: String) {
        if (activeGatePackage != packageName) return
        completedReviews++
        val settings = preferences.load()
        if (completedReviews >= settings.requiredReviews) {
            grantAccess(packageName, settings.accessMinutes)
            removeGateOverlay()
        } else {
            loadNextCard()
        }
    }

    private fun skipGate() {
        val packageName = activeGatePackage ?: return
        val settings = preferences.load()
        if (!settings.skipEnabled) return
        grantAccess(packageName, settings.accessMinutes)
        removeGateOverlay()
    }

    private fun failOpen(packageName: String, warning: String) {
        val settings = preferences.load()
        grantAccess(packageName, settings.accessMinutes)
        removeGateOverlay()
        Toast.makeText(this, "CardWall allowed access: $warning", Toast.LENGTH_LONG).show()
    }

    private fun grantAccess(packageName: String, minutes: Int) {
        val durationMs = minutes.coerceIn(1, 120) * 60_000L
        val expiresAt = SystemClock.elapsedRealtime() + durationMs
        accessUntilElapsed[packageName] = expiresAt
        expiryCallbacks.remove(packageName)?.let(mainHandler::removeCallbacks)
        val callback =
            Runnable {
                accessUntilElapsed.remove(packageName)
                expiryCallbacks.remove(packageName)
                if (currentForegroundPackage == packageName && activeGatePackage == null) beginGate(packageName)
            }
        expiryCallbacks[packageName] = callback
        // Handler uses uptime (which pauses during deep sleep), while access expiry is
        // checked against elapsedRealtime. A delayed callback handles active use; the
        // foreground-event check catches an expiry that happened while the screen slept.
        mainHandler.postDelayed(callback, durationMs)
    }

    private fun abandonGate() {
        removeGateOverlay()
    }

    private fun removeGateOverlay() {
        loadJob?.cancel()
        loadJob = null
        overlay?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Throwable) {
                // The system may already have removed the accessibility overlay.
            }
        }
        overlay = null
        activeCard = null
        activeGatePackage = null
        completedReviews = 0
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "CardWall flashcard gate"
            // Accessibility overlays are fitted below system bars by default. Let the
            // window fill the display, then GateOverlayView applies the insets as
            // internal padding so its background also hides edge-to-edge app content.
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

    companion object {
        private val TRANSIENT_SYSTEM_PACKAGES =
            setOf(
                "com.android.systemui",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "com.google.android.inputmethod.latin",
                "com.android.inputmethod.latin",
            )
    }
}
