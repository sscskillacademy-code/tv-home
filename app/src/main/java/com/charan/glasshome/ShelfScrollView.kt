package com.charan.glasshome

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import kotlin.math.max

/**
 * The scrolling row inside the glass shelf. When focus moves, it scrolls so the focused tile
 * (and its halo) always stays well inside the shelf — never under the faded edges or off screen.
 */
class ShelfScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    /** Minimum gap between the focused tile and the shelf's left/right edge. */
    var focusMargin = 70f * resources.displayMetrics.density

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (childCount == 0) return 0
        val w = width
        val m = focusMargin.toInt().coerceAtMost(w / 4)
        val visibleLeft = scrollX + m
        val visibleRight = scrollX + w - m
        val delta = when {
            rect.right > visibleRight -> rect.right - visibleRight
            rect.left < visibleLeft -> rect.left - visibleLeft
            else -> 0
        }
        val maxScroll = max(0, getChildAt(0).width - w)
        return (scrollX + delta).coerceIn(0, maxScroll) - scrollX
    }
}
