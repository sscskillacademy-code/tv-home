package com.charan.glasshome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A whisper of film grain over the whole screen — the texture that makes dark gradients feel
 * like projected light instead of flat digital colour (and hides gradient banding).
 * Static: generated once, tiled, never animated, so it costs nothing at runtime.
 */
class GrainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint().apply {
        shader = BitmapShader(tile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPaint(paint)
    }

    private companion object {
        @Volatile private var cached: Bitmap? = null

        /** Zero-mean noise: some pixels a touch darker, some a touch lighter. */
        fun tile(): Bitmap {
            cached?.let { return it }
            val size = 160
            val px = IntArray(size * size)
            val rnd = Random(7)
            for (i in px.indices) {
                val n = (rnd.nextGaussian() * 7.0).roundToInt().coerceIn(-20, 20)
                px[i] = if (n < 0) Color.argb(abs(n), 0, 0, 0) else Color.argb(n, 255, 255, 255)
            }
            val bmp = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
            cached = bmp
            return bmp
        }
    }
}
