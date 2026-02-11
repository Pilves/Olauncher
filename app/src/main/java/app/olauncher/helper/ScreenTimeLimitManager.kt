package app.olauncher.helper

import android.content.Context
import android.os.Build
import android.widget.Toast
import app.olauncher.helper.usageStats.EventLogWrapper

/**
 * Manages per-app screen time limits stored as CSV in SharedPreferences.
 * Format: "com.instagram=60,com.twitter=30" (minutes)
 *
 * This is a soft-warning system only. It never blocks app launches.
 */
object ScreenTimeLimitManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val KEY_SCREEN_TIME_LIMITS = "SCREEN_TIME_LIMITS"

    private val lock = Any()

    @Volatile
    private var cachedLimits: Map<String, Int>? = null

    /**
     * Parse the CSV preference and return a map of packageName to limit in minutes.
     * Results are cached until the next write operation.
     */
    fun getLimits(context: Context): Map<String, Int> {
        synchronized(lock) {
            cachedLimits?.let { return it }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val csv = prefs.getString(KEY_SCREEN_TIME_LIMITS, "") ?: ""
            val result = parseCsv(csv)
            cachedLimits = result
            return result
        }
    }

    /**
     * Set the time limit for a single app. Pass minutes as the daily limit.
     */
    fun setLimit(context: Context, packageName: String, minutes: Int) {
        synchronized(lock) {
            val limits = getLimits(context).toMutableMap()
            limits[packageName] = minutes
            saveLimits(context, limits)
            cachedLimits = limits
        }
    }

    /**
     * Remove the time limit for a single app.
     */
    fun removeLimit(context: Context, packageName: String) {
        synchronized(lock) {
            val limits = getLimits(context).toMutableMap()
            limits.remove(packageName)
            saveLimits(context, limits)
            cachedLimits = limits
        }
    }

    /**
     * Check today's usage for the given app against its limit.
     * If usage exceeds the limit, a Toast warning is shown.
     * This is a soft warning only and NEVER blocks the launch.
     */
    fun checkAndWarn(context: Context, packageName: String) {
        val limits = getLimits(context)
        val limitMinutes = limits[packageName] ?: return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!context.appUsagePermissionGranted()) return

        val usageMs = getUsageForApp(context, packageName)
        val usageMinutes = usageMs / 60_000

        if (usageMinutes >= limitMinutes) {
            val hours = usageMinutes / 60
            val mins = usageMinutes % 60
            val usageText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            Toast.makeText(
                context,
                "Screen time limit reached: $usageText used (limit: ${limitMinutes}m)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Returns today's total foreground usage for the given app in milliseconds.
     */
    fun getUsageForApp(context: Context, packageName: String): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0L
        if (!context.appUsagePermissionGranted()) return 0L

        return try {
            val wrapper = EventLogWrapper(context)
            val foregroundStats = wrapper.getForegroundStatsByRelativeDay(0)
            val aggregated = wrapper.aggregateForegroundStats(foregroundStats)
            aggregated
                .filter { it.applicationId == packageName }
                .sumOf { it.timeUsed }
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseCsv(csv: String): Map<String, Int> {
        if (csv.isBlank()) return emptyMap()
        val result = mutableMapOf<String, Int>()
        csv.split(",").forEach { entry ->
            val parts = entry.split("=")
            if (parts.size == 2) {
                val pkg = parts[0].trim()
                val minutes = parts[1].trim().toIntOrNull()
                if (pkg.isNotEmpty() && minutes != null) {
                    result[pkg] = minutes
                }
            }
        }
        return result
    }

    private fun saveLimits(context: Context, limits: Map<String, Int>) {
        val csv = limits.entries.joinToString(",") { "${it.key}=${it.value}" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCREEN_TIME_LIMITS, csv)
            .apply()
    }
}
