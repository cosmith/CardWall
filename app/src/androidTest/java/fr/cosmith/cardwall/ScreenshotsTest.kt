package fr.cosmith.cardwall

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.cosmith.cardwall.data.CardWallPreferences
import fr.cosmith.cardwall.gate.GateOverlayView
import fr.cosmith.cardwall.model.ReviewCard
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Captures production UI with a synthetic card; no Anki collection is read or modified. */
@RunWith(AndroidJUnit4::class)
class ScreenshotsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun captureReadmeScreenshots() {
        val context = instrumentation.targetContext
        CardWallPreferences(context).saveSkipEnabled(true)
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            capture("settings")
            lateinit var gate: GateOverlayView
            instrumentation.runOnMainSync {
                gate = GateOverlayView(
                    context = activity,
                    appLabel = "Social app",
                    requiredReviews = 3,
                    skipEnabled = true,
                    accessMinutes = 10,
                    onCloseApp = {},
                    onBack = {},
                    onReveal = { gate.revealAnswer() },
                    onGrade = {},
                    onReviewed = {},
                    onSkip = {},
                )
                activity.setContentView(gate)
                gate.showCard(
                    ReviewCard(
                        cardId = 1,
                        noteId = 1,
                        cardOrd = 0,
                        questionHtml = "How do you say<br><b>\"a little every day\"</b><br>in Italian?",
                        answerHtml = "Un po’ ogni giorno.<hr>A little every day.",
                        buttonCount = 4,
                        nextReviewTimes = listOf("1 min", "6 min", "10 min", "4 days"),
                        scheduled = true,
                    ),
                    completed = 1,
                )
            }
            waitForCard(gate)
            capture("question")
            instrumentation.runOnMainSync { gate.revealAnswer() }
            waitForCard(gate)
            capture("answer")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun waitForCard(root: View) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var loaded = false
            instrumentation.runOnMainSync { loaded = findWebView(root)?.progress == 100 }
            if (loaded) return
            SystemClock.sleep(100)
        }
        error("Card WebView did not finish loading")
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(1500) // Let WebView and Compose finish drawing before capture.
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
