package com.charan.glasshome

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView

/**
 * The big title on the home screen. It glows (text shadow) and fades in and out; telling Android it
 * has no overlapping layers makes the fade apply directly, so the glow is never cut off in a box.
 */
class HeroTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs) {
    override fun hasOverlappingRendering() = false
}
