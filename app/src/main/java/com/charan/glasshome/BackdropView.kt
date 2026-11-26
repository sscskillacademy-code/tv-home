package com.charan.glasshome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max

/**
 * The full-screen background:
 *  1. a theme gradient with soft colour glows (drawn ONCE — nothing animates while idle),
 *  2. the focused app's artwork, blurred and enlarged, cross-fading when focus moves,
 *  3. a radial glow in the focused app's colour behind the title.
 */
class BackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var palette: Themes.Palette = Themes.ALL[0]
        set(value) {
            field = value
            base = null
            invalidate()
        }

    /** How strongly the app artwork shows (0..1) — kept low: light in a dark room, not a wall of colour. */
    var artStrength = 0.5f

    /** Where the colour glow sits, as a fraction of the screen. */
    var glowX = 0.52f
    var glowY = 0.55f

    private var base: Bitmap? = null
    private var artFrom: Bitmap? = null
    private var artTo: Bitmap? = null
    private var glowFrom = Color.TRANSPARENT
    private var glowTo = Color.TRANSPARENT
    private var mix = 1f
    private var fade: ValueAnimator? = null

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val dst = Rect()

    /** Show an app's artwork + glow colour (null art = just the theme). */
    fun show(art: Bitmap?, glow: Int, animate: Boolean = true) {
        if (art === artTo && glow == glowTo) return
        fade?.cancel()
        // whatever is visible now becomes the starting point
        if (mix >= 0.5f) {
            artFrom = artTo
        }
        glowFrom = blend(glowFrom, glowTo, mix)
        artTo = art
        glowTo = glow
        if (!animate) {
            mix = 1f
            invalidate()
            return
        }
        mix = 0f
        fade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                mix = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        base = null
    }

    override fun onDetachedFromWindow() {
        fade?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val b = base ?: renderBase().also { base = it }
        dst.set(0, 0, width, height)
        canvas.drawBitmap(b, null, dst, bmpPaint)

        artFrom?.let {
            bmpPaint.alpha = ((1f - mix) * artStrength * 255).toInt()
            if (bmpPaint.alpha > 0) canvas.drawBitmap(it, null, dst, bmpPaint)
        }
        artTo?.let {
            bmpPaint.alpha = (mix * artStrength * 255).toInt()
            if (bmpPaint.alpha > 0) canvas.drawBitmap(it, null, dst, bmpPaint)
        }
        bmpPaint.alpha = 255

        val g = blend(glowFrom, glowTo, mix)
        if (Color.alpha(g) > 0) {
            val cx = width * glowX
            val cy = height * glowY
            val r = width * 0.42f
            val inner = Color.argb(Color.alpha(g) * 85 / 255, Color.red(g), Color.green(g), Color.blue(g))
            val outer = Color.argb(0, Color.red(g), Color.green(g), Color.blue(g))
            glowPaint.shader = RadialGradient(cx, cy, r, inner, outer, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, glowPaint)
        }
    }

    /** Theme background, rendered once at 1/4 size and scaled up (soft by nature). */
    private fun renderBase(): Bitmap {
        val bw = max(1, width / 4)
        val bh = max(1, height / 4)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        p.shader = LinearGradient(0f, 0f, bw * 0.3f, bh.toFloat(), palette.top, palette.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)

        val spots = arrayOf(floatArrayOf(0.12f, 0.18f), floatArrayOf(0.88f, 0.22f), floatArrayOf(0.62f, 1.0f))
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        for (i in spots.indices) {
            val col = palette.glows[i % palette.glows.size]
            val cx = bw * spots[i][0]
            val cy = bh * spots[i][1]
            val r = bw * 0.55f
            p.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(col, Color.argb(Color.alpha(col) / 3, Color.red(col), Color.green(col), Color.blue(col)), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, r, p)
        }
        return bmp
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        // fade from/to "nothing" keeps the hue instead of passing through grey
        val from = if (Color.alpha(a) == 0) b and 0x00FFFFFF else a
        val to = if (Color.alpha(b) == 0) a and 0x00FFFFFF else b
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return Color.argb(
            ch(Color.alpha(from), Color.alpha(to)), ch(Color.red(from), Color.red(to)),
            ch(Color.green(from), Color.green(to)), ch(Color.blue(from), Color.blue(to)),
        )
    }
}
