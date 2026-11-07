package com.mio.os.core

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class Weather(
    val city: String,
    val tempC: Int,
    val code: Int,
    val condition: String,
    val icon: String,
    val high: Int,
    val low: Int,
    val windKph: Int,
    val humidity: Int,
    val live: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Live weather through the free Open-Meteo API (no key, no account).
 * Everything degrades gracefully: cached -> synthetic fallback.
 */
object WeatherRepo {

    private val cache = ConcurrentHashMap<String, Weather>()
    private var lastFetch = 0L

    suspend fun get(context: Context, city: String, force: Boolean = false): Weather {
        val cached = cache[city]
        if (!force && cached != null && System.currentTimeMillis() - cached.updatedAt < 15 * 60_000) return cached
        val fresh = runCatching { fetch(city) }.getOrNull()
        if (fresh != null) {
            cache[city] = fresh
            return fresh
        }
        return cached?.copy(live = false) ?: fallback(city)
    }

    private fun fetch(city: String): Weather {
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
            URLEncoder.encode(city, "UTF-8")
        val geo = JSONObject(http(geoUrl))
        val results = geo.optJSONArray("results") ?: throw IllegalStateException("no results")
        if (results.length() == 0) throw IllegalStateException("unknown city")
        val loc = results.getJSONObject(0)
        val lat = loc.getDouble("latitude")
        val lon = loc.getDouble("longitude")
        val name = loc.optString("name", city)

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
        val json = JSONObject(http(url))
        val cur = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val code = cur.optInt("weather_code", 0)
        val temp = cur.optDouble("temperature_2m", 0.0).toInt()
        val high = daily.optJSONArray("temperature_2m_max")?.optDouble(0, temp.toDouble())?.toInt() ?: temp
        val low = daily.optJSONArray("temperature_2m_min")?.optDouble(0, temp.toDouble())?.toInt() ?: temp
        val (condition, icon) = describe(code)
        return Weather(
            city = name,
            tempC = temp,
            code = code,
            condition = condition,
            icon = icon,
            high = high,
            low = low,
            windKph = cur.optDouble("wind_speed_10m", 0.0).toInt(),
            humidity = cur.optInt("relative_humidity_2m", 0),
            live = true,
        )
    }

    private fun http(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("http ${conn.responseCode}")
            BufferedReader(InputStreamReader(conn.inputStream)).use { r -> return r.readText() }
        } finally {
            conn.disconnect()
        }
    }

    data class Day(val label: String, val max: Int, val min: Int, val code: Int)

    /** 5-day outlook for the weather app. */
    suspend fun forecast(city: String): List<Day> = runCatching {
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
            URLEncoder.encode(city, "UTF-8")
        val results = JSONObject(http(geoUrl)).optJSONArray("results") ?: return emptyList()
        if (results.length() == 0) return emptyList()
        val loc = results.getJSONObject(0)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.getDouble("latitude")}" +
            "&longitude=${loc.getDouble("longitude")}&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&forecast_days=5&timezone=auto"
        val json = JSONObject(http(url)).getJSONObject("daily")
        val times = json.optJSONArray("time") ?: return emptyList()
        val codes = json.optJSONArray("weather_code")
        val maxs = json.optJSONArray("temperature_2m_max")
        val mins = json.optJSONArray("temperature_2m_min")
        (0 until times.length()).map { i ->
            val date = times.optString(i)
            val label = runCatching {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val parsed = fmt.parse(date)
                java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(parsed!!)
            }.getOrDefault(date)
            Day(
                label = label,
                max = maxs?.optDouble(i, 0.0)?.toInt() ?: 0,
                min = mins?.optDouble(i, 0.0)?.toInt() ?: 0,
                code = codes?.optInt(i, 1) ?: 1,
            )
        }
    }.getOrDefault(emptyList())

    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear sky" to "sun"
        1, 2 -> "Partly cloudy" to "cloud-sun"
        3 -> "Overcast" to "cloud"
        45, 48 -> "Fog" to "fog"
        51, 53, 55, 56, 57 -> "Drizzle" to "rain"
        61, 63, 65, 66, 67 -> "Rain" to "rain"
        71, 73, 75, 77 -> "Snow" to "snow"
        80, 81, 82 -> "Showers" to "rain"
        85, 86 -> "Snow showers" to "snow"
        95, 96, 99 -> "Thunderstorm" to "storm"
        else -> "Clear" to "sun"
    }

    /** Used when offline so the widget never looks broken. */
    fun fallback(city: String): Weather = Weather(
        city = city, tempC = 24, code = 1, condition = "Partly cloudy", icon = "cloud-sun",
        high = 28, low = 18, windKph = 8, humidity = 64, live = false,
    )
}
