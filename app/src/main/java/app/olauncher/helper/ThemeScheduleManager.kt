package app.olauncher.helper

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object ThemeScheduleManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val THEME_SCHEDULE_MODE = "THEME_SCHEDULE_MODE"
    private const val THEME_LIGHT_TIME = "THEME_LIGHT_TIME"
    private const val THEME_DARK_TIME = "THEME_DARK_TIME"

    private const val WORKER_TAG = "THEME_SCHEDULE_WORKER"

    const val MODE_MANUAL = 0
    const val MODE_SCHEDULED = 1
    const val MODE_SUNRISE_SUNSET = 2

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)

    fun getMode(context: Context): Int =
        prefs(context).getInt(THEME_SCHEDULE_MODE, MODE_MANUAL)

    fun setMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(THEME_SCHEDULE_MODE, mode).apply()
    }

    fun getLightTime(context: Context): String =
        prefs(context).getString(THEME_LIGHT_TIME, "07:00") ?: "07:00"

    fun setLightTime(context: Context, time: String) {
        prefs(context).edit().putString(THEME_LIGHT_TIME, time).apply()
    }

    fun getDarkTime(context: Context): String =
        prefs(context).getString(THEME_DARK_TIME, "19:00") ?: "19:00"

    fun setDarkTime(context: Context, time: String) {
        prefs(context).edit().putString(THEME_DARK_TIME, time).apply()
    }

    fun startWorker(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<ThemeScheduleWorker>(
            60, TimeUnit.MINUTES
        ).addTag(WORKER_TAG).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORKER_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORKER_TAG)
    }

    fun shouldBeDark(context: Context): Boolean {
        val mode = getMode(context)
        val now = LocalTime.now()

        return when (mode) {
            MODE_SCHEDULED -> {
                val lightTime = try {
                    LocalTime.parse(getLightTime(context), timeFormatter)
                } catch (_: Exception) { LocalTime.of(7, 0) }
                val darkTime = try {
                    LocalTime.parse(getDarkTime(context), timeFormatter)
                } catch (_: Exception) { LocalTime.of(19, 0) }
                isInDarkPeriod(now, lightTime, darkTime)
            }

            MODE_SUNRISE_SUNSET -> {
                val (latStr, lngStr) = WeatherManager.getLocation(context)
                if (latStr.isBlank() || lngStr.isBlank()) {
                    val lightTime = try {
                        LocalTime.parse(getLightTime(context), timeFormatter)
                    } catch (_: Exception) { LocalTime.of(7, 0) }
                    val darkTime = try {
                        LocalTime.parse(getDarkTime(context), timeFormatter)
                    } catch (_: Exception) { LocalTime.of(19, 0) }
                    isInDarkPeriod(now, lightTime, darkTime)
                } else {
                    val lat = latStr.toDoubleOrNull() ?: return false
                    val lng = lngStr.toDoubleOrNull() ?: return false
                    val (sunrise, sunset) = SunriseSunsetCalculator.calculate(lat, lng, LocalDate.now())
                    now.isBefore(sunrise) || now.isAfter(sunset)
                }
            }

            else -> false // MODE_MANUAL, no automatic determination
        }
    }

    /**
     * Determines if the current time falls in the "dark" period.
     * Dark period is from darkTime to lightTime (wrapping around midnight if needed).
     */
    private fun isInDarkPeriod(now: LocalTime, lightTime: LocalTime, darkTime: LocalTime): Boolean {
        return if (darkTime.isAfter(lightTime)) {
            // Normal case: light 07:00, dark 19:00
            // Dark period: 19:00 -> midnight -> 07:00
            now.isAfter(darkTime) || now.isBefore(lightTime)
        } else {
            // Inverted case: light 19:00, dark 07:00
            // Dark period: 07:00 -> 19:00
            now.isAfter(darkTime) && now.isBefore(lightTime)
        }
    }
}
