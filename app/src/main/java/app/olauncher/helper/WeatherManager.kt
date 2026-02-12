package app.olauncher.helper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CityResult(
    val name: String,
    val country: String,
    val admin1: String,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String
        get() = buildString {
            append(name)
            if (admin1.isNotEmpty()) append(", $admin1")
            if (country.isNotEmpty()) append(", $country")
        }
}

object WeatherManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val WEATHER_ENABLED = "WEATHER_ENABLED"
    private const val WEATHER_LAT = "WEATHER_LAT"
    private const val WEATHER_LNG = "WEATHER_LNG"
    private const val WEATHER_CACHED_TEMP = "WEATHER_CACHED_TEMP"
    private const val WEATHER_LAST_FETCHED = "WEATHER_LAST_FETCHED"
    private const val WEATHER_CITY_NAME = "WEATHER_CITY_NAME"

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

    fun getCityName(context: Context): String =
        prefs(context).getString(WEATHER_CITY_NAME, "") ?: ""

    fun setCityName(context: Context, name: String) {
        prefs(context).edit().putString(WEATHER_CITY_NAME, name).apply()
    }

    fun getCachedTemp(context: Context): String =
        prefs(context).getString(WEATHER_CACHED_TEMP, "") ?: ""

    fun getDisplayString(context: Context): String = getCachedTemp(context)

    suspend fun searchCities(query: String): List<CityResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) return@withContext emptyList()

        var connection: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=en&format=json")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doInput = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            if (!json.has("results")) return@withContext emptyList()

            val results = json.getJSONArray("results")
            val cities = mutableListOf<CityResult>()
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                cities.add(
                    CityResult(
                        name = obj.optString("name", ""),
                        country = obj.optString("country", ""),
                        admin1 = obj.optString("admin1", ""),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0)
                    )
                )
            }
            cities
        } catch (e: Exception) {
            Log.e("WeatherManager", "Failed to search cities", e)
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchWeather(context: Context): String? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val lastFetched = p.getLong(WEATHER_LAST_FETCHED, 0L)
        val now = System.currentTimeMillis()

        if (now - lastFetched < FETCH_INTERVAL_MS) {
            val cached = getCachedTemp(context)
            return@withContext cached.ifEmpty { null }
        }

        val (lat, lng) = getLocation(context)
        if (lat.isBlank() || lng.isBlank()) return@withContext null

        val latD = lat.toDoubleOrNull() ?: return@withContext null
        val lngD = lng.toDoubleOrNull() ?: return@withContext null

        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$latD&longitude=$lngD&current_weather=true")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val currentWeather = json.getJSONObject("current_weather")
            val temperature = currentWeather.getDouble("temperature")
            val formatted = "${temperature.toInt()}\u00B0"

            synchronized(this@WeatherManager) {
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
