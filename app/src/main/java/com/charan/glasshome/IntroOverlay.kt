package com.charan.glasshome

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The welcome animation shown when the TV starts (and when it wakes up):
 * black screen → a neon clock ring sweeps in, the hands spin to the current time →
 * "Welcome home" + the owner's name → everything dissolves into the home screen.
 * About 3 seconds; any button skips it.
 */
class IntroOverlay(
    private val activity: Activity,
    private val root: FrameLayout,
    private val content: View,
    name: String,
    private val accent: Int,
    private val onDone: () -> Unit,
) {
    private val d = activity.resources.displayMetrics.density
    private val overlay = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
    private val glow = View(activity)
    private val clock = AnalogClockView(activity)
    private val hello = TextView(activity)
    private val who = TextView(activity)
    private var enter: AnimatorSet? = null
    private var finished = false

    init {
        glow.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 300f * d
            colors = intArrayOf((accent and 0x00FFFFFF) or 0x55000000, accent and 0x00FFFFFF)
        }
        glow.alpha = 0f
        overlay.addView(glow, FrameLayout.LayoutParams((600 * d).toInt(), (600 * d).toInt(), Gravity.CENTER))

        clock.accent = accent
        clock.introProgress = 0f

        hello.text = "Welcome home"
        hello.textSize = 60f
        hello.typeface = Fonts.serif(activity, italic = true)
        hello.setTextColor(Ui.IVORY)
        hello.includeFontPadding = false
        hello.alpha = 0f

        who.text = name.uppercase()
        who.textSize = 17f
        who.typeface = Fonts.get(activity, Fonts.SEMIBOLD)
        who.setTextColor(accent)
        who.letterSpacing = 0.75f
        who.alpha = 0f
        who.setShadowLayer(18f * d, 0f, 0f, accent)
        // room for the glow, otherwise it is cut off in a visible box while fading in
        who.setPadding((32 * d).toInt(), (18 * d).toInt(), (32 * d).toInt(), (18 * d).toInt())

        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        col.addView(clock, LinearLayout.LayoutParams((190 * d).toInt(), (190 * d).toInt()))
        col.addView(hello, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (26 * d).toInt() })
        if (name.isNotBlank()) {
            col.addView(who, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (-12 * d).toInt() })
        }
        overlay.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        overlay.addView(GrainView(activity), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun play() {
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.alpha = 0f
        content.translationY = 28f * d

        val clockIn = ObjectAnimator.ofFloat(clock, "introProgress", 0f, 1f).apply {
            duration = 1400
            interpolator = DecelerateInterpolator(1.2f)
        }
        val glowIn = ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 1f).apply { duration = 1400 }
        val helloIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(hello, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(hello, View.TRANSLATION_Y, 16f * d, 0f),
            )
            startDelay = 650
            duration = 800
            interpolator = DecelerateInterpolator()
        }
        val whoIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(who, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(who, "letterSpacing", 0.75f, 0.38f),
            )
            startDelay = 1050
            duration = 1000
            interpolator = DecelerateInterpolator()
        }
        enter = AnimatorSet().apply {
            playTogether(clockIn, glowIn, helloIn, whoIn)
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) overlay.postDelayed({ exit(fast = false) }, 650)
                }
            })
            start()
        }
    }

    /** Any button press: jump straight to the home screen. */
    fun skip() = exit(fast = true)

    private fun exit(fast: Boolean) {
        if (finished) return
        finished = true
        enter?.cancel()
        val dur = if (fast) 260L else 700L
        overlay.animate()
            .alpha(0f).scaleX(1.08f).scaleY(1.08f)
            .setDuration(dur)
            .withEndAction {
                root.removeView(overlay)
                onDone()
            }
            .start()
        content.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(if (fast) 0L else 120L)
            .setDuration(dur + 200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
