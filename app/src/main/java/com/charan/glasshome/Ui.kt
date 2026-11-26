package com.charan.glasshome

import android.app.Activity
import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.TextView

/**
 * Design system — "Screening Room".
 * A velvet-black room, warm ivory type, light that spills from whatever is selected,
 * a whisper of film grain. Every screen uses these tokens so the TV feels like one piece.
 *
 * Layout is in dp on a 960x540 dp canvas (what every 720p / 1080p / 4K Android TV reports),
 * with 48 dp side and ~27 dp top/bottom safe margins so nothing is lost to TV overscan.
 */
object Ui {
    // colour
    const val IVORY = 0xFFF4EFE6.toInt()
    const val IVORY_MUTED = 0xB8F4EFE6.toInt()   // 72 %
    const val IVORY_FAINT = 0x80F4EFE6.toInt()   // 50 %
    const val HAIRLINE = 0x29F4EFE6.toInt()      // 16 %
    const val SCRIM = 0xB8000000.toInt()

    // type scale (sp) — sized to read from across the room
    const val DISPLAY = 80f
    const val TITLE = 60f
    const val HEADLINE = 36f
    const val BODY = 18f
    const val LABEL = 15f
    const val CAPTION = 13f

    // motion (ms)
    const val ENTER = 420L

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    /** Small capitals with wide tracking: chapter markers and section labels. */
    fun eyebrow(ctx: Context, text: String = "", color: Int = IVORY_FAINT): TextView = TextView(ctx).apply {
        this.text = text.uppercase()
        textSize = CAPTION
        letterSpacing = 0.28f
        typeface = Fonts.get(ctx, Fonts.MEDIUM)
        setTextColor(color)
        includeFontPadding = false
        maxLines = 1
    }

    /** Editorial serif title. */
    fun serif(ctx: Context, sizeSp: Float, italic: Boolean = false, color: Int = IVORY): TextView = TextView(ctx).apply {
        textSize = sizeSp
        typeface = Fonts.serif(ctx, italic)
        setTextColor(color)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    /** Clean sans text. */
    fun sans(ctx: Context, sizeSp: Float, weight: Int = Fonts.LIGHT, color: Int = IVORY_MUTED): TextView = TextView(ctx).apply {
        textSize = sizeSp
        typeface = Fonts.get(ctx, weight)
        setTextColor(color)
    }

    /** Page entrance: content rises softly into place. */
    fun enter(v: View, delay: Long = 0L) {
        v.alpha = 0f
        v.translationY = dpf(v.context, 26f)
        v.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(delay)
            .setDuration(ENTER)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** True full screen: hides any status / navigation bar (TVs usually have none; emulators do). */
    fun immersive(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    /**
     * Text fields on a TV: Up / Down leave the field straight away (instead of first moving the
     * cursor), and the cursor always starts at the end of the text.
     */
    fun tvTextField(edit: EditText) {
        edit.setOnKeyListener { v, keyCode, event ->
            val dir = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                else -> return@setOnKeyListener false
            }
            if (event.action == KeyEvent.ACTION_DOWN) v.focusSearch(dir)?.requestFocus()
            true
        }
    }
}
