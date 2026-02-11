package app.olauncher.helper

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object SunriseSunsetCalculator {

    /**
     * Calculates sunrise and sunset times for a given location and date.
     *
     * Uses the standard solar declination and hour angle algorithm.
     *
     * @param lat Latitude in degrees
     * @param lng Longitude in degrees
     * @param date The date to calculate for
     * @return Pair of (sunrise, sunset) as LocalTime in the system default timezone
     */
    fun calculate(lat: Double, lng: Double, date: LocalDate): Pair<LocalTime, LocalTime> {
        val dayOfYear = date.dayOfYear

        // Solar declination in degrees
        val declination = 23.45 * sin(Math.toRadians(360.0 / 365 * (dayOfYear - 81)))

        // Hour angle in degrees
        val latRad = Math.toRadians(lat)
        val declRad = Math.toRadians(declination)
        val cosHourAngle = -tan(latRad) * tan(declRad)

        // Handle polar regions: clamp to reasonable values
        if (cosHourAngle > 1.0) {
            // No sunrise (polar night) - clamp to short day
            return Pair(LocalTime.of(10, 0), LocalTime.of(14, 0))
        }
        if (cosHourAngle < -1.0) {
            // No sunset (midnight sun) - clamp to long day
            return Pair(LocalTime.of(2, 0), LocalTime.of(22, 0))
        }

        val hourAngleDeg = Math.toDegrees(acos(cosHourAngle))

        // Sunrise and sunset in hours (UTC, solar noon at 12:00 UTC for longitude 0)
        val sunriseUtcHours = 12.0 - hourAngleDeg / 15.0 - lng / 15.0
        val sunsetUtcHours = 12.0 + hourAngleDeg / 15.0 - lng / 15.0

        // Apply timezone offset
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val offsetHours = zoneOffset.totalSeconds / 3600.0

        val sunriseLocal = sunriseUtcHours + offsetHours
        val sunsetLocal = sunsetUtcHours + offsetHours

        // Clamp to reasonable values (6:00 - 22:00)
        val sunriseTime = hoursToLocalTime(sunriseLocal.coerceIn(6.0, 22.0))
        val sunsetTime = hoursToLocalTime(sunsetLocal.coerceIn(6.0, 22.0))

        // Ensure sunrise is before sunset
        return if (sunriseTime.isBefore(sunsetTime)) {
            Pair(sunriseTime, sunsetTime)
        } else {
            Pair(LocalTime.of(6, 0), LocalTime.of(22, 0))
        }
    }

    private fun hoursToLocalTime(hours: Double): LocalTime {
        val normalizedHours = ((hours % 24) + 24) % 24
        val h = normalizedHours.toInt().coerceIn(0, 23)
        val m = ((normalizedHours - h) * 60).toInt().coerceIn(0, 59)
        return LocalTime.of(h, m)
    }
}
