package com.charan.glasshome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * One soft halo of light behind the focused tile. It glides from tile to tile with a little
 * spring and takes the colour of the app under it. Drawn once into its own layer, so moving
 * it costs nothing; it only redraws while its colour changes.
 */
class FocusGlowView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density

    /** How far the light spills out around the tile. */
    val pad = 18f * d

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(13f * d, BlurMaskFilter.Blur.NORMAL)
    }
    private val rect = RectF()
    private var current = Color.WHITE
    private var colorAnim: ValueAnimator? = null
    private var placed = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)   // blur needs a software layer
        alpha = 0f
        isFocusable = false
        isClickable = false
    }

    /** Move (and resize) the halo to sit behind [tile] (a sibling-level view), scaled like a focused tile. */
    fun moveTo(tile: View, scale: Float) {
        if (tile.width == 0) {
            tile.post { moveTo(tile, scale) }
            return
        }
        moveToRect(tile.left, tile.top, tile.width, tile.height, scale)
    }

    /** Same, for a tile rectangle already expressed in this halo's parent coordinates. */
    fun moveToRect(left: Int, top: Int, width: Int, height: Int, scale: Float) {
        val p = pad.toInt()
        val lp = layoutParams
        val w = width + 2 * p
        val h = height + 2 * p
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            layoutParams = lp
        }
        val tx = (left - p).toFloat()
        val ty = (top - p).toFloat()
        animate().cancel()
        if (!placed || alpha == 0f) {
            placed = true
            translationX = tx
            translationY = ty
            scaleX = scale
            scaleY = scale
            animate().alpha(1f).setDuration(220).start()
        } else {
            animate()
                .translationX(tx).translationY(ty)
                .scaleX(scale).scaleY(scale)
                .alpha(1f)
                .setInterpolator(OvershootInterpolator(1.2f))
                .setDuration(320)
                .start()
        }
    }

    fun setColor(color: Int, animate: Boolean) {
        colorAnim?.cancel()
        if (!animate || current == color) {
            current = color
            invalidate()
            return
        }
        colorAnim = ValueAnimator.ofArgb(current, color).apply {
            duration = 300
            addUpdateListener {
                current = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    fun hide() {
        animate().cancel()
        animate().alpha(0f).setDuration(200).start()
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = (current and 0x00FFFFFF) or (0xE6 shl 24)
        rect.set(pad, pad, width - pad, height - pad)
        val r = 20f * d
        canvas.drawRoundRect(rect, r, r, paint)
    }
}
