package com.charan.glasshome

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * The Library — every app on the TV, with live search.
 *  - MODE_LAUNCH ("All apps"): OK opens an app, hold OK for more.
 *  - MODE_EDIT ("Edit shelf"): OK adds / removes an app from the home shelf.
 */
class AppPickerActivity : Activity() {

    private lateinit var backdrop: BackdropView
    private lateinit var subtitle: TextView
    private lateinit var search: EditText
    private lateinit var scroll: GridScrollView
    private lateinit var content: FrameLayout
    private lateinit var grid: LinearLayout
    private lateinit var glow: FocusGlowView
    private lateinit var empty: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyText: TextView

    private val loader = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var editMode = false
    private var all: List<AppEntry> = emptyList()
    private val badges = HashMap<String, View>()
    private var palette = Themes.ALL[0]
    private var query = ""
    private var focusedPkg: String? = null
    private val rerender = Runnable { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        editMode = intent.getStringExtra(EXTRA_MODE) == MODE_EDIT
        palette = Themes.get(Prefs.getTheme(this))
        buildLayout()
        showSkeleton()
        loader.execute {
            val apps = AppRepository.loadAll(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                all = apps
                render()
                firstTile()?.requestFocus()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loader.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.page_return_enter, R.anim.page_return_exit)
    }

    // ---------------------------------------------------------------- layout

    private fun buildLayout() {
        val root = FrameLayout(this)
        backdrop = BackdropView(this).apply {
            palette = this@AppPickerActivity.palette
            glowX = 0.18f
            glowY = 0.12f
        }
        root.addView(backdrop, match())
        root.addView(View(this).apply { background = getDrawable(R.drawable.vignette) }, match())
        root.addView(GrainView(this), match())

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48f), dp(30f), dp(48f), 0)
            clipChildren = false
            clipToPadding = false
        }

        // header: eyebrow, serif title, subtitle — and the search field on the right
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(Ui.eyebrow(this, if (editMode) "Your shelf" else "Library"))
        titles.addView(Ui.serif(this, Ui.TITLE).apply { text = if (editMode) "Edit shelf" else "All apps" }, wrap(top = 8f))
        subtitle = Ui.sans(this, Ui.BODY - 1).apply { text = "Gathering your apps…" }
        titles.addView(subtitle, wrap(top = 4f))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(Ui.IVORY_FAINT)
            setTextColor(Ui.IVORY)
            textSize = Ui.BODY
            typeface = Fonts.get(this@AppPickerActivity, Fonts.REGULAR)
            background = getDrawable(R.drawable.search_pill)
            setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.drawable.ic_search), null, null, null)
            compoundDrawablePadding = dp(12f)
            setPadding(dp(20f), 0, dp(20f), 0)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim().orEmpty()
                    handler.removeCallbacks(rerender)
                    handler.postDelayed(rerender, 160)
                }
            })
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                    firstTile()?.requestFocus()
                    true
                } else {
                    false
                }
            }
        }
        Ui.tvTextField(search)
        header.addView(search, LinearLayout.LayoutParams(dp(300f), dp(56f)).apply { bottomMargin = dp(4f) })
        page.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // body: the grid (scrolls, fades at the edges) or the empty state.
        // body must clip (clipChildren = true): that is what keeps scrolled rows from drawing over the header.
        val body = FrameLayout(this)
        scroll = GridScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(40f))
            clipToPadding = false
            clipChildren = false      // focused tiles may grow past the grid; `body` still clips the scroller
            // 30dp page overlap - 10dp cell padding = 20dp: tiles line up with the title above
            setPadding(dp(20f), dp(18f), dp(20f), 0)
        }
        content = FrameLayout(this).apply { clipChildren = false }
        glow = FocusGlowView(this)
        content.addView(glow, FrameLayout.LayoutParams(1, 1))
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(0, 0, 0, dp(60f))   // the last row can scroll clear of the bottom fade
        }
        content.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        empty = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        emptyTitle = Ui.serif(this, Ui.HEADLINE + 8)
        emptyText = Ui.sans(this, Ui.BODY - 1).apply { gravity = Gravity.CENTER }
        empty.addView(emptyTitle, wrap())
        empty.addView(emptyText, wrap(top = 8f))
        body.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            .apply { bottomMargin = dp(40f) })

        // the body reaches into the page margin, so focused tiles and their halo never get cut at the sides
        page.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(8f)
            marginStart = -dp(30f)
            marginEnd = -dp(30f)
        })
        root.addView(page, match())
        setContentView(root)
        Ui.immersive(this)
        backdrop.show(null, palette.accent, animate = false)
        Ui.enter(page)
    }

    // ---------------------------------------------------------------- states

    /** Loading: two rows of softly shimmering placeholders. */
    private fun showSkeleton() {
        grid.removeAllViews()
        repeat(2) {
            val row = newRow()
            repeat(COLUMNS) {
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(CELL_PAD), dp(CELL_PAD), dp(CELL_PAD), 0)
                }
                cell.addView(ShimmerView(this), LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H)))
                cell.addView(ShimmerView(this, 6f), LinearLayout.LayoutParams(dp(96f), dp(12f)).apply { topMargin = dp(14f) })
                row.addView(cell, cellParams())
            }
            grid.addView(row, rowParams())
        }
    }

    private fun render() {
        val shown = if (query.isBlank()) all else all.filter { it.label.contains(query, ignoreCase = true) }
        updateSubtitle(shown.size)

        if (shown.isEmpty()) {
            grid.removeAllViews()
            glow.hide()
            scroll.visibility = View.GONE
            empty.visibility = View.VISIBLE
            if (all.isEmpty()) {
                emptyTitle.text = "No apps yet"
                emptyText.text = "Apps you install from the Play Store will appear here."
            } else {
                emptyTitle.text = "Nothing found"
                emptyText.text = "No app matches “$query”. Try fewer letters."
            }
            Ui.enter(empty)
            return
        }
        empty.visibility = View.GONE
        scroll.visibility = View.VISIBLE

        val favorites = Prefs.getFavorites(this).toSet()
        badges.clear()
        grid.removeAllViews()
        var row: LinearLayout? = null
        shown.forEachIndexed { i, app ->
            if (i % COLUMNS == 0) row = newRow().also { grid.addView(it, rowParams()) }
            row?.addView(cell(app, app.pkg in favorites), cellParams())
        }
    }

    private fun updateSubtitle(shownCount: Int) {
        subtitle.text = when {
            editMode -> "Press OK to add or remove   ·   ${Prefs.getFavorites(this).size} on your shelf"
            query.isNotBlank() -> "$shownCount of ${all.size} apps"
            else -> "${all.size} apps   ·   Hold OK for more"
        }
    }

    // ---------------------------------------------------------------- cells

    private fun cell(app: AppEntry, onShelf: Boolean): LinearLayout {
        // the padding is the room a focused tile grows into (so it never pokes past its cell)
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(CELL_PAD), dp(CELL_PAD), dp(CELL_PAD), 0)
        }
        val tile = Tiles.appTile(this, app, TILE_W, TILE_H)
        val name = Ui.sans(this, Ui.LABEL - 1, Fonts.MEDIUM, Ui.IVORY_MUTED).apply {
            text = app.label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        if (editMode) {
            val size = dp(28f)
            val badge = TextView(this).apply {
                text = "✓"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(0xFF000000.toInt())
                background = getDrawable(R.drawable.badge)?.mutate()
                background?.setTint(palette.accent)
                visibility = if (onShelf) View.VISIBLE else View.GONE
            }
            tile.addView(badge, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                val m = dp(7f)
                setMargins(m, m, m, m)
            })
            badges[app.pkg] = badge
            tile.setOnClickListener { toggle(app) }
        } else {
            tile.setOnClickListener {
                if (!AppRepository.launch(this, app.pkg)) Notice.show(this, "Couldn’t open ${app.label}")
            }
            tile.setOnLongClickListener { showMenu(app); true }
        }

        tile.setOnFocusChangeListener { v, has ->
            Tiles.focusZoom(v, has, 1.1f)
            name.setTextColor(if (has) Ui.IVORY else Ui.IVORY_MUTED)
            name.animate().translationY(if (has) dpf(6f) else 0f).setDuration(200).start()
            if (has) {
                focusedPkg = app.pkg
                moveGlow(v)
                glowColorFor(app)
            }
        }

        cell.addView(tile)
        cell.addView(name, LinearLayout.LayoutParams(dp(TILE_W), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        return cell
    }

    private fun moveGlow(tile: View) {
        if (tile.width == 0) {
            tile.post { moveGlow(tile) }
            return
        }
        val r = Rect(0, 0, tile.width, tile.height)
        content.offsetDescendantRectToMyCoords(tile, r)
        glow.moveToRect(r.left, r.top, r.width(), r.height(), 1.1f)
    }

    /** The halo takes the app's own colour (worked out in the background the first time). */
    private fun glowColorFor(app: AppEntry) {
        glow.setColor(palette.accent, animate = true)
        loader.execute {
            val color = AppRepository.art(app)?.color ?: return@execute
            runOnUiThread {
                if (!isDestroyed && focusedPkg == app.pkg) {
                    glow.setColor(color, animate = true)
                    backdrop.show(null, color)
                }
            }
        }
    }

    private fun toggle(app: AppEntry) {
        val favs = Prefs.getFavorites(this)
        val added = app.pkg !in favs
        if (added) favs.add(app.pkg) else favs.remove(app.pkg)
        Prefs.setFavorites(this, favs)
        badges[app.pkg]?.let { b ->
            if (added) {
                b.visibility = View.VISIBLE
                b.scaleX = 0.4f
                b.scaleY = 0.4f
                b.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
            } else {
                b.visibility = View.GONE
            }
        }
        updateSubtitle(0)
        Notice.show(this, if (added) "${app.label} added to your shelf" else "${app.label} removed from your shelf")
    }

    private fun showMenu(app: AppEntry) {
        val onShelf = app.pkg in Prefs.getFavorites(this)
        GlassMenu(
            this, app.label, null,
            listOf("Open", if (onShelf) "Remove from shelf" else "Add to shelf", "App info"),
            palette.accent,
        ) { which ->
            when (which) {
                0 -> if (!AppRepository.launch(this, app.pkg)) Notice.show(this, "Couldn’t open ${app.label}")
                1 -> {
                    val favs = Prefs.getFavorites(this)
                    if (onShelf) favs.remove(app.pkg) else favs.add(app.pkg)
                    Prefs.setFavorites(this, favs)
                    Notice.show(this, if (onShelf) "${app.label} removed from your shelf" else "${app.label} added to your shelf")
                }
                2 -> runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                }
            }
        }.show()
    }

    // ---------------------------------------------------------------- helpers

    private fun firstTile(): View? =
        ((grid.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? LinearLayout)?.getChildAt(0)
            ?.takeIf { it.isFocusable }

    private fun newRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
    }

    private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = dp(22f) }

    /** Cells sit edge to edge; their padding (2 × CELL_PAD) is the gap between tiles. */
    private fun cellParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(v: Float) = Ui.dp(this, v)
    private fun dpf(v: Float) = Ui.dpf(this, v)

    private fun wrap(top: Float = 0f) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_EDIT = "edit"
        const val MODE_LAUNCH = "launch"
        private const val COLUMNS = 5
        private const val TILE_W = 152f
        private const val TILE_H = 86f
        private const val CELL_PAD = 10f
    }
}
