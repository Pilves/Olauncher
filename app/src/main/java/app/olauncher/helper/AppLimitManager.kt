package app.olauncher.helper

import android.content.Context

/**
 * Manages per-app daily time limits.
 * When a limited app exceeds its daily limit, a confirmation dialog is shown before launching.
 *
 * Storage format in SharedPreferences: "pkg=minutes,pkg2=minutes"
 * SharedPrefs key kept as BAD_HABIT_APPS for backward compatibility.
 */
object AppLimitManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val KEY_APP_LIMITS = "BAD_HABIT_APPS"

    private val lock = Any()

    @Volatile
    private var cachedLimits: Map<String, Int>? = null

    fun hasLimit(context: Context, packageName: String): Boolean {
        return getAllLimits(context).containsKey(packageName)
    }

    fun getLimit(context: Context, packageName: String): Int? {
        return getAllLimits(context)[packageName]
    }

    fun setLimit(context: Context, packageName: String, minutes: Int) {
        synchronized(lock) {
            val limits = getAllLimitsInternal(context).toMutableMap()
            limits[packageName] = minutes
            save(context, limits)
            cachedLimits = null
        }
    }

    fun removeLimit(context: Context, packageName: String) {
        synchronized(lock) {
            val limits = getAllLimitsInternal(context).toMutableMap()
            limits.remove(packageName)
            save(context, limits)
            cachedLimits = null
        }
    }

    fun getAllLimits(context: Context): Map<String, Int> {
        synchronized(lock) {
            return getAllLimitsInternal(context)
        }
    }

    private fun getAllLimitsInternal(context: Context): Map<String, Int> {
        cachedLimits?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val csv = prefs.getString(KEY_APP_LIMITS, "") ?: ""
        if (csv.isBlank()) {
            cachedLimits = emptyMap()
            return emptyMap()
        }
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
        cachedLimits = result
        return result
    }

    private fun save(context: Context, limits: Map<String, Int>) {
        val csv = limits.entries.joinToString(",") { "${it.key}=${it.value}" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LIMITS, csv)
            .apply()
    }
}
