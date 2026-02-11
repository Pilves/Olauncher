package app.olauncher.helper

import android.content.Context

/**
 * Manages habit-tracking streaks for designated apps.
 * Users mark apps as "habit apps" and each daily launch increments a streak counter.
 * Missing a day resets the streak to 1.
 *
 * All state is persisted directly in SharedPreferences ("app.olauncher").
 */
object HabitStreakManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val KEY_HABIT_APPS = "HABIT_APPS"
    private const val KEY_HABIT_STREAK_DATA = "HABIT_STREAK_DATA"

    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

    private val lock = Any()

    /**
     * Returns true if the given package is marked as a habit app.
     */
    fun isHabitApp(context: Context, packageName: String): Boolean {
        return getHabitApps(context).contains(packageName)
    }

    /**
     * Marks the given package as a habit app.
     */
    fun addHabitApp(context: Context, packageName: String) {
        synchronized(lock) {
            val apps = getHabitAppsInternal(context).toMutableSet()
            apps.add(packageName)
            context.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putString(KEY_HABIT_APPS, apps.joinToString(","))
                .apply()
        }
    }

    /**
     * Removes the given package from the habit app set and clears its streak data.
     */
    fun removeHabitApp(context: Context, packageName: String) {
        synchronized(lock) {
            val apps = getHabitAppsInternal(context).toMutableSet()
            apps.remove(packageName)

            val streaks = parseStreakData(context).toMutableMap()
            streaks.remove(packageName)

            context.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putString(KEY_HABIT_APPS, apps.joinToString(","))
                .putString(KEY_HABIT_STREAK_DATA, serializeStreakData(streaks))
                .apply()
        }
    }

    /**
     * Returns the set of all package names marked as habit apps.
     */
    fun getHabitApps(context: Context): Set<String> {
        synchronized(lock) {
            return getHabitAppsInternal(context)
        }
    }

    /**
     * Records a launch of the given package. Updates streak data if the app is a habit app.
     * - Same day as last recorded: no-op.
     * - Next day after last recorded: streak incremented.
     * - More than one day gap: streak reset to 1.
     */
    fun recordLaunch(context: Context, packageName: String) {
        synchronized(lock) {
            if (!getHabitAppsInternal(context).contains(packageName)) return

            val today = currentEpochDay()
            val streaks = parseStreakData(context).toMutableMap()
            val existing = streaks[packageName]

            if (existing != null) {
                val lastDay = existing.first
                val count = existing.second

                when {
                    today == lastDay -> return // already recorded today
                    today == lastDay + 1 -> streaks[packageName] = Pair(today, count + 1)
                    today > lastDay + 1 -> streaks[packageName] = Pair(today, 1)
                    // today < lastDay should not happen; treat as no-op
                }
            } else {
                streaks[packageName] = Pair(today, 1)
            }

            context.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putString(KEY_HABIT_STREAK_DATA, serializeStreakData(streaks))
                .apply()
        }
    }

    /**
     * Returns the current streak count for the given package, or 0 if not tracked.
     */
    fun getStreak(context: Context, packageName: String): Int {
        synchronized(lock) {
            val data = parseStreakData(context)[packageName] ?: return 0
            return data.second
        }
    }

    /**
     * Returns a human-readable streak display such as "14 days" or "1 day",
     * or null if the app is not tracked or the streak is 0.
     */
    fun getStreakDisplay(context: Context, packageName: String): String? {
        val count = getStreak(context, packageName)
        if (count <= 0) return null
        return if (count == 1) "1 day" else "$count days"
    }

    /**
     * Returns a map of all habit apps to their current streak counts.
     */
    fun getAllStreaks(context: Context): Map<String, Int> {
        synchronized(lock) {
            val apps = getHabitAppsInternal(context)
            val streaks = parseStreakData(context)
            val result = mutableMapOf<String, Int>()
            for (app in apps) {
                result[app] = streaks[app]?.second ?: 0
            }
            return result
        }
    }

    // --- Internal helpers ---

    private fun currentEpochDay(): Long {
        return System.currentTimeMillis() / MILLIS_PER_DAY
    }

    private fun getHabitAppsInternal(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val csv = prefs.getString(KEY_HABIT_APPS, "") ?: ""
        if (csv.isBlank()) return emptySet()
        return csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Parses streak data from prefs.
     * Format: "pkg:lastEpochDay:count,pkg2:lastEpochDay:count,..."
     * Returns map of packageName to (lastEpochDay, count).
     */
    private fun parseStreakData(context: Context): Map<String, Pair<Long, Int>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val csv = prefs.getString(KEY_HABIT_STREAK_DATA, "") ?: ""
        if (csv.isBlank()) return emptyMap()

        val result = mutableMapOf<String, Pair<Long, Int>>()
        for (entry in csv.split(",")) {
            val parts = entry.trim().split(":")
            if (parts.size == 3) {
                val pkg = parts[0]
                val lastDay = parts[1].toLongOrNull() ?: continue
                val count = parts[2].toIntOrNull() ?: continue
                result[pkg] = Pair(lastDay, count)
            }
        }
        return result
    }

    /**
     * Serializes streak data map to CSV format.
     */
    private fun serializeStreakData(data: Map<String, Pair<Long, Int>>): String {
        return data.entries.joinToString(",") { (pkg, pair) ->
            "$pkg:${pair.first}:${pair.second}"
        }
    }
}
