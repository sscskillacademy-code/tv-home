package com.charan.glasshome

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns an app's banner/icon into
 *  - a soft "light painting" of its colourful parts (white/grey/black become transparent),
 *    used as the full-screen ambient backdrop, and
 *  - its most vivid colour, used for the glow, clock ring and focus halo.
 * Works on a 192x108 bitmap, once per app (cached), off the main thread.
 */
object ArtUtil {
    private const val W = 192
    private const val H = 108

    class Art(val backdrop: Bitmap, val color: Int?)

    /** Safe to call off the main thread (draws a private copy of the drawable). */
    fun make(source: Drawable?, isBanner: Boolean): Art? {
        if (source == null) return null
        val d = source.constantState?.newDrawable()?.mutate() ?: return null
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)

        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        if (iw > 0 && ih > 0) {
            // banners: fill the frame (centre-crop); icons: a large icon in the middle
            val scale = if (isBanner) max(W.toFloat() / iw, H.toFloat() / ih) else (H * 0.9f) / max(iw, ih)
            val dw = (iw * scale).roundToInt()
            val dh = (ih * scale).roundToInt()
            val left = (W - dw) / 2
            val top = (H - dh) / 2
            d.setBounds(left, top, left + dw, top + dh)
        } else {
            d.setBounds(0, 0, W, H)
        }
        runCatching { d.draw(c) }

        val n = W * H
        val px = IntArray(n)
        bmp.getPixels(px, 0, W, 0, 0, W, H)
        val color = dominant(px)

        // Keep only colourful pixels, as premultiplied float channels.
        val a = FloatArray(n)
        val r = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val p = px[i]
            var pr = Color.red(p).toFloat()
            var pg = Color.green(p).toFloat()
            var pb = Color.blue(p).toFloat()
            val chroma = max(pr, max(pg, pb)) - min(pr, min(pg, pb))
            val w = smoothstep(30f, 120f, chroma) * 0.7f
            val luma = 0.299f * pr + 0.587f * pg + 0.114f * pb
            if (luma > 125f) {
                val k = 125f / luma
                pr *= k; pg *= k; pb *= k
            }
            a[i] = w
            r[i] = pr * w
            g[i] = pg * w
            b[i] = pb * w
        }
        for (ch in arrayOf(a, r, g, b)) {
            repeat(3) { boxBlur(ch, W, H, 8) }
        }
        for (i in 0 until n) {
            val al = a[i]
            px[i] = if (al < 0.004f) {
                Color.TRANSPARENT
            } else {
                Color.argb(
                    (al * 255).roundToInt().coerceIn(0, 255),
                    (r[i] / al).roundToInt().coerceIn(0, 255),
                    (g[i] / al).roundToInt().coerceIn(0, 255),
                    (b[i] / al).roundToInt().coerceIn(0, 255),
                )
            }
        }
        bmp.setPixels(px, 0, W, 0, 0, W, H)
        return Art(bmp, color)
    }

    /** Most vivid hue in the picture (ignores black, white and grey), boosted to glow nicely. */
    private fun dominant(px: IntArray): Int? {
        val bins = 12
        val weight = FloatArray(bins)
        val rs = FloatArray(bins)
        val gs = FloatArray(bins)
        val bs = FloatArray(bins)
        val hsv = FloatArray(3)
        for (p in px) {
            Color.colorToHSV(p, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (s < 0.3f || v < 0.25f) continue
            val w = s * s * v
            val bin = (hsv[0] / (360f / bins)).toInt().coerceIn(0, bins - 1)
            weight[bin] += w
            rs[bin] += Color.red(p) * w
            gs[bin] += Color.green(p) * w
            bs[bin] += Color.blue(p) * w
        }
        var best = -1
        for (i in 0 until bins) if (best == -1 || weight[i] > weight[best]) best = i
        if (best == -1 || weight[best] < px.size * 0.01f) return null
        val r = (rs[best] / weight[best]).roundToInt()
        val g = (gs[best] / weight[best]).roundToInt()
        val b = (bs[best] / weight[best]).roundToInt()
        Color.RGBToHSV(r, g, b, hsv)
        hsv[1] = max(hsv[1], 0.6f)
        hsv[2] = max(hsv[2], 0.95f)
        return Color.HSVToColor(hsv)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Fast box blur (sliding window); three passes look like a Gaussian blur. */
    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int) {
        val tmp = FloatArray(src.size)
        val norm = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum * norm
                sum += src[row + min(w - 1, x + r + 1)] - src[row + max(0, x - r)]
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                src[y * w + x] = sum * norm
                sum += tmp[min(h - 1, y + r + 1) * w + x] - tmp[max(0, y - r) * w + x]
            }
        }
    }
}
