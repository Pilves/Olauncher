package app.olauncher.helper

import android.content.Context

/**
 * Manages "bad habit" apps with daily time limits.
 * When a bad-habit app exceeds its daily limit, a confirmation dialog is shown before launching.
 *
 * Storage format in SharedPreferences: "pkg=minutes,pkg2=minutes"
 */
object BadHabitManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val KEY_BAD_HABIT_APPS = "BAD_HABIT_APPS"

    private val lock = Any()

    fun isBadHabitApp(context: Context, packageName: String): Boolean {
        return getAllBadHabits(context).containsKey(packageName)
    }

    fun getLimit(context: Context, packageName: String): Int? {
        return getAllBadHabits(context)[packageName]
    }

    fun addBadHabit(context: Context, packageName: String, minutes: Int) {
        synchronized(lock) {
            val habits = getAllBadHabitsInternal(context).toMutableMap()
            habits[packageName] = minutes
            save(context, habits)
        }
    }

    fun removeBadHabit(context: Context, packageName: String) {
        synchronized(lock) {
            val habits = getAllBadHabitsInternal(context).toMutableMap()
            habits.remove(packageName)
            save(context, habits)
        }
    }

    fun getAllBadHabits(context: Context): Map<String, Int> {
        synchronized(lock) {
            return getAllBadHabitsInternal(context)
        }
    }

    private fun getAllBadHabitsInternal(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val csv = prefs.getString(KEY_BAD_HABIT_APPS, "") ?: ""
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

    private fun save(context: Context, habits: Map<String, Int>) {
        val csv = habits.entries.joinToString(",") { "${it.key}=${it.value}" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BAD_HABIT_APPS, csv)
            .apply()
    }
}
