package com.charan.glasshome

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView

/**
 * Two typefaces, each with one job:
 *  - Plus Jakarta Sans (variable sans) for everything you read at a glance: time, labels, hints —
 *    open, clear letters that stay readable from across the room.
 *  - Instrument Serif for the big editorial titles, like a film's title card.
 * Fonts live at the assets root (the Windows build tools mangle sub-folders).
 */
object Fonts {
    const val THIN = 200
    const val LIGHT = 300
    const val REGULAR = 400
    const val MEDIUM = 500
    const val SEMIBOLD = 600
    const val BOLD = 700

    private val cache = HashMap<Int, Typeface>()
    private var serifRegular: Typeface? = null
    private var serifItalic: Typeface? = null

    fun get(ctx: Context, weight: Int): Typeface = synchronized(cache) {
        cache.getOrPut(weight) {
            runCatching {
                Typeface.Builder(ctx.assets, "PlusJakartaSans.ttf")
                    .setFontVariationSettings("'wght' $weight")
                    .build()
            }.getOrNull() ?: Typeface.create("sans-serif", if (weight >= SEMIBOLD) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    fun serif(ctx: Context, italic: Boolean = false): Typeface = synchronized(cache) {
        val known = if (italic) serifItalic else serifRegular
        known ?: (runCatching {
            Typeface.createFromAsset(ctx.assets, if (italic) "InstrumentSerif-Italic.ttf" else "InstrumentSerif-Regular.ttf")
        }.getOrNull() ?: Typeface.create(Typeface.SERIF, if (italic) Typeface.ITALIC else Typeface.NORMAL))
            .also { if (italic) serifItalic = it else serifRegular = it }
    }

    fun apply(weight: Int, vararg views: TextView) {
        for (v in views) v.typeface = get(v.context, weight)
    }
}
