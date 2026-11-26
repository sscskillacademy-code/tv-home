package com.charan.glasshome

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView

/** A quiet glass pill for short messages ("Saved", "Can't open …") instead of the system toast. */
object Notice {
    private const val TAG = "glass_notice"

    fun show(activity: Activity, message: String, bottomDp: Float = 64f) {
        val root = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        root.findViewWithTag<View>(TAG)?.let { root.removeView(it) }

        val pill = TextView(activity).apply {
            tag = TAG
            text = message
            textSize = Ui.BODY - 1
            typeface = Fonts.get(activity, Fonts.MEDIUM)
            setTextColor(Ui.IVORY)
            background = activity.getDrawable(R.drawable.notice_bg)
            setPadding(Ui.dp(activity, 28f), Ui.dp(activity, 14f), Ui.dp(activity, 28f), Ui.dp(activity, 14f))
            elevation = Ui.dpf(activity, 10f)
            alpha = 0f
            translationY = Ui.dpf(activity, 16f)
            isFocusable = false
        }
        root.addView(pill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = Ui.dp(activity, bottomDp) })

        pill.animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
        pill.postDelayed({
            pill.animate().alpha(0f).translationY(Ui.dpf(activity, 10f)).setDuration(260)
                .withEndAction { root.removeView(pill) }.start()
        }, 2800)
    }
}
