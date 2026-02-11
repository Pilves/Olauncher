package app.olauncher.helper

import android.content.Context
import androidx.core.content.edit

object SwipeUpAppManager {

    private const val PREFS_NAME = "app.olauncher"

    private fun nameKey(slot: Int) = "SWIPE_UP_NAME_$slot"
    private fun packageKey(slot: Int) = "SWIPE_UP_PACKAGE_$slot"
    private fun activityKey(slot: Int) = "SWIPE_UP_ACTIVITY_$slot"
    private fun userKey(slot: Int) = "SWIPE_UP_USER_$slot"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)

    fun hasSwipeUpApp(context: Context, slot: Int): Boolean =
        getSwipeUpAppPackage(context, slot).isNotEmpty()

    fun getSwipeUpAppName(context: Context, slot: Int): String =
        prefs(context).getString(nameKey(slot), "").orEmpty()

    fun getSwipeUpAppPackage(context: Context, slot: Int): String =
        prefs(context).getString(packageKey(slot), "").orEmpty()

    fun getSwipeUpAppActivity(context: Context, slot: Int): String =
        prefs(context).getString(activityKey(slot), "").orEmpty()

    fun getSwipeUpAppUser(context: Context, slot: Int): String =
        prefs(context).getString(userKey(slot), "").orEmpty()

    fun setSwipeUpApp(
        context: Context,
        slot: Int,
        name: String,
        packageName: String,
        activityClassName: String,
        userString: String
    ) {
        prefs(context).edit {
            putString(nameKey(slot), name)
            putString(packageKey(slot), packageName)
            putString(activityKey(slot), activityClassName)
            putString(userKey(slot), userString)
        }
    }

    fun clearSwipeUpApp(context: Context, slot: Int) {
        prefs(context).edit {
            putString(nameKey(slot), "")
            putString(packageKey(slot), "")
            putString(activityKey(slot), "")
            putString(userKey(slot), "")
        }
    }
}
