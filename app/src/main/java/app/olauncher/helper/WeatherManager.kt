package app.olauncher.helper

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val WEATHER_ENABLED = "WEATHER_ENABLED"
    private const val WEATHER_LAT = "WEATHER_LAT"
    private const val WEATHER_LNG = "WEATHER_LNG"
    private const val WEATHER_CACHED_TEMP = "WEATHER_CACHED_TEMP"
    private const val WEATHER_LAST_FETCHED = "WEATHER_LAST_FETCHED"

    private const val FETCH_INTERVAL_MS = 60 * 60 * 1000L // 1 hour

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(WEATHER_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(WEATHER_ENABLED, enabled).apply()
    }

    fun setLocation(context: Context, lat: String, lng: String) {
        prefs(context).edit()
            .putString(WEATHER_LAT, lat)
            .putString(WEATHER_LNG, lng)
            .apply()
    }

    fun getLocation(context: Context): Pair<String, String> {
        val p = prefs(context)
        return Pair(
            p.getString(WEATHER_LAT, "") ?: "",
            p.getString(WEATHER_LNG, "") ?: ""
        )
    }

    fun getCachedTemp(context: Context): String =
        prefs(context).getString(WEATHER_CACHED_TEMP, "") ?: ""

    fun getDisplayString(context: Context): String = getCachedTemp(context)

    suspend fun fetchWeather(context: Context): String? {
        val p = prefs(context)
        val lastFetched = p.getLong(WEATHER_LAST_FETCHED, 0L)
        val now = System.currentTimeMillis()

        if (now - lastFetched < FETCH_INTERVAL_MS) {
            val cached = getCachedTemp(context)
            return cached.ifEmpty { null }
        }

        val (lat, lng) = getLocation(context)
        if (lat.isBlank() || lng.isBlank()) return null

        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current_weather=true")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val currentWeather = json.getJSONObject("current_weather")
            val temperature = currentWeather.getDouble("temperature")
            val formatted = "${temperature.toInt()}\u00B0"

            synchronized(this) {
                p.edit()
                    .putString(WEATHER_CACHED_TEMP, formatted)
                    .putLong(WEATHER_LAST_FETCHED, now)
                    .apply()
            }

            formatted
        } catch (e: Exception) {
            Log.e("WeatherManager", "Failed to fetch weather", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
