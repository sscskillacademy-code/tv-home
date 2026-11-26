package com.charan.glasshome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Glass analog clock with a neon ring in the accent colour.
 * Redraws once per second only (cheap). [introProgress] < 1 draws the "build-up" used by the
 * welcome animation: the ring sweeps in and the hands spin to the current time.
 * With [halo] on, the clock floats in a soft glow of its accent colour (no card needed).
 */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val d = resources.displayMetrics.density

    var accent: Int = 0xFF8FD8FF.toInt()
        set(value) {
            field = value
            ringCore.color = value
            ringGlow1.color = withAlpha(value, 0x2E)
            ringGlow2.color = withAlpha(value, 0x66)
            secondPaint.color = value
            capAccent.color = value
            invalidate()
        }

    var introProgress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var halo: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Numbers on the dial: 12, 4 (12 · 3 · 6 · 9) or 0. */
    var numbers: Int = 12
        set(value) {
            field = value
            invalidate()
        }

    var showSeconds: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Fonts.get(context, Fonts.MEDIUM)
        setShadowLayer(4f * d, 0f, 1f * d, 0x80000000.toInt())
    }

    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000 - System.currentTimeMillis() % 1000 + 5)
        }
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private var haloFor = 0
    private var haloSize = 0f
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var faceSize = 0f
    private val ringGlow1 = stroke(10f, withAlpha(accent, 0x2E))
    private val ringGlow2 = stroke(5f, withAlpha(accent, 0x66))
    private val ringCore = stroke(1.8f, accent)
    private val majorTick = stroke(3f, Color.WHITE)
    private val minorTick = stroke(1.2f, 0x73FFFFFF)
    private val hourPaint = stroke(5.5f, Color.WHITE).apply { setShadowLayer(5f * d, 0f, 2f * d, 0x80000000.toInt()) }
    private val minutePaint = stroke(3.6f, Color.WHITE).apply { setShadowLayer(5f * d, 0f, 2f * d, 0x80000000.toInt()) }
    private val secondPaint = stroke(1.7f, accent)
    private val capWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val capAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val oval = RectF()

    private fun stroke(widthDp: Float, c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthDp * d
        strokeCap = Paint.Cap.ROUND
        color = c
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startTicking() else stopTicking()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTicking()
    }

    override fun onDetachedFromWindow() {
        stopTicking()
        super.onDetachedFromWindow()
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        post(tick)
    }

    private fun stopTicking() {
        ticking = false
        removeCallbacks(tick)
    }

    override fun onDraw(canvas: Canvas) {
        val p = introProgress
        val e = 1f - (1f - p).pow(3)          // ease-out
        val cx = width / 2f
        val cy = height / 2f
        val full = min(width, height) / 2f
        val r = if (halo) full * 0.78f else full - 6f * d

        // soft glow around the clock, in the accent colour
        if (halo) {
            if (haloFor != accent || haloSize != full) {
                haloFor = accent
                haloSize = full
                haloPaint.shader = RadialGradient(
                    cx, cy, full,
                    intArrayOf(withAlpha(accent, 0x50), withAlpha(accent, 0x1C), withAlpha(accent, 0)),
                    floatArrayOf(0.62f, 0.82f, 1f), Shader.TileMode.CLAMP,
                )
            }
            haloPaint.alpha = (255 * e).toInt()
            canvas.drawCircle(cx, cy, full, haloPaint)
        }

        // glass face
        if (faceSize != r) {
            faceSize = r
            facePaint.shader = RadialGradient(
                cx, cy - r * 0.35f, r * 1.4f,
                intArrayOf(0x38FFFFFF, 0x14FFFFFF, 0x08FFFFFF), floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        facePaint.alpha = (255 * e).toInt()
        canvas.drawCircle(cx, cy, r, facePaint)

        // neon ring (sweeps in during the intro)
        oval.set(cx - r, cy - r, cx + r, cy + r)
        val sweep = 360f * e
        canvas.drawArc(oval, -90f, sweep, false, ringGlow1)
        canvas.drawArc(oval, -90f, sweep, false, ringGlow2)
        canvas.drawArc(oval, -90f, sweep, false, ringCore)

        // hour and minute markers
        val markerAlpha = ((p - 0.25f) / 0.5f).coerceIn(0f, 1f)
        if (markerAlpha > 0f) {
            majorTick.alpha = (255 * markerAlpha).toInt()
            minorTick.alpha = (0x73 * markerAlpha).toInt()
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val a = Math.toRadians(i * 6.0)
                val sx = sin(a).toFloat()
                val sy = -cos(a).toFloat()
                val outer = r - 8f * d
                val len = when {
                    !major -> 3f
                    numbers > 0 -> 6f            // short hour marks when numbers are shown
                    i % 15 == 0 -> 13f
                    else -> 8f
                }
                val inner = outer - len * d
                canvas.drawLine(cx + sx * inner, cy + sy * inner, cx + sx * outer, cy + sy * outer,
                    if (major) majorTick else minorTick)
            }

            if (numbers > 0) {
                numberPaint.textSize = r * 0.2f
                numberPaint.alpha = (240 * markerAlpha).toInt()
                val fm = numberPaint.fontMetrics
                val yOffset = -(fm.ascent + fm.descent) / 2f
                val nr = r * 0.66f
                for (n in 1..12) {
                    if (numbers == 4 && n % 3 != 0) continue
                    val a = Math.toRadians(n * 30.0)
                    canvas.drawText(n.toString(), cx + sin(a).toFloat() * nr, cy - cos(a).toFloat() * nr + yOffset, numberPaint)
                }
            }
        }

        // hands
        val now = System.currentTimeMillis()
        val local = now + TimeZone.getDefault().getOffset(now)
        val secOfDay = Math.floorMod(local, 86_400_000L) / 1000.0
        val seconds = floor(secOfDay % 60.0).toFloat()
        val minutes = ((secOfDay / 60.0) % 60.0).toFloat()
        val hours = ((secOfDay / 3600.0) % 12.0).toFloat()

        hand(canvas, cx, cy, hours * 30f * e, r * 0.50f, r * 0.10f, hourPaint)
        hand(canvas, cx, cy, minutes * 6f * e, r * 0.72f, r * 0.12f, minutePaint)
        if (p >= 1f && showSeconds) hand(canvas, cx, cy, seconds * 6f, r * 0.80f, r * 0.20f, secondPaint)

        canvas.drawCircle(cx, cy, 5.5f * d * e, capWhite)
        canvas.drawCircle(cx, cy, 2.6f * d * e, capAccent)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, tail: Float, paint: Paint) {
        val a = Math.toRadians(deg.toDouble())
        val sx = sin(a).toFloat()
        val sy = -cos(a).toFloat()
        c.drawLine(cx - sx * tail, cy - sy * tail, cx + sx * len, cy + sy * len, paint)
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)
}
