package com.charan.glasshome

/** Colour palettes for the background glow, clock accent and highlights. All static (no animation). */
object Themes {
    class Palette(
        val name: String,
        val top: Int,
        val bottom: Int,
        val glows: IntArray,
        val accent: Int,
    )

    val ALL = listOf(
        Palette(
            "Midnight",
            0xFF05060E.toInt(), 0xFF0B0E24.toInt(),
            intArrayOf(0x996A3CFF.toInt(), 0x7700A2FF.toInt(), 0x55FF3D9A.toInt()),
            0xFF8FD8FF.toInt(),
        ),
        Palette(
            "Ember",
            0xFF0E0506.toInt(), 0xFF240B10.toInt(),
            intArrayOf(0x99FF5A36.toInt(), 0x77FF2D75.toInt(), 0x55FFB03B.toInt()),
            0xFFFFB36B.toInt(),
        ),
        Palette(
            "Ocean",
            0xFF02090F.toInt(), 0xFF06202E.toInt(),
            intArrayOf(0x9900B4D8.toInt(), 0x770077B6.toInt(), 0x552BFFB1.toInt()),
            0xFF6FF0FF.toInt(),
        ),
        Palette(
            "Emerald",
            0xFF020B08.toInt(), 0xFF061C15.toInt(),
            intArrayOf(0x9900C896.toInt(), 0x77007A5E.toInt(), 0x55A8FF60.toInt()),
            0xFF8CFFC8.toInt(),
        ),
        Palette(
            "Pure Black",
            0xFF000000.toInt(), 0xFF000000.toInt(),
            intArrayOf(0x33404070, 0x22404070, 0x11404070),
            0xFFE6E9FF.toInt(),
        ),
    )

    fun get(index: Int): Palette = ALL[Math.floorMod(index, ALL.size)]
}
