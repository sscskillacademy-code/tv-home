package com.charan.glasshome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Sleep mode: the greeting ("Good night, <name>"), the analog clock, the time, date and weather on a
 * PURE BLACK screen — every background pixel off. In RGB mode the greeting and the time flow through
 * the colours of the rainbow and the clock ring follows. Glides to a new spot every minute so nothing
 * burns in. Used by the real screensaver (ClockDream) and by the Sleep button / preview.
 */
class ClockScene(context: Context) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val style = Prefs.getSleepStyle(context)
    private val accent = Themes.get(Prefs.getTheme(context)).accent
    private val name = Prefs.getName(context)

    private val cluster = LinearLayout(context)
    private val greeting: TextView
    private val clock: AnalogClockView
    private val timeText: TextView
    private val dateText: TextView
    private val weatherText: TextView

    private val time12 = SimpleDateFormat("h:mm", Locale.getDefault())
    private val time24 = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val amPm = SimpleDateFormat("a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    private var rgb: ValueAnimator? = null
    private val shaderMatrix = Matrix()
    private val period = 560f * resources.displayMetrics.density

    private val tick = object : Runnable {
        override fun run() {
            updateTexts()
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    private val drift = object : Runnable {
        override fun run() {
            glide()
            updateWeather()
            handler.postDelayed(this, 60_000)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)   // all background pixels off
        val d = resources.displayMetrics.density
        val base = when (style) {
            Prefs.SLEEP_WHITE -> Ui.IVORY
            else -> accent
        }

        cluster.orientation = LinearLayout.VERTICAL
        cluster.gravity = Gravity.CENTER_HORIZONTAL

        // Upright, clean and large: readable from across the room even when half asleep
        greeting = Ui.sans(context, 40f, Fonts.SEMIBOLD, if (style == Prefs.SLEEP_ROOM) accent else Ui.IVORY).apply {
            maxLines = 1
            letterSpacing = 0.01f
            setPadding((12 * d).toInt(), 0, (12 * d).toInt(), (6 * d).toInt())
        }
        if (Prefs.getSleepGreeting(context)) cluster.addView(greeting, wrap())

        clock = AnalogClockView(context).apply {
            this.accent = base
            numbers = Prefs.getClockNumbers(context)
        }
        cluster.addView(clock, LinearLayout.LayoutParams((200 * d).toInt(), (200 * d).toInt()).apply { topMargin = (14 * d).toInt() })

        timeText = Ui.sans(context, 72f, Fonts.LIGHT, Ui.IVORY).apply { includeFontPadding = false }
        cluster.addView(timeText, wrap().apply { topMargin = (12 * d).toInt() })
        dateText = Ui.sans(context, 24f, Fonts.MEDIUM, 0xD9F4EFE6.toInt())
        cluster.addView(dateText, wrap().apply { topMargin = (8 * d).toInt() })
        weatherText = Ui.sans(context, 19f, Fonts.REGULAR, 0xB3F4EFE6.toInt())
        if (Prefs.getSleepWeather(context)) cluster.addView(weatherText, wrap().apply { topMargin = (10 * d).toInt() })

        addView(cluster, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        updateTexts()
        updateWeather()
    }

    fun start() {
        stop()
        handler.post(tick)
        handler.postDelayed(drift, 60_000)
        if (style == Prefs.SLEEP_RGB) startRgb()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        rgb?.cancel()
        rgb = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------- RGB colour flow

    private fun startRgb() {
        val texts = listOf(greeting, timeText)
        texts.forEach { tv ->
            tv.setTextColor(Color.WHITE)
            tv.paint.shader = LinearGradient(0f, 0f, period, 0f, RAINBOW, null, Shader.TileMode.REPEAT)
        }
        val hsv = floatArrayOf(0f, 0.7f, 1f)
        rgb = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 9000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val phase = it.animatedValue as Float
                shaderMatrix.setTranslate(phase * period, 0f)
                texts.forEach { tv ->
                    tv.paint.shader?.setLocalMatrix(shaderMatrix)
                    tv.invalidate()
                }
                hsv[0] = (360f - phase * 360f) % 360f
                clock.accent = Color.HSVToColor(hsv)
            }
            start()
        }
    }

    // ---------------------------------------------------------------- content

    private fun updateTexts() {
        val now = Date()
        if (DateFormat.is24HourFormat(context)) {
            timeText.text = time24.format(now)
        } else {
            val t = time12.format(now)
            val s = SpannableString("$t ${amPm.format(now)}")
            s.setSpan(RelativeSizeSpan(0.34f), t.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            timeText.text = s
        }
        dateText.text = dateFormat.format(now)
        val part = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
        greeting.text = if (name.isBlank()) part else "$part, $name"
    }

    /** Weather comes from what the home screen last fetched (no network use here). */
    private fun updateWeather() {
        val cached = Prefs.loadWeather(context)
        weatherText.text = cached?.first?.let { w ->
            val (emoji, desc) = WeatherClient.describe(w.code, w.isDay)
            "$emoji   ${w.temp.roundToInt()}°  ·  $desc  ·  ${w.city}"
        } ?: ""
        weatherText.visibility = if (weatherText.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Glide to a random spot (keeps everything on screen). */
    private fun glide() {
        val maxX = (width - cluster.width) / 2f
        val maxY = (height - cluster.height) / 2f
        if (maxX <= 0f && maxY <= 0f) return
        cluster.animate()
            .translationX(Random.nextFloat() * 2 * maxX - maxX)
            .translationY(Random.nextFloat() * 2 * maxY - maxY)
            .setDuration(4000)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun wrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private companion object {
        /** Soft but vivid rainbow; first and last colour match so the flow loops seamlessly. */
        val RAINBOW = intArrayOf(
            0xFFFF5F6D.toInt(), 0xFFFFB86B.toInt(), 0xFFF9E866.toInt(), 0xFF6EE7B7.toInt(),
            0xFF60A5FA.toInt(), 0xFFA78BFA.toInt(), 0xFFF472B6.toInt(), 0xFFFF5F6D.toInt(),
        )
    }
}
