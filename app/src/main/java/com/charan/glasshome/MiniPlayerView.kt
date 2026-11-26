package com.charan.glasshome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.sin

/**
 * The "now playing" glass card on the home screen: cover, title, artist, a little live equaliser,
 * previous / play-pause / next buttons and a thin progress line.
 */
class MiniPlayerView(
    context: Context,
    private val onPrev: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onButtonFocus: (View, Boolean) -> Unit,
) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private val art = ImageView(context)
    private val title = Ui.sans(context, 15f, Fonts.SEMIBOLD, Ui.IVORY)
    private val subtitle = Ui.sans(context, 13f, Fonts.LIGHT, Ui.IVORY_MUTED)
    private val eq = EqualizerView(context)
    private val progress = ProgressLine(context)
    private val playIcon = ImageView(context)
    val prevButton: View
    val playButton: View
    val nextButton: View

    init {
        background = context.getDrawable(R.drawable.mini_card)
        elevation = 10 * d
        clipChildren = false

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(12f), dp(16f), dp(14f))
            clipChildren = false
            clipToPadding = false
        }
        art.background = context.getDrawable(R.drawable.art_frame)
        art.clipToOutline = true
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        row.addView(art, LinearLayout.LayoutParams(dp(70f), dp(70f)))

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        subtitle.maxLines = 1
        subtitle.ellipsize = TextUtils.TruncateAt.END
        col.addView(title)
        val sub = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sub.addView(eq, LinearLayout.LayoutParams(dp(14f), dp(12f)))
        sub.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(7f) })
        col.addView(sub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(2f) })

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
        }
        prevButton = button(R.drawable.ic_prev, 30f, 15f, null) { onPrev() }
        playButton = button(R.drawable.ic_play, 36f, 18f, playIcon) { onPlayPause() }
        nextButton = button(R.drawable.ic_next, 30f, 15f, null) { onNext() }
        controls.addView(prevButton, LinearLayout.LayoutParams(dp(30f), dp(30f)))
        controls.addView(playButton, LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { marginStart = dp(12f) })
        controls.addView(nextButton, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { marginStart = dp(12f) })
        col.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4f) })

        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14f) })
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(progress, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2f), Gravity.BOTTOM).apply {
            setMargins(dp(22f), 0, dp(22f), dp(7f))
        })

        // keep left/right moving between the three buttons
        listOf(prevButton, playButton, nextButton).forEach { it.id = View.generateViewId() }
        prevButton.nextFocusLeftId = prevButton.id
        nextButton.nextFocusRightId = nextButton.id
    }

    fun bind(s: NowPlaying.State, fallbackArt: Drawable?, accent: Int) {
        title.text = s.title
        subtitle.text = if (s.full) s.subtitle else "${s.subtitle}  ·  ${s.appName}"
        if (s.art != null) art.setImageBitmap(s.art) else art.setImageDrawable(fallbackArt)
        playIcon.setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        eq.color = accent
        eq.playing = s.playing
        progress.color = accent
        progress.fraction = if (s.duration > 0) (s.position.toFloat() / s.duration).coerceIn(0f, 1f) else -1f
    }

    private fun button(icon: Int, sizeDp: Float, iconDp: Float, iconView: ImageView?, onClick: () -> Unit): View {
        val b = FrameLayout(context).apply {
            background = context.getDrawable(R.drawable.ctrl_btn)
            isFocusable = true
            isClickable = true
        }
        val iv = iconView ?: ImageView(context)
        iv.setImageResource(icon)
        b.addView(iv, LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
        b.setOnClickListener { onClick() }
        b.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.22f else 1f).scaleY(if (has) 1.22f else 1f)
                .setInterpolator(if (has) OvershootInterpolator(2f) else null).setDuration(200).start()
            onButtonFocus(v, has)
        }
        b.contentDescription = when (icon) {
            R.drawable.ic_prev -> "Previous"
            R.drawable.ic_next -> "Next"
            else -> "Play or pause"
        }
        return b
    }

    private fun dp(v: Float) = (v * d + 0.5f).toInt()
}

/** Three little bars that dance while music plays. */
class EqualizerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var t = 0f
    private var anim: ValueAnimator? = null

    var color: Int = Ui.IVORY
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    var playing = false
        set(value) {
            if (field == value) return
            field = value
            if (value && isAttachedToWindow) start() else stopAnim()
            invalidate()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (playing) start()
    }

    override fun onDetachedFromWindow() {
        stopAnim()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnim() {
        anim?.cancel()
        anim = null
    }

    override fun onDraw(canvas: Canvas) {
        val bw = width / 5f
        val r = bw / 2f
        for (i in 0 until 3) {
            val level = if (playing) 0.3f + 0.7f * abs(sin((t * 2 * Math.PI + i * 2.1).toFloat())) else 0.3f
            val h = height * level
            val x = i * bw * 2f
            rect.set(x, height - h, x + bw, height.toFloat())
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }
}

/** A hairline progress bar (hidden when the length isn't known). */
class ProgressLine(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26F4EFE6 }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var color: Int = Ui.IVORY
        set(value) {
            field = value
            fill.color = value
            invalidate()
        }

    /** 0..1, or < 0 to hide. */
    var fraction = -1f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (fraction < 0f) return
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, track)
        rect.set(0f, 0f, width * fraction, height.toFloat())
        canvas.drawRoundRect(rect, r, r, fill)
    }
}
