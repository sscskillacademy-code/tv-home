package com.charan.glasshome

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * A band of light that sweeps once across a tile when it is selected — like light catching glass.
 * Draws nothing at all when idle.
 */
class ShineView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var progress = -1f
    private var anim: ValueAnimator? = null

    init {
        isFocusable = false
        isClickable = false
        tag = TAG
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paint.shader = LinearGradient(
            0f, 0f, w * 0.45f, h.toFloat(),
            intArrayOf(0x00FFFFFF, 0x4DFFFFFF, 0x00FFFFFF), floatArrayOf(0.3f, 0.5f, 0.7f),
            Shader.TileMode.CLAMP,
        )
    }

    fun play() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(-0.6f, 1.2f).apply {
            duration = 800
            startDelay = 90
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    progress = -1f
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (progress < -0.55f) return
        matrix.setTranslate(progress * width, 0f)
        paint.shader?.setLocalMatrix(matrix)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        const val TAG = "shine"
    }
}
