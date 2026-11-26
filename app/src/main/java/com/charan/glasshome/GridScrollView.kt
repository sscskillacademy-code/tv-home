package com.charan.glasshome

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ScrollView
import kotlin.math.max

/**
 * Vertical scroller for the Library grid: keeps the focused row clear of the faded top and
 * bottom edges, so the selected app is always fully visible.
 */
class GridScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {

    private val extra = (10 * resources.displayMetrics.density).toInt()

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (childCount == 0) return 0
        val fade = verticalFadingEdgeLength
        val visibleTop = scrollY + paddingTop + fade + extra
        val visibleBottom = scrollY + height - paddingBottom - fade - extra
        val delta = when {
            rect.bottom > visibleBottom -> rect.bottom - visibleBottom
            rect.top < visibleTop -> rect.top - visibleTop
            else -> 0
        }
        val maxScroll = max(0, getChildAt(0).height + paddingTop + paddingBottom - height)
        return (scrollY + delta).coerceIn(0, maxScroll) - scrollY
    }
}
