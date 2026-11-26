package com.charan.glasshome

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class Weather(
    val city: String,
    val temp: Double,
    val code: Int,
    val isDay: Boolean,
    val max: Double,
    val min: Double,
) {
    fun toJson(): String = JSONObject()
        .put("city", city).put("temp", temp).put("code", code)
        .put("isDay", isDay).put("max", max).put("min", min)
        .toString()

    companion object {
        fun fromJson(s: String): Weather {
            val j = JSONObject(s)
            return Weather(
                j.getString("city"), j.getDouble("temp"), j.getInt("code"),
                j.getBoolean("isDay"), j.getDouble("max"), j.getDouble("min"),
            )
        }
    }
}

/**
 * Free weather from Open-Meteo (no API key needed).
 * If no city is set, the location is guessed from the internet connection.
 */
object WeatherClient {

    private class Place(val name: String, val lat: Double, val lon: Double)

    /** Blocking network call — run it off the main thread. */
    fun fetch(city: String): Weather {
        val place = if (city.isBlank()) locateByIp() else geocode(city)
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
            place.lat, place.lon,
        )
        val json = JSONObject(get(url))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        return Weather(
            city = place.name,
            temp = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            max = daily.getJSONArray("temperature_2m_max").getDouble(0),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0),
        )
    }

    private fun geocode(city: String): Place {
        val q = URLEncoder.encode(city.trim(), "UTF-8")
        val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json"))
        val results = json.optJSONArray("results")
        if (results == null || results.length() == 0) throw IllegalArgumentException("City not found")
        val r = results.getJSONObject(0)
        return Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    private fun locateByIp(): Place {
        // Try two free services in case one is down.
        runCatching {
            val j = JSONObject(get("https://ipwho.is/"))
            if (j.optBoolean("success", false)) {
                return Place(j.optString("city", "Your area"), j.getDouble("latitude"), j.getDouble("longitude"))
            }
        }
        val j = JSONObject(get("https://ipapi.co/json/"))
        return Place(j.optString("city", "Your area"), j.getDouble("latitude"), j.getDouble("longitude"))
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "GlassHome-TV-Launcher/1.0")
        try {
            if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Weather code (WMO) to an emoji and a short description. */
    fun describe(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
        0 -> (if (isDay) "☀️" else "🌙") to "Clear sky"
        1 -> (if (isDay) "🌤️" else "🌙") to "Mainly clear"
        2 -> "⛅" to "Partly cloudy"
        3 -> "☁️" to "Overcast"
        45, 48 -> "🌫️" to "Foggy"
        51, 53, 55, 56, 57 -> "🌦️" to "Drizzle"
        61, 63, 65, 66, 67 -> "🌧️" to "Rain"
        71, 73, 75, 77 -> "🌨️" to "Snow"
        80, 81, 82 -> "🌦️" to "Rain showers"
        85, 86 -> "🌨️" to "Snow showers"
        95, 96, 99 -> "⛈️" to "Thunderstorm"
        else -> "🌡️" to "—"
    }
}
