package com.charan.glasshome

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

/** Small wrapper around SharedPreferences: home-screen apps, name, weather, theme. */
object Prefs {
    private const val FILE = "glass_home"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_INITIALIZED = "initialized"
    private const val KEY_CITY = "city"
    private const val KEY_THEME = "theme"
    private const val KEY_NAME = "name"
    private const val KEY_WEATHER = "weather_cache"
    private const val KEY_WEATHER_AT = "weather_at"
    private const val KEY_INTRO_BOOT = "intro_boot"

    const val DEFAULT_NAME = "Charanjeet"

    /** Apps put on the home screen the first time, if they are installed. */
    private val DEFAULT_APPS = listOf(
        "com.netflix.ninja",                    // Netflix
        "com.google.android.youtube.tv",        // YouTube
        "com.amazon.amazonvideo.livingroom",    // Prime Video
        "com.google.android.youtube.tvmusic",   // YouTube Music
        "in.startv.hotstar",                    // Hotstar
        "com.jio.media.stb.ondemand",           // JioCinema
        "com.android.vending",                  // Play Store
    )

    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getFavorites(c: Context): MutableList<String> =
        (sp(c).getString(KEY_FAVORITES, "") ?: "")
            .split(',')
            .filter { it.isNotBlank() }
            .toMutableList()

    fun setFavorites(c: Context, list: List<String>) {
        sp(c).edit().putString(KEY_FAVORITES, list.distinct().joinToString(",")).apply()
    }

    /** On the very first run, fill the home screen with common streaming apps. */
    fun ensureDefaults(c: Context, installed: Set<String>) {
        if (sp(c).getBoolean(KEY_INITIALIZED, false)) return
        setFavorites(c, DEFAULT_APPS.filter { it in installed })
        sp(c).edit().putBoolean(KEY_INITIALIZED, true).apply()
    }

    fun getCity(c: Context): String = sp(c).getString(KEY_CITY, "") ?: ""
    fun setCity(c: Context, city: String) = sp(c).edit().putString(KEY_CITY, city.trim()).apply()

    fun getTheme(c: Context): Int = sp(c).getInt(KEY_THEME, 0)
    fun setTheme(c: Context, index: Int) = sp(c).edit().putInt(KEY_THEME, index).apply()

    fun getName(c: Context): String = sp(c).getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
    fun setName(c: Context, name: String) = sp(c).edit().putString(KEY_NAME, name.trim()).apply()

    fun saveWeather(c: Context, w: Weather) {
        sp(c).edit()
            .putString(KEY_WEATHER, w.toJson())
            .putLong(KEY_WEATHER_AT, System.currentTimeMillis())
            .apply()
    }

    /** Last weather we fetched (shown instantly at start-up and in the screensaver). */
    fun loadWeather(c: Context): Pair<Weather, Long>? {
        val json = sp(c).getString(KEY_WEATHER, null) ?: return null
        val w = runCatching { Weather.fromJson(json) }.getOrNull() ?: return null
        return w to sp(c).getLong(KEY_WEATHER_AT, 0L)
    }

    // ---- Sleep mode + home options (Personalize page)

    const val SLEEP_RGB = 0
    const val SLEEP_ROOM = 1
    const val SLEEP_WHITE = 2

    /** How sleep mode is coloured: flowing RGB, the room's colour, or plain white. */
    fun getSleepStyle(c: Context): Int = sp(c).getInt("sleep_style", SLEEP_RGB)
    fun setSleepStyle(c: Context, v: Int) = sp(c).edit().putInt("sleep_style", v).apply()

    fun getSleepGreeting(c: Context): Boolean = sp(c).getBoolean("sleep_greeting", true)
    fun setSleepGreeting(c: Context, v: Boolean) = sp(c).edit().putBoolean("sleep_greeting", v).apply()

    fun getSleepWeather(c: Context): Boolean = sp(c).getBoolean("sleep_weather", true)
    fun setSleepWeather(c: Context, v: Boolean) = sp(c).edit().putBoolean("sleep_weather", v).apply()

    /** Numbers on the analog clock: 12, 4 (12-3-6-9) or 0. */
    fun getClockNumbers(c: Context): Int = sp(c).getInt("clock_numbers", 12)
    fun setClockNumbers(c: Context, v: Int) = sp(c).edit().putInt("clock_numbers", v).apply()

    fun getShelf3d(c: Context): Boolean = sp(c).getBoolean("shelf_3d", true)
    fun setShelf3d(c: Context, v: Boolean) = sp(c).edit().putBoolean("shelf_3d", v).apply()

    fun getMiniPlayer(c: Context): Boolean = sp(c).getBoolean("mini_player", true)
    fun setMiniPlayer(c: Context, v: Boolean) = sp(c).edit().putBoolean("mini_player", v).apply()

    /** True only the first time it is asked after each TV start-up (for the welcome animation). */
    fun claimBootIntro(c: Context): Boolean {
        val boot = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot == -1) return false
        if (sp(c).getInt(KEY_INTRO_BOOT, -2) == boot) return false
        sp(c).edit().putInt(KEY_INTRO_BOOT, boot).apply()
        return true
    }
}
