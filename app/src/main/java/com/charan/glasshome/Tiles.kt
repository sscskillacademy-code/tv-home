package com.charan.glasshome

import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Builds the glass app tiles used on the home screen and in the app picker. */
object Tiles {

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** A 16:9 tile showing the app's TV banner, or its icon + name if it has no banner. */
    fun appTile(ctx: Context, app: AppEntry, widthDp: Float, heightDp: Float): FrameLayout {
        val tile = baseTile(ctx, widthDp, heightDp)
        tile.tag = app.pkg
        tile.contentDescription = app.label

        if (app.banner != null) {
            tile.addView(ImageView(ctx).apply {
                setImageDrawable(app.banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 8f), dp(ctx, 6f), dp(ctx, 8f), dp(ctx, 6f))
            }
            val iconSize = dp(ctx, heightDp * 0.45f)
            col.addView(ImageView(ctx).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(iconSize, iconSize))
            col.addView(label(ctx, app.label, 12f).apply {
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(ctx, 4f)
            })
            tile.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        addShine(tile)
        return tile
    }

    private fun addShine(tile: FrameLayout) {
        tile.addView(ShineView(tile.context), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** A small square glass button with a white line icon (emoji if the icon can't load) and a caption. */
    fun actionTile(ctx: Context, iconRes: Int, fallback: String, caption: String, sizeDp: Float): FrameLayout {
        val tile = baseTile(ctx, sizeDp, sizeDp)
        tile.contentDescription = caption
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val icon = runCatching { ctx.getDrawable(iconRes) }.getOrNull()
        if (icon != null) {
            val s = dp(ctx, 30f)
            col.addView(ImageView(ctx).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(s, s))
        } else {
            col.addView(TextView(ctx).apply {
                text = fallback
                textSize = 26f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
            })
        }
        col.addView(label(ctx, caption, 12f).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(ctx, 8f)
        })
        tile.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addShine(tile)
        return tile
    }

    private fun baseTile(ctx: Context, wDp: Float, hDp: Float): FrameLayout =
        FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, wDp), dp(ctx, hDp))
            background = ctx.getDrawable(R.drawable.tile_bg)
            foreground = ctx.getDrawable(R.drawable.tile_focus)
            clipToOutline = true
            isFocusable = true
            isClickable = true
            cameraDistance = 9000f * ctx.resources.displayMetrics.density   // gentle perspective for the 3D tilt
        }

    private fun label(ctx: Context, s: String, sizeSp: Float) = TextView(ctx).apply {
        text = s
        textSize = sizeSp
        typeface = Fonts.get(ctx, Fonts.MEDIUM)
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Colour the tile's lifted shadow, so a focused tile glows in its app's colour. */
    fun setGlow(v: View, color: Int) {
        if (Build.VERSION.SDK_INT >= 28) {
            v.outlineSpotShadowColor = color
            v.outlineAmbientShadowColor = color
        }
    }

    /** Grow + lift the focused tile with a little spring, shrink it back when focus leaves. */
    fun focusZoom(v: View, focused: Boolean, scale: Float = 1.12f) {
        if (focused) (v as? ViewGroup)?.findViewWithTag<ShineView>(ShineView.TAG)?.play()
        val lift = v.resources.displayMetrics.density * 18f
        v.animate()
            .scaleX(if (focused) scale else 1f)
            .scaleY(if (focused) scale else 1f)
            .translationZ(if (focused) lift else 0f)
            .setInterpolator(if (focused) OvershootInterpolator(1.6f) else null)
            .setDuration(if (focused) 260 else 160)
            .start()
    }
}
