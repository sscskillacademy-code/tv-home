package com.charan.glasshome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Loading placeholder: a dim glass slab with a slow band of light passing over it.
 * Only animates while on screen (and placeholders only exist for the first moment).
 */
class ShimmerView(context: Context, private val radiusDp: Float = 18f) : View(context) {

    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x17F4EFE6 }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private var progress = -1f
    private var anim: ValueAnimator? = null

    init {
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(-1f, 2f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shine.shader = LinearGradient(
            0f, 0f, w * 0.6f, 0f,
            intArrayOf(0x00F4EFE6, 0x1FF4EFE6, 0x00F4EFE6), null, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val r = radiusDp * resources.displayMetrics.density
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, base)
        matrix.setTranslate(progress * width, 0f)
        shine.shader?.setLocalMatrix(matrix)
        canvas.drawRoundRect(rect, r, r, shine)
    }
}
