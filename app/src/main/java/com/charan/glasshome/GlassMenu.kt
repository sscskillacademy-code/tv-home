package com.charan.glasshome

import android.app.Activity
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A floating glass menu (replaces the system dialogs): serif title, large options,
 * focus is kept inside the menu, Back closes it and focus returns where it was.
 */
class GlassMenu(
    private val activity: Activity,
    private val title: String,
    private val subtitle: String?,
    private val options: List<String>,
    private val accent: Int,
    private val onPick: (Int) -> Unit,
) {
    private val root: FrameLayout = activity.findViewById(android.R.id.content)
    private val scrim = FrameLayout(activity)
    private var previousFocus: View? = null
    var isShowing = false
        private set

    fun show() {
        if (isShowing) return
        isShowing = true
        previousFocus = activity.currentFocus
        val ctx = activity

        scrim.setBackgroundColor(Ui.SCRIM)
        scrim.isClickable = true

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ctx.getDrawable(R.drawable.menu_panel)
            setPadding(Ui.dp(ctx, 30f), Ui.dp(ctx, 26f), Ui.dp(ctx, 30f), Ui.dp(ctx, 20f))
            elevation = Ui.dpf(ctx, 24f)
        }
        panel.addView(View(ctx).apply { setBackgroundColor(accent) },
            LinearLayout.LayoutParams(Ui.dp(ctx, 40f), Ui.dp(ctx, 3f)).apply { bottomMargin = Ui.dp(ctx, 18f) })
        panel.addView(Ui.serif(ctx, Ui.HEADLINE + 4).apply { text = title })
        if (!subtitle.isNullOrBlank()) {
            panel.addView(Ui.sans(ctx, Ui.LABEL).apply { text = subtitle },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = Ui.dp(ctx, 4f) })
        }

        val rows = options.mapIndexed { i, label -> row(label, i) }
        rows.forEachIndexed { i, r ->
            panel.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 52f))
                .apply { topMargin = Ui.dp(ctx, if (i == 0) 18f else 4f) })
        }
        // keep focus inside the menu
        rows.forEachIndexed { i, r ->
            r.nextFocusLeftId = r.id
            r.nextFocusRightId = r.id
            r.nextFocusUpId = if (i == 0) r.id else rows[i - 1].id
            r.nextFocusDownId = if (i == rows.size - 1) r.id else rows[i + 1].id
        }

        scrim.addView(panel, FrameLayout.LayoutParams(Ui.dp(ctx, 460f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        scrim.alpha = 0f
        panel.scaleX = 0.95f
        panel.scaleY = 0.95f
        scrim.animate().alpha(1f).setDuration(200).start()
        panel.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(DecelerateInterpolator(2f)).start()
        rows.firstOrNull()?.requestFocus()
    }

    fun dismiss() = close(null)

    private fun close(then: (() -> Unit)?) {
        if (!isShowing) return
        isShowing = false
        scrim.animate().alpha(0f).setDuration(180).withEndAction { root.removeView(scrim) }.start()
        previousFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
        then?.invoke()
    }

    private fun row(label: String, index: Int) = TextView(activity).apply {
        id = View.generateViewId()
        text = label
        textSize = Ui.BODY + 1
        typeface = Fonts.get(activity, Fonts.MEDIUM)
        setTextColor(Ui.IVORY_MUTED)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(Ui.dp(activity, 18f), 0, Ui.dp(activity, 18f), 0)
        background = activity.getDrawable(R.drawable.menu_row)
        isFocusable = true
        isClickable = true
        setOnFocusChangeListener { v, has ->
            (v as TextView).setTextColor(if (has) Ui.IVORY else Ui.IVORY_MUTED)
        }
        setOnClickListener { close { onPick(index) } }
        setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_BACK) {
                if (ev.action == KeyEvent.ACTION_UP) dismiss()
                true
            } else {
                false
            }
        }
    }
}
