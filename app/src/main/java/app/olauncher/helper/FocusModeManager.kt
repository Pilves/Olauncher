package app.olauncher.helper

import android.content.Context

/**
 * Manages Focus Mode state: enabled flag, auto-expire timer, and app whitelist.
 * All state is persisted directly in SharedPreferences ("app.olauncher").
 *
 * Focus mode prevents launching non-whitelisted apps. The caller is responsible
 * for checking [isAppAllowed] and showing feedback to the user.
 */
object FocusModeManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val KEY_ENABLED = "FOCUS_MODE_ENABLED"
    private const val KEY_END_TIME = "FOCUS_MODE_END_TIME"
    private const val KEY_WHITELIST = "FOCUS_MODE_WHITELIST"

    private const val MAX_WHITELIST_SIZE = 5
    private const val END_TIME_UNLIMITED = -1L

    /**
     * Returns true if focus mode is currently active:
     * enabled flag is true AND (endTime is unlimited OR current time is before endTime).
     */
    fun isActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return false
        val endTime = prefs.getLong(KEY_END_TIME, END_TIME_UNLIMITED)
        return endTime == END_TIME_UNLIMITED || System.currentTimeMillis() < endTime
    }

    /**
     * Enables focus mode.
     * @param durationMinutes Duration in minutes. Pass -1 for unlimited (until manually disabled).
     */
    fun enable(context: Context, durationMinutes: Int) {
        val endTime = if (durationMinutes == -1) {
            END_TIME_UNLIMITED
        } else {
            System.currentTimeMillis() + durationMinutes * 60_000L
        }
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_END_TIME, endTime)
            .apply()
    }

    /**
     * Disables focus mode and resets the end time.
     */
    fun disable(context: Context) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .putLong(KEY_END_TIME, END_TIME_UNLIMITED)
            .apply()
    }

    /**
     * Returns true if the given app is allowed to launch.
     * An app is allowed if focus mode is not active OR the app is in the whitelist.
     * Reads SharedPreferences once to avoid redundant lookups from isActive() + getWhitelist().
     */
    fun isAppAllowed(context: Context, packageName: String): Boolean {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!p.getBoolean(KEY_ENABLED, false)) return true
        val endTime = p.getLong(KEY_END_TIME, END_TIME_UNLIMITED)
        if (endTime != END_TIME_UNLIMITED && System.currentTimeMillis() > endTime) return true
        val csv = p.getString(KEY_WHITELIST, "") ?: ""
        if (csv.isBlank()) return false
        val whitelist = csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return packageName in whitelist
    }

    /**
     * Returns the current whitelist as a set of package names.
     */
    fun getWhitelist(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val csv = prefs.getString(KEY_WHITELIST, "") ?: ""
        if (csv.isBlank()) return emptySet()
        return csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Sets the whitelist. At most [MAX_WHITELIST_SIZE] apps are stored.
     */
    fun setWhitelist(context: Context, packages: Set<String>) {
        val limited = packages.take(MAX_WHITELIST_SIZE)
        val csv = limited.joinToString(",")
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_WHITELIST, csv)
            .apply()
    }

    /**
     * Returns a human-readable remaining time string ("Xm" or "Xh Ym"),
     * or null if focus mode is inactive or set to unlimited.
     */
    fun getRemainingTimeFormatted(context: Context): String? {
        if (!isActive(context)) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val endTime = prefs.getLong(KEY_END_TIME, END_TIME_UNLIMITED)
        if (endTime == END_TIME_UNLIMITED) return null

        val remainingMs = endTime - System.currentTimeMillis()
        if (remainingMs <= 0) return null

        val totalMinutes = (remainingMs / 60_000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /**
     * If focus mode is active with a timed end and the time has passed, auto-disables it.
     * Call this from onResume to ensure expired sessions are cleaned up.
     */
    fun checkAndExpire(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val endTime = prefs.getLong(KEY_END_TIME, END_TIME_UNLIMITED)
        if (endTime == END_TIME_UNLIMITED) return
        if (System.currentTimeMillis() > endTime) {
            disable(context)
        }
    }
}
