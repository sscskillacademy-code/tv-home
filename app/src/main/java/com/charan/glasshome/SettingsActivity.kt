package com.charan.glasshome

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Personalize — the settings page, in sections:
 *  You (name, weather city) · Home (colour, clock numbers, 3D shelf, mini player) ·
 *  Sleep mode (colours, greeting, weather, when it starts, preview) · Welcome animation.
 * Rows with choices change with Left / Right (or OK).
 */
class SettingsActivity : Activity() {

    private lateinit var backdrop: BackdropView
    private var palette = Themes.ALL[0]
    private val swatches = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the on-screen keyboard only opens when you press OK on a field
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        palette = Themes.get(Prefs.getTheme(this))

        val root = FrameLayout(this)
        backdrop = BackdropView(this).apply {
            palette = this@SettingsActivity.palette
            glowX = 0.82f
            glowY = 0.32f
        }
        root.addView(backdrop, match())
        root.addView(View(this).apply { background = getDrawable(R.drawable.vignette) }, match())
        root.addView(GrainView(this), match())

        // two columns: an editorial title card on the left, the settings (scrolling) on the right
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(48f), dp(34f), dp(36f), 0)
            clipChildren = false
            clipToPadding = false
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(30f))
        }
        left.addView(Ui.eyebrow(this, "Personalize"))
        left.addView(Ui.serif(this, Ui.TITLE + 4).apply {
            text = "Make it\nyours"
            maxLines = 2
            ellipsize = null
            setLineSpacing(0f, 0.9f)
        }, wrap(top = 14f))
        left.addView(Ui.sans(this, Ui.BODY - 1).apply { text = "Your name, colours, the clock\nand how sleep mode looks." }, wrap(top = 12f))
        left.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        left.addView(Ui.sans(this, Ui.CAPTION, Fonts.LIGHT, Ui.IVORY_FAINT).apply {
            text = "Use ‹ › on the remote to change a choice.\n\nTo bring back the original home screen:\nSettings  ›  Apps  ›  Android TV Home  ›  Enable"
            setLineSpacing(0f, 1.15f)
        }, wrap())
        page.addView(left, LinearLayout.LayoutParams(dp(310f), ViewGroup.LayoutParams.MATCH_PARENT))

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(6f), dp(14f), dp(40f))   // right padding = room for a selected row to slide
        }

        section(list, "You", first = true)
        val nameRow = textRow("Your name", Prefs.getName(this), "Add your name") { Prefs.setName(this, it) }
        list.addView(nameRow, rowParams())
        list.addView(textRow("Weather city", Prefs.getCity(this), "Auto-detect") { Prefs.setCity(this, it) }, rowParams())

        section(list, "Home")
        list.addView(colourRow(), rowParams())
        val numbers = listOf(12, 4, 0)
        list.addView(choiceRow("Clock numbers", listOf("All 12", "12 · 3 · 6 · 9", "None"),
            numbers.indexOf(Prefs.getClockNumbers(this)).coerceAtLeast(0)) { Prefs.setClockNumbers(this, numbers[it]) }, rowParams())
        list.addView(toggleRow("3D shelf", Prefs.getShelf3d(this)) { Prefs.setShelf3d(this, it) }, rowParams())
        list.addView(toggleRow("Music mini player", Prefs.getMiniPlayer(this)) { Prefs.setMiniPlayer(this, it) }, rowParams())

        section(list, "Sleep mode")
        list.addView(choiceRow("Colours", listOf("RGB", "Room colour", "White"), Prefs.getSleepStyle(this)) {
            Prefs.setSleepStyle(this, it)
        }, rowParams())
        list.addView(toggleRow("Greeting with your name", Prefs.getSleepGreeting(this)) { Prefs.setSleepGreeting(this, it) }, rowParams())
        list.addView(toggleRow("Weather", Prefs.getSleepWeather(this)) { Prefs.setSleepWeather(this, it) }, rowParams())
        list.addView(sleepAfterRow(), rowParams())
        list.addView(actionRow("Sleep mode", "Preview") {
            startActivity(Intent(this, ScreensaverActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.page_enter, R.anim.page_exit)
        }, rowParams())

        section(list, "Welcome")
        list.addView(actionRow("Welcome animation", "Play now") { playIntro() }, rowParams())

        val scroll = GridScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(28f))
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(4f), 0, 0)
            addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // the frame clips the scroller to its own area (keeps scrolled rows off the edges)
        val frame = FrameLayout(this).apply { addView(scroll, match()) }
        page.addView(frame, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            .apply { marginStart = dp(30f) })
        root.addView(page, match())

        setContentView(root)
        Ui.immersive(this)
        backdrop.show(null, palette.accent, animate = false)
        Ui.enter(page)
        nameRow.getChildAt(1)?.requestFocus()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.page_return_enter, R.anim.page_return_exit)
    }

    // ---------------------------------------------------------------- rows

    private fun section(list: LinearLayout, title: String, first: Boolean = false) {
        list.addView(Ui.eyebrow(this, title), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(if (first) 6f else 22f)
            bottomMargin = dp(12f)
            marginStart = dp(4f)
        })
    }

    private fun baseRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24f), 0, dp(24f), 0)
        background = getDrawable(R.drawable.settings_row)
        clipChildren = false      // a focused colour swatch grows past the row's padding
        clipToPadding = false
    }

    private fun label(text: String) = Ui.sans(this, Ui.BODY + 1, Fonts.MEDIUM, Ui.IVORY).apply { this.text = text }

    private fun lift(row: View, on: Boolean) {
        row.animate().translationX(if (on) Ui.dpf(this, 8f) else 0f).setDuration(200).start()
    }

    /** Label + an inline text field (press OK to type; Done or moving away saves). */
    private fun textRow(title: String, value: String, hint: String, onSave: (String) -> Unit): LinearLayout {
        val row = baseRow()
        row.addView(label(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val edit = EditText(this).apply {
            setText(value)
            this.hint = hint
            setHintTextColor(Ui.IVORY_FAINT)
            setTextColor(Ui.IVORY_MUTED)
            textSize = Ui.BODY
            typeface = Fonts.get(this@SettingsActivity, Fonts.LIGHT)
            background = null
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(0, 0, 0, 0)
        }
        var saved = value.trim()
        val commit = {
            val v = edit.text.toString().trim()
            if (v != saved) {
                saved = v
                onSave(v)
                Notice.show(this, "Saved")
            }
        }
        edit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commit()
                hideKeyboard(v)
                true
            } else {
                false
            }
        }
        Ui.tvTextField(edit)
        edit.setOnFocusChangeListener { _, has ->
            row.isSelected = has
            lift(row, has)
            edit.setTextColor(if (has) Ui.IVORY else Ui.IVORY_MUTED)
            if (has) edit.setSelection(edit.text.length) else commit()
        }
        row.addView(edit, LinearLayout.LayoutParams(dp(240f), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    /** Colour of the room: one swatch per theme; moving across them previews it instantly. */
    private fun colourRow(): LinearLayout {
        val row = baseRow()
        row.addView(label("Colour"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val nameView = Ui.sans(this, Ui.BODY, Fonts.LIGHT, Ui.IVORY_MUTED).apply { text = palette.name }
        row.addView(nameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginEnd = dp(8f) })
        Themes.ALL.forEachIndexed { i, p ->
            val sw = View(this).apply {
                isFocusable = true
                isClickable = true
                contentDescription = p.name
                setOnFocusChangeListener { v, has ->
                    v.animate().scaleX(if (has) 1.35f else 1f).scaleY(if (has) 1.35f else 1f).setDuration(180).start()
                    refreshSwatches()
                    if (has) {
                        row.isSelected = true
                        lift(row, true)
                        nameView.text = p.name
                        nameView.setTextColor(Ui.IVORY)
                        backdrop.show(null, p.accent)   // live preview of the room's light
                    } else {
                        // only reset once focus has really left the swatches (not when moving between them)
                        v.post {
                            if (swatches.none { it.isFocused }) {
                                row.isSelected = false
                                lift(row, false)
                                nameView.text = palette.name
                                nameView.setTextColor(Ui.IVORY_MUTED)
                                backdrop.show(null, palette.accent)
                            }
                        }
                    }
                }
                setOnClickListener {
                    pickTheme(i)
                    nameView.text = palette.name
                }
            }
            swatches.add(sw)
            row.addView(sw, LinearLayout.LayoutParams(dp(28f), dp(28f)).apply { marginStart = dp(13f) })
        }
        refreshSwatches()
        return row
    }

    private fun refreshSwatches() {
        val current = Math.floorMod(Prefs.getTheme(this), Themes.ALL.size)
        swatches.forEachIndexed { i, v ->
            val p = Themes.ALL[i]
            val focused = v.isFocused
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(p.accent, p.glows[0] or 0xFF000000.toInt())
                setStroke(
                    dp(if (focused || i == current) 2.5f else 1f),
                    when {
                        focused -> Ui.IVORY
                        i == current -> Ui.IVORY_MUTED
                        else -> Ui.HAIRLINE
                    },
                )
            }
        }
    }

    private fun pickTheme(i: Int) {
        Prefs.setTheme(this, i)
        palette = Themes.get(i)
        backdrop.palette = palette
        backdrop.show(null, palette.accent)
        refreshSwatches()
        Notice.show(this, "Colour of the room: ${palette.name}")
    }

    /** A row with a few choices: Left / Right (or OK) changes it, saved straight away. */
    private fun choiceRow(title: String, options: List<String>, start: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = baseRow().apply {
            isFocusable = true
            isClickable = true
        }
        row.addView(label(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = Ui.sans(this, Ui.BODY, Fonts.MEDIUM, Ui.IVORY_MUTED)
        row.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        var index = start.coerceIn(0, options.size - 1)
        val show = { focused: Boolean ->
            value.text = if (focused) "‹    ${options[index]}    ›" else options[index]
            value.setTextColor(if (focused) Ui.IVORY else Ui.IVORY_MUTED)
        }
        val change = { step: Int ->
            index = Math.floorMod(index + step, options.size)
            show(true)
            value.scaleX = 0.88f
            value.scaleY = 0.88f
            value.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
            onPick(index)
        }
        show(false)
        row.setOnFocusChangeListener { _, has ->
            show(has)
            lift(row, has)
        }
        row.setOnClickListener { change(1) }
        row.setOnKeyListener { _, keyCode, event ->
            val step = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> return@setOnKeyListener false
            }
            if (event.action == KeyEvent.ACTION_DOWN) change(step)
            true
        }
        return row
    }

    private fun toggleRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) =
        choiceRow(title, listOf("On", "Off"), if (on) 0 else 1) { onChange(it == 0) }

    /** When sleep mode starts: the TV's own screensaver delay. */
    private fun sleepAfterRow(): LinearLayout {
        val minutes = listOf(5, 10, 15, 30, 60)
        val currentMs = runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) }.getOrDefault(900_000)
        val start = minutes.indices.minByOrNull { abs(minutes[it] * 60_000 - currentMs) } ?: 2
        return choiceRow("Starts after", listOf("5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour"), start) {
            val ok = Settings.System.canWrite(this) && runCatching {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, minutes[it] * 60_000)
            }.getOrDefault(false)
            if (!ok) Notice.show(this, "The TV didn’t allow this change")
        }
    }

    /** A whole row you press OK on. */
    private fun actionRow(title: String, value: String, onClick: () -> Unit): LinearLayout {
        val row = baseRow().apply {
            isFocusable = true
            isClickable = true
        }
        row.addView(label(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = Ui.sans(this, Ui.BODY, Fonts.MEDIUM, Ui.IVORY_MUTED).apply { text = "$value   →" }
        row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.setOnFocusChangeListener { _, has ->
            v.setTextColor(if (has) Ui.IVORY else Ui.IVORY_MUTED)
            lift(row, has)
        }
        row.setOnClickListener { onClick() }
        return row
    }

    // ---------------------------------------------------------------- helpers

    private fun playIntro() {
        startActivity(Intent(this, HomeActivity::class.java)
            .putExtra(HomeActivity.EXTRA_PLAY_INTRO, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun hideKeyboard(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun dp(v: Float) = Ui.dp(this, v)

    private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62f))
        .apply { bottomMargin = dp(10f) }

    private fun wrap(top: Float = 0f) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}
