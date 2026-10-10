package fr.cosmith.cardwall.gate

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import fr.cosmith.cardwall.model.ReviewCard
import java.io.ByteArrayInputStream
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import fr.cosmith.cardwall.R

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class GateOverlayView(
    context: Context,
    private val appLabel: String,
    private val requiredReviews: Int,
    private val skipEnabled: Boolean,
    private val accessMinutes: Int,
    private val onCloseApp: () -> Unit,
    private val onBack: () -> Unit,
    private val onReveal: () -> Unit,
    private val onGrade: (Int) -> Unit,
    private val onReviewed: () -> Unit,
    private val onSkip: () -> Unit,
) : FrameLayout(context) {
    private val darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val backgroundColor = if (darkMode) Color.rgb(16, 21, 18) else Color.rgb(247, 248, 244)
    private val surfaceColor = if (darkMode) Color.rgb(26, 34, 29) else Color.WHITE
    private val textColor = if (darkMode) Color.rgb(238, 244, 240) else Color.rgb(28, 33, 30)
    private val secondaryTextColor = if (darkMode) Color.rgb(182, 194, 187) else Color.rgb(84, 95, 89)
    private val primaryColor = Color.rgb(88, 204, 2)
    private val roundedTypeface = Typeface.create("sans-serif-rounded", Typeface.NORMAL)

    private val progressText = textView(sizeSp = 14f, color = secondaryTextColor)
    private val reviewProgress =
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = requiredReviews
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    if (darkMode) Color.rgb(43, 55, 48) else Color.rgb(221, 229, 224),
                )
            background = roundedBackground(
                if (darkMode) Color.rgb(43, 55, 48) else Color.rgb(221, 229, 224),
                dp(6).toFloat(),
            )
            clipToOutline = true
        }
    private val cardWebView = WebView(context)
    private val actions = LinearLayout(context)
    private val loading = ProgressBar(context)
    private var currentCard: ReviewCard? = null
    private var answerVisible = false
    private var skipConfirmation: AlertDialog? = null
    private val skipButton = Button(context).apply {
        setText(R.string.skip_gate)
        isAllCaps = false
        textSize = 14f
        typeface = roundedTypeface
        setTextColor(secondaryTextColor)
        setBackgroundColor(Color.TRANSPARENT)
        visibility = if (skipEnabled) View.VISIBLE else View.GONE
        setOnClickListener { confirmSkip() }
    }
    private val predictiveBackCallback = OnBackInvokedCallback { onBack() }

    init {
        setBackgroundColor(backgroundColor)
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS

        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(18))
            }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnApplyWindowInsetsListener { _, insets ->
            val safeInsets =
                insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
            content.setPadding(
                dp(20) + safeInsets.left,
                dp(14) + safeInsets.top,
                dp(20) + safeInsets.right,
                dp(18) + safeInsets.bottom,
            )
            insets
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon =
            ImageView(context).apply {
                try {
                    setImageDrawable(context.packageManager.getApplicationIcon((context as CardWallAccessibilityService).activeTargetPackage))
                } catch (_: Throwable) {
                    setImageResource(android.R.drawable.sym_def_app_icon)
                }
            }
        header.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val heading =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                addView(
                    textView(sizeSp = 17f, color = textColor).apply {
                        text = appLabel
                        setTypeface(roundedTypeface, Typeface.BOLD)
                    },
                )
                addView(progressText)
            }
        header.addView(heading, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            Button(context).apply {
                setText(R.string.close_app)
                isAllCaps = false
                minHeight = 0
                minWidth = 0
                setPadding(dp(16), 0, dp(16), 0)
                setTextColor(textColor)
                textSize = 14f
                setTypeface(roundedTypeface, Typeface.BOLD)
                background =
                    roundedBackground(
                        if (darkMode) Color.rgb(43, 52, 47) else Color.rgb(232, 236, 233),
                        dp(14).toFloat(),
                    )
                setOnClickListener { onCloseApp() }
            },
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)),
        )
        content.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(
            reviewProgress,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(12) },
        )

        cardWebView.apply {
            setBackgroundColor(surfaceColor)
            settings.javaScriptEnabled = true
            settings.blockNetworkLoads = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val scheme = request?.url?.scheme.orEmpty()
                        return if (scheme in setOf("about", "data")) {
                            super.shouldInterceptRequest(view, request)
                        } else {
                            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                }
            background = roundedBackground(surfaceColor, dp(18).toFloat())
            clipToOutline = true
        }
        val cardParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(18)
            bottomMargin = dp(16)
        }
        content.addView(cardWebView, cardParams)

        actions.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        content.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        loading.visibility = View.GONE
        content.addView(
            loading,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply { gravity = Gravity.CENTER_HORIZONTAL },
        )
        content.addView(skipButton, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        showLoading(completed = 0)
        post { requestFocus() }
    }

    fun showLoading(completed: Int) {
        skipButton.isEnabled = false
        reviewProgress.progress = completed.coerceAtMost(requiredReviews)
        progressText.text = resources.getString(
            R.string.review_progress,
            completed.coerceAtMost(requiredReviews),
            requiredReviews,
        )
        loading.visibility = View.VISIBLE
        actions.visibility = View.GONE
    }

    fun showCard(card: ReviewCard, completed: Int) {
        skipButton.isEnabled = true
        currentCard = card
        answerVisible = false
        reviewProgress.progress = completed.coerceAtMost(requiredReviews)
        progressText.text = resources.getString(R.string.review_progress, completed, requiredReviews)
        loading.visibility = View.GONE
        actions.visibility = View.VISIBLE
        loadHtml(card.questionHtml)
        showRevealAction()
    }

    fun revealAnswer() {
        val card = currentCard ?: return
        answerVisible = true
        loadHtml(card.answerHtml)
        showAnswerActions(card)
    }

    private fun confirmSkip() {
        if (!skipEnabled || !skipButton.isEnabled || skipConfirmation != null || !isAttachedToWindow) return
        val theme = if (darkMode) {
            android.R.style.Theme_Material_Dialog_Alert
        } else {
            android.R.style.Theme_Material_Light_Dialog_Alert
        }
        val dialog = AlertDialog.Builder(context, theme)
            .setTitle(R.string.skip_confirmation_title)
            .setMessage(resources.getString(R.string.skip_confirmation_message, appLabel, accessMinutes))
            .setNegativeButton(R.string.keep_reviewing, null)
            .setPositiveButton(R.string.skip_gate) { _, _ -> onSkip() }
            .create()
        // This view is hosted by a service, so its dialog also needs an
        // accessibility-overlay window rather than an Activity window token.
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.setOnDismissListener { skipConfirmation = null }
        skipConfirmation = dialog
        dialog.show()
    }

    private fun showRevealAction() {
        actions.removeAllViews()
        actions.addView(
            actionButton("Show answer", primary = true) {
                onReveal()
            },
            LinearLayout.LayoutParams(0, dp(60), 1f),
        )
    }

    private fun showAnswerActions(card: ReviewCard) {
        actions.removeAllViews()
        if (!card.scheduled) {
            actions.addView(
                actionButton("Reviewed", primary = true) { onReviewed() },
                LinearLayout.LayoutParams(0, dp(60), 1f),
            )
            return
        }

        val labels = listOf("Again", "Hard", "Good", "Easy")
        val count = card.buttonCount.coerceIn(2, 4)
        repeat(count) { index ->
            val label = labels[index]
            val interval = card.nextReviewTimes.getOrNull(index).orEmpty()
            val buttonText = if (interval.isBlank()) label else "$label\n$interval"
            val params = LinearLayout.LayoutParams(0, dp(68), 1f).apply {
                if (index > 0) marginStart = dp(6)
            }
            actions.addView(actionButton(buttonText, primary = index == 2) { onGrade(index + 1) }, params)
        }
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            minHeight = 0
            minWidth = 0
            setTypeface(roundedTypeface, Typeface.BOLD)
            setTextColor(if (primary) Color.rgb(22, 63, 10) else textColor)
            background =
                roundedBackground(
                    if (primary) primaryColor else if (darkMode) Color.rgb(48, 58, 52) else Color.rgb(229, 234, 230),
                    dp(16).toFloat(),
                )
            elevation = dp(2).toFloat()
            setOnClickListener { action() }
        }

    private fun loadHtml(html: String) {
        val wrapper =
            """
            <!doctype html>
            <html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=5">
            <style>
            html,body{height:100%;margin:0;background:${cssColor(surfaceColor)};color:${cssColor(textColor)};}
            body{box-sizing:border-box;display:flex;padding:32px;overflow:auto;overflow-wrap:anywhere;}
            #cardwall-content{
              box-sizing:border-box;width:100%;max-width:760px;margin:auto;
              font-family:'sans-serif-rounded','Avenir Next','Trebuchet MS',sans-serif !important;
              font-size:clamp(30px,6vw,48px);font-weight:650;line-height:1.35;text-align:center !important;
            }
            #cardwall-content *{font-family:inherit !important;text-align:center !important;}
            #cardwall-content hr{width:min(76%,520px);margin:28px auto;border:0;border-top:2px solid ${cssColor(secondaryTextColor)};opacity:.7;}
            #cardwall-content table{margin-left:auto !important;margin-right:auto !important;}
            img,svg,picture,video,audio,object,embed{max-width:100%;height:auto;}
            .cardwall-media-placeholder{display:block;margin:12px 0;padding:12px;border:1px dashed #8b968f;border-radius:10px;color:#66716b;font-size:14px;}
            @media (orientation:landscape){
              body{padding:20px 30px;}
              #cardwall-content{font-size:clamp(26px,4vw,40px);}
              #cardwall-content hr{margin:20px auto;}
            }
            </style>
            <script>
            window.addEventListener('DOMContentLoaded', function () {
              document.querySelectorAll('img,svg,picture,video,audio,object,embed').forEach(function (node) {
                var placeholder = document.createElement('span');
                placeholder.className = 'cardwall-media-placeholder';
                placeholder.textContent = node.getAttribute('alt') || '[Media omitted in CardWall v0.1]';
                node.replaceWith(placeholder);
              });
            });
            </script></head><body><main id="cardwall-content">$html</main></body></html>
            """.trimIndent()
        cardWebView.loadDataWithBaseURL("https://cardwall.invalid/", wrapper, "text/html", "utf-8", null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val lightBars =
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        windowInsetsController?.setSystemBarsAppearance(
            if (darkMode) 0 else lightBars,
            lightBars,
        )
        requestApplyInsets()
        findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            predictiveBackCallback,
        )
    }

    override fun onDetachedFromWindow() {
        skipConfirmation?.dismiss()
        skipConfirmation = null
        findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(predictiveBackCallback)
        cardWebView.stopLoading()
        cardWebView.destroy()
        super.onDetachedFromWindow()
    }

    private fun textView(sizeSp: Float, color: Int): TextView =
        TextView(context).apply {
            textSize = sizeSp
            setTextColor(color)
            typeface = roundedTypeface
        }

    private fun roundedBackground(color: Int, radius: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun cssColor(color: Int): String = String.format("#%06X", 0xFFFFFF and color)
}
