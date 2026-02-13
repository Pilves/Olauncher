package app.olauncher.helper

import android.content.Context
import android.os.Build
import app.olauncher.helper.usageStats.EventLogWrapper

/**
 * Utility for querying per-app usage stats.
 */
object UsageStatsHelper {

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
}
