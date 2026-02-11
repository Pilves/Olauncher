package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import org.json.JSONObject

class Prefs(context: Context) {
    private val PREFS_FILENAME = "app.olauncher"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_OPEN_TIME = "FIRST_OPEN_TIME"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val LOCK_MODE = "LOCK_MODE"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val KEYBOARD_MESSAGE = "KEYBOARD_MESSAGE"
    private val DAILY_WALLPAPER = "DAILY_WALLPAPER"
    private val DAILY_WALLPAPER_URL = "DAILY_WALLPAPER_URL"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val HOME_BOTTOM_ALIGNMENT = "HOME_BOTTOM_ALIGNMENT"
    private val APP_LABEL_ALIGNMENT = "APP_LABEL_ALIGNMENT"
    private val STATUS_BAR = "STATUS_BAR"
    private val DATE_TIME_VISIBILITY = "DATE_TIME_VISIBILITY"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val APP_THEME = "APP_THEME"
    private val SWIPE_DOWN_ACTION = "SWIPE_DOWN_ACTION"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val SCREEN_TIME_LAST_UPDATED = "SCREEN_TIME_LAST_UPDATED"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    private val WIDGET_ID = "WIDGET_ID"
    private val WIDGET_IDS = "WIDGET_IDS"
    private val WIDGET_PLACEMENT = "WIDGET_PLACEMENT"
    private val WIDGET_HEIGHTS = "WIDGET_HEIGHTS"
    private val WIDGET_PROVIDERS = "WIDGET_PROVIDERS"
    private val APP_DRAWER_SORT_BY_USAGE = "APP_DRAWER_SORT_BY_USAGE"
    private val CACHED_USAGE_STATS = "CACHED_USAGE_STATS"
    private val SWIPE_LEFT_ACTION = "SWIPE_LEFT_ACTION"
    private val SWIPE_RIGHT_ACTION = "SWIPE_RIGHT_ACTION"

    private val APP_NAME_1 = "APP_NAME_1"
    private val APP_NAME_2 = "APP_NAME_2"
    private val APP_NAME_3 = "APP_NAME_3"
    private val APP_NAME_4 = "APP_NAME_4"
    private val APP_NAME_5 = "APP_NAME_5"
    private val APP_NAME_6 = "APP_NAME_6"
    private val APP_NAME_7 = "APP_NAME_7"
    private val APP_NAME_8 = "APP_NAME_8"
    private val APP_PACKAGE_1 = "APP_PACKAGE_1"
    private val APP_PACKAGE_2 = "APP_PACKAGE_2"
    private val APP_PACKAGE_3 = "APP_PACKAGE_3"
    private val APP_PACKAGE_4 = "APP_PACKAGE_4"
    private val APP_PACKAGE_5 = "APP_PACKAGE_5"
    private val APP_PACKAGE_6 = "APP_PACKAGE_6"
    private val APP_PACKAGE_7 = "APP_PACKAGE_7"
    private val APP_PACKAGE_8 = "APP_PACKAGE_8"
    private val APP_ACTIVITY_CLASS_NAME_1 = "APP_ACTIVITY_CLASS_NAME_1"
    private val APP_ACTIVITY_CLASS_NAME_2 = "APP_ACTIVITY_CLASS_NAME_2"
    private val APP_ACTIVITY_CLASS_NAME_3 = "APP_ACTIVITY_CLASS_NAME_3"
    private val APP_ACTIVITY_CLASS_NAME_4 = "APP_ACTIVITY_CLASS_NAME_4"
    private val APP_ACTIVITY_CLASS_NAME_5 = "APP_ACTIVITY_CLASS_NAME_5"
    private val APP_ACTIVITY_CLASS_NAME_6 = "APP_ACTIVITY_CLASS_NAME_6"
    private val APP_ACTIVITY_CLASS_NAME_7 = "APP_ACTIVITY_CLASS_NAME_7"
    private val APP_ACTIVITY_CLASS_NAME_8 = "APP_ACTIVITY_CLASS_NAME_8"
    private val APP_USER_1 = "APP_USER_1"
    private val APP_USER_2 = "APP_USER_2"
    private val APP_USER_3 = "APP_USER_3"
    private val APP_USER_4 = "APP_USER_4"
    private val APP_USER_5 = "APP_USER_5"
    private val APP_USER_6 = "APP_USER_6"
    private val APP_USER_7 = "APP_USER_7"
    private val APP_USER_8 = "APP_USER_8"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT = "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT = "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT"
    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val CLOCK_APP_PACKAGE = "CLOCK_APP_PACKAGE"
    private val CLOCK_APP_USER = "CLOCK_APP_USER"
    private val CLOCK_APP_CLASS_NAME = "CLOCK_APP_CLASS_NAME"
    private val CALENDAR_APP_PACKAGE = "CALENDAR_APP_PACKAGE"
    private val CALENDAR_APP_USER = "CALENDAR_APP_USER"
    private val CALENDAR_APP_CLASS_NAME = "CALENDAR_APP_CLASS_NAME"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value) }

    var firstOpenTime: Long
        get() = prefs.getLong(FIRST_OPEN_TIME, 0L)
        set(value) = prefs.edit { putLong(FIRST_OPEN_TIME, value) }

    var firstHide: Boolean
        get() = prefs.getBoolean(FIRST_HIDE, true)
        set(value) = prefs.edit { putBoolean(FIRST_HIDE, value) }

    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value) }

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value) }

    var keyboardMessageShown: Boolean
        get() = prefs.getBoolean(KEYBOARD_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(KEYBOARD_MESSAGE, value) }

    var dailyWallpaper: Boolean
        get() = prefs.getBoolean(DAILY_WALLPAPER, false)
        set(value) = prefs.edit { putBoolean(DAILY_WALLPAPER, value) }

    var dailyWallpaperUrl: String
        get() = prefs.getString(DAILY_WALLPAPER_URL, "").toString()
        set(value) = prefs.edit { putString(DAILY_WALLPAPER_URL, value) }

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 4)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value) }

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value) }

    var homeBottomAlignment: Boolean
        get() = prefs.getBoolean(HOME_BOTTOM_ALIGNMENT, false)
        set(value) = prefs.edit { putBoolean(HOME_BOTTOM_ALIGNMENT, value) }

    var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value) }

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value) }

    var dateTimeVisibility: Int
        get() = prefs.getInt(DATE_TIME_VISIBILITY, Constants.DateTime.ON)
        set(value) = prefs.edit { putInt(DATE_TIME_VISIBILITY, value) }

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value) }

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value) }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value) }

    var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value) }

    var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value) }

    var screenTimeLastUpdated: Long
        get() = prefs.getLong(SCREEN_TIME_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(SCREEN_TIME_LAST_UPDATED, value) }

    var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value) }

    var widgetId: Int
        get() = prefs.getInt(WIDGET_ID, -1)
        set(value) = prefs.edit { putInt(WIDGET_ID, value) }

    var widgetIds: String
        get() = prefs.getString(WIDGET_IDS, "").toString()
        set(value) = prefs.edit { putString(WIDGET_IDS, value) }

    var widgetPlacement: Int
        get() = prefs.getInt(WIDGET_PLACEMENT, Constants.WidgetPlacement.ABOVE)
        set(value) = prefs.edit { putInt(WIDGET_PLACEMENT, value) }

    var appDrawerSortByUsage: Boolean
        get() = prefs.getBoolean(APP_DRAWER_SORT_BY_USAGE, false)
        set(value) = prefs.edit { putBoolean(APP_DRAWER_SORT_BY_USAGE, value) }

    fun getCachedUsageStats(): Map<String, Long> {
        val raw = prefs.getString(CACHED_USAGE_STATS, "").toString()
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val sep = entry.indexOf('=')
            if (sep > 0) {
                val pkg = entry.substring(0, sep)
                val time = entry.substring(sep + 1).toLongOrNull()
                if (time != null) pkg to time else null
            } else null
        }.toMap()
    }

    fun setCachedUsageStats(stats: Map<String, Long>) {
        prefs.edit {
            putString(CACHED_USAGE_STATS, stats.entries.joinToString(",") { "${it.key}=${it.value}" })
        }
    }

    // -1 means "not set" → fall back to OPEN_APP (backward compatible with existing swipe app prefs)
    var swipeLeftAction: Int
        get() = prefs.getInt(SWIPE_LEFT_ACTION, -1)
        set(value) = prefs.edit { putInt(SWIPE_LEFT_ACTION, value) }

    var swipeRightAction: Int
        get() = prefs.getInt(SWIPE_RIGHT_ACTION, -1)
        set(value) = prefs.edit { putInt(SWIPE_RIGHT_ACTION, value) }

    fun getEffectiveSwipeLeftAction(): Int =
        if (swipeLeftAction == -1) Constants.GestureAction.OPEN_APP else swipeLeftAction

    fun getEffectiveSwipeRightAction(): Int =
        if (swipeRightAction == -1) Constants.GestureAction.OPEN_APP else swipeRightAction

    fun getWidgetIdList(): MutableList<Int> {
        val raw = widgetIds
        if (raw.isBlank()) return mutableListOf()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
    }

    fun setWidgetIdList(ids: List<Int>) {
        widgetIds = ids.joinToString(",")
    }

    private var widgetHeights: String
        get() = prefs.getString(WIDGET_HEIGHTS, "").toString()
        set(value) = prefs.edit { putString(WIDGET_HEIGHTS, value) }

    fun getWidgetHeight(widgetId: Int): Int {
        val map = parseWidgetHeights()
        return map[widgetId] ?: 200 // default 200dp
    }

    fun setWidgetHeight(widgetId: Int, heightDp: Int) {
        val map = parseWidgetHeights().toMutableMap()
        map[widgetId] = heightDp
        widgetHeights = map.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    private fun parseWidgetHeights(): Map<Int, Int> {
        val raw = widgetHeights
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val id = parts[0].trim().toIntOrNull()
                val h = parts[1].trim().toIntOrNull()
                if (id != null && h != null) id to h else null
            } else null
        }.toMap()
    }

    private var widgetProviders: String
        get() = prefs.getString(WIDGET_PROVIDERS, "").toString()
        set(value) = prefs.edit { putString(WIDGET_PROVIDERS, value) }

    fun getWidgetProvider(widgetId: Int): String? {
        val map = parseWidgetProviders()
        return map[widgetId]
    }

    fun setWidgetProvider(widgetId: Int, provider: String) {
        val map = parseWidgetProviders().toMutableMap()
        map[widgetId] = provider
        widgetProviders = map.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    fun removeWidgetProvider(widgetId: Int) {
        val map = parseWidgetProviders().toMutableMap()
        map.remove(widgetId)
        widgetProviders = map.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    fun getAllWidgetProviders(): Map<Int, String> = parseWidgetProviders()

    private fun parseWidgetProviders(): Map<Int, String> {
        val raw = widgetProviders
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            // Format: "id:component/class" — split only on first ':'
            val sep = entry.indexOf(':')
            if (sep > 0) {
                val id = entry.substring(0, sep).trim().toIntOrNull()
                val comp = entry.substring(sep + 1).trim()
                if (id != null && comp.isNotBlank()) id to comp else null
            } else null
        }.toMap()
    }

    fun migrateWidgetIfNeeded() {
        if (widgetId != -1 && widgetIds.isBlank()) {
            setWidgetIdList(listOf(widgetId))
            widgetId = -1
        }
    }

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, value) }

    var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit { putBoolean(HIDDEN_APPS_UPDATED, value) }

    var swipeDownAction: Int
        get() = prefs.getInt(SWIPE_DOWN_ACTION, Constants.SwipeDownAction.NOTIFICATIONS)
        set(value) = prefs.edit { putInt(SWIPE_DOWN_ACTION, value) }

    var appName1: String
        get() = prefs.getString(APP_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_1, value) }

    var appName2: String
        get() = prefs.getString(APP_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_2, value) }

    var appName3: String
        get() = prefs.getString(APP_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_3, value) }

    var appName4: String
        get() = prefs.getString(APP_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_4, value) }

    var appName5: String
        get() = prefs.getString(APP_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_5, value) }

    var appName6: String
        get() = prefs.getString(APP_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_6, value) }

    var appName7: String
        get() = prefs.getString(APP_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_7, value) }

    var appName8: String
        get() = prefs.getString(APP_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_8, value) }

    var appPackage1: String
        get() = prefs.getString(APP_PACKAGE_1, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_1, value) }

    var appPackage2: String
        get() = prefs.getString(APP_PACKAGE_2, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_2, value) }

    var appPackage3: String
        get() = prefs.getString(APP_PACKAGE_3, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_3, value) }

    var appPackage4: String
        get() = prefs.getString(APP_PACKAGE_4, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_4, value) }

    var appPackage5: String
        get() = prefs.getString(APP_PACKAGE_5, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_5, value) }

    var appPackage6: String
        get() = prefs.getString(APP_PACKAGE_6, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_6, value) }

    var appPackage7: String
        get() = prefs.getString(APP_PACKAGE_7, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_7, value) }

    var appPackage8: String
        get() = prefs.getString(APP_PACKAGE_8, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_8, value) }

    var appActivityClassName1: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_1, value) }

    var appActivityClassName2: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_2, value) }

    var appActivityClassName3: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_3, value) }

    var appActivityClassName4: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_4, value) }

    var appActivityClassName5: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_5, value) }

    var appActivityClassName6: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_6, value) }

    var appActivityClassName7: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_7, value) }

    var appActivityClassName8: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_8, value) }

    var appUser1: String
        get() = prefs.getString(APP_USER_1, "").toString()
        set(value) = prefs.edit { putString(APP_USER_1, value) }

    var appUser2: String
        get() = prefs.getString(APP_USER_2, "").toString()
        set(value) = prefs.edit { putString(APP_USER_2, value) }

    var appUser3: String
        get() = prefs.getString(APP_USER_3, "").toString()
        set(value) = prefs.edit { putString(APP_USER_3, value) }

    var appUser4: String
        get() = prefs.getString(APP_USER_4, "").toString()
        set(value) = prefs.edit { putString(APP_USER_4, value) }

    var appUser5: String
        get() = prefs.getString(APP_USER_5, "").toString()
        set(value) = prefs.edit { putString(APP_USER_5, value) }

    var appUser6: String
        get() = prefs.getString(APP_USER_6, "").toString()
        set(value) = prefs.edit { putString(APP_USER_6, value) }

    var appUser7: String
        get() = prefs.getString(APP_USER_7, "").toString()
        set(value) = prefs.edit { putString(APP_USER_7, value) }

    var appUser8: String
        get() = prefs.getString(APP_USER_8, "").toString()
        set(value) = prefs.edit { putString(APP_USER_8, value) }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_LEFT, value) }

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_RIGHT, value) }

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_LEFT, value) }

    var appActivityClassNameSwipeLeft: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, value) }

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_RIGHT, value) }

    var appActivityClassNameRight: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, value) }

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_LEFT, value) }

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_RIGHT, value) }

    var clockAppPackage: String
        get() = prefs.getString(CLOCK_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_PACKAGE, value) }

    var clockAppUser: String
        get() = prefs.getString(CLOCK_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_USER, value) }

    var clockAppClassName: String?
        get() = prefs.getString(CLOCK_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_CLASS_NAME, value) }

    var calendarAppPackage: String
        get() = prefs.getString(CALENDAR_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_PACKAGE, value) }

    var calendarAppUser: String
        get() = prefs.getString(CALENDAR_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_USER, value) }

    var calendarAppClassName: String?
        get() = prefs.getString(CALENDAR_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_CLASS_NAME, value) }

    // Indexed accessors for home app slots (1-8) — same SharedPreferences keys as individual properties
    private fun nameKeyForSlot(slot: Int) = "APP_NAME_$slot"
    private fun packageKeyForSlot(slot: Int) = "APP_PACKAGE_$slot"
    private fun activityKeyForSlot(slot: Int) = "APP_ACTIVITY_CLASS_NAME_$slot"
    private fun userKeyForSlot(slot: Int) = "APP_USER_$slot"

    fun getHomeAppName(slot: Int): String = prefs.getString(nameKeyForSlot(slot), "").toString()
    fun setHomeAppName(slot: Int, value: String) = prefs.edit { putString(nameKeyForSlot(slot), value) }

    fun getHomeAppPackage(slot: Int): String = prefs.getString(packageKeyForSlot(slot), "").toString()
    fun setHomeAppPackage(slot: Int, value: String) = prefs.edit { putString(packageKeyForSlot(slot), value) }

    fun getHomeAppActivityClassName(slot: Int): String = prefs.getString(activityKeyForSlot(slot), "").toString()
    fun setHomeAppActivityClassName(slot: Int, value: String?) = prefs.edit { putString(activityKeyForSlot(slot), value) }

    fun getHomeAppUser(slot: Int): String = prefs.getString(userKeyForSlot(slot), "").toString()
    fun setHomeAppUser(slot: Int, value: String) = prefs.edit { putString(userKeyForSlot(slot), value) }

    fun getAppName(location: Int): String = getHomeAppName(location)
    fun getAppPackage(location: Int): String = getHomeAppPackage(location)
    fun getAppActivityClassName(location: Int): String = getHomeAppActivityClassName(location)
    fun getAppUser(location: Int): String = getHomeAppUser(location)

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").toString()

    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit().putString(appPackage, renameLabel).apply()

    // Keys to exclude from export (device-specific, not transferable)
    private val exportExcludeKeys = setOf(WIDGET_ID, WIDGET_IDS, WIDGET_HEIGHTS, WIDGET_PLACEMENT, WIDGET_PROVIDERS)

    fun exportToJson(): JSONObject {
        val json = JSONObject()
        for ((key, value) in prefs.all) {
            if (key in exportExcludeKeys) continue
            when (value) {
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is Float -> json.put(key, value.toDouble())
                is String -> json.put(key, value)
                is Set<*> -> {
                    val arr = org.json.JSONArray()
                    for (item in value) arr.put(item)
                    json.put(key, arr)
                }
            }
        }
        return json
    }

    fun importFromJson(json: JSONObject) {
        val editor = prefs.edit()
        for (key in json.keys()) {
            if (key in exportExcludeKeys) continue
            val value = json.get(key)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
                is org.json.JSONArray -> {
                    val set = mutableSetOf<String>()
                    for (i in 0 until value.length()) set.add(value.getString(i))
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.apply()
    }
}