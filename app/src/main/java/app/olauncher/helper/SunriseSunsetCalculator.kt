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

        // Equation of Time correction
        val b = Math.toRadians(360.0 / 365.0 * (dayOfYear - 81))
        val eotMinutes = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val solarNoonUtcHours = 12.0 - lng / 15.0 - eotMinutes / 60.0

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

        // Sunrise and sunset in hours (UTC) relative to solar noon with EoT correction
        val sunriseUtcHours = solarNoonUtcHours - hourAngleDeg / 15.0
        val sunsetUtcHours = solarNoonUtcHours + hourAngleDeg / 15.0

        // Apply timezone offset (use noon instant for DST accuracy)
        val noonInstant = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(noonInstant)
        val offsetHours = zoneOffset.totalSeconds / 3600.0

        val sunriseLocal = sunriseUtcHours + offsetHours
        val sunsetLocal = sunsetUtcHours + offsetHours

        // Clamp to valid range (0:00 - 23:59)
        val sunriseTime = hoursToLocalTime(sunriseLocal.coerceIn(0.0, 23.99))
        val sunsetTime = hoursToLocalTime(sunsetLocal.coerceIn(0.0, 23.99))

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
