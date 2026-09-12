package com.retra.emulator

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import org.json.JSONObject
import androidx.core.view.WindowInsetsCompat
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Retra-native replacement for Android Toast.
 *
 * Notifications are rendered inside the Activity so they remain visible above
 * both the WebView library/settings UI and native gameplay surfaces. This also
 * keeps every native message inside Retra's visual language instead of showing
 * the platform's white Toast bubble.
 */
object RetraNotice {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    private const val NOTICE_TAG = "retra-native-notice"
    private val activeNotices = WeakHashMap<Activity, WeakReference<View>>()

    enum class Kind {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    class PendingNotice internal constructor(
        private val activity: Activity,
        private val message: String,
        private val duration: Int,
        private val kind: Kind?
    ) {
        fun show() {
            showInternal(activity, message, duration, kind ?: inferKind(message))
        }
    }

    fun makeText(
        activity: Activity,
        message: CharSequence,
        duration: Int
    ): PendingNotice = PendingNotice(activity, message.toString(), duration, null)

    fun show(
        activity: Activity,
        message: CharSequence,
        kind: Kind = inferKind(message.toString()),
        duration: Int = LENGTH_SHORT
    ) {
        showInternal(activity, message.toString(), duration, kind)
    }

    private fun showInternal(
        activity: Activity,
        message: String,
        duration: Int,
        kind: Kind
    ) {
        if (message.isBlank() || activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

            // When Retra's WebView surface is visible, use the exact same in-app
            // notification component as the rest of the UI. Native gameplay can hide
            // the WebView, so the native overlay below remains the fallback there.
            if (activity is MainActivity) {
                val webView = runCatching { activity.binding.webView }.getOrNull()
                if (webView != null && webView.visibility == View.VISIBLE && webView.url != null) {
                    val jsMessage = JSONObject.quote(message)
                    val jsKind = kind.name.lowercase()
                    val visibleFor = if (duration == LENGTH_LONG) 3600 else 2300
                    webView.evaluateJavascript(
                        "if (typeof showToast === 'function') showToast($jsMessage, '$jsKind', $visibleFor);",
                        null
                    )
                    return@runOnUiThread
                }
            }

            val host = activity.findViewById<FrameLayout>(android.R.id.content) ?: return@runOnUiThread
            activeNotices.remove(activity)?.get()?.let { previous ->
                (previous.parent as? ViewGroup)?.removeView(previous)
            }
            host.findViewWithTag<View>(NOTICE_TAG)?.let { stale ->
                (stale.parent as? ViewGroup)?.removeView(stale)
            }

            val accentColor = when (kind) {
                Kind.SUCCESS -> Color.rgb(75, 201, 137)
                Kind.WARNING -> Color.rgb(240, 182, 109)
                Kind.ERROR -> Color.rgb(238, 101, 113)
                Kind.INFO -> Color.rgb(116, 216, 229)
            }

            val card = LinearLayout(activity).apply {
                tag = NOTICE_TAG
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                alpha = 0f
                translationY = dp(activity, 12).toFloat()
                elevation = dp(activity, 10).toFloat()
                isClickable = false
                isFocusable = false
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                contentDescription = message
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(activity, 14).toFloat()
                    setColor(Color.rgb(37, 39, 65))
                    setStroke(dp(activity, 1), accentColor)
                }
                setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10))
            }

            val maxNoticeWidth = (activity.resources.displayMetrics.widthPixels - dp(activity, 36))
                .coerceAtMost(dp(activity, 420))
                .coerceAtLeast(dp(activity, 120))
            val text = TextView(activity).apply {
                setText(message)
                setTextColor(Color.rgb(245, 245, 250))
                textSize = 12.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 3
                maxWidth = (maxNoticeWidth - dp(activity, 32)).coerceAtLeast(dp(activity, 88))
                includeFontPadding = false
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            card.addView(
                text,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )

            val navInset = ViewCompat.getRootWindowInsets(host)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                ?.bottom ?: 0
            val horizontalMargin = dp(activity, 18)
            val noticeBottomMargin = navInset + dp(activity, 82)

            host.addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply {
                    marginStart = horizontalMargin
                    marginEnd = horizontalMargin
                    this.bottomMargin = noticeBottomMargin
                }
            )
            activeNotices[activity] = WeakReference(card)

            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .start()

            val visibleFor = if (duration == LENGTH_LONG) 3600L else 2300L
            card.postDelayed({
                if (card.parent == null) return@postDelayed
                card.animate()
                    .alpha(0f)
                    .translationY(dp(activity, 8).toFloat())
                    .setDuration(180L)
                    .withEndAction {
                        (card.parent as? ViewGroup)?.removeView(card)
                        if (activeNotices[activity]?.get() === card) activeNotices.remove(activity)
                    }
                    .start()
            }, visibleFor)
        }
    }

    internal fun inferKind(message: String): Kind {
        val normalized = message.lowercase()
        return when {
            listOf(
                "could not", "failed", "failure", "error", "invalid", "expired",
                "unavailable", "unsupported", "not granted", "not recognized"
            ).any(normalized::contains) -> Kind.ERROR

            listOf(
                "disabled", "requires", "choose ", "turn ", "allow ", "pair ",
                "empty", "no quick", "not supported", "different rom"
            ).any(normalized::contains) -> Kind.WARNING

            listOf(
                "saved", "selected", "connected", "complete", "exported", "deleted",
                "reset", "added", "installed", "player ", "reconnected"
            ).any(normalized::contains) -> Kind.SUCCESS

            else -> Kind.INFO
        }
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()
}
