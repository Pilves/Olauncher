package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import androidx.core.content.edit
import app.olauncher.R
import app.olauncher.data.Constants

object DoubleTapActionManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val KEY_ACTION = "DOUBLE_TAP_ACTION"
    private const val KEY_APP_PACKAGE = "DOUBLE_TAP_APP_PACKAGE"
    private const val KEY_APP_ACTIVITY = "DOUBLE_TAP_APP_ACTIVITY"
    private const val KEY_APP_USER = "DOUBLE_TAP_APP_USER"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)

    fun getAction(context: Context): Int =
        prefs(context).getInt(KEY_ACTION, Constants.GestureAction.LOCK_SCREEN)

    fun setAction(context: Context, action: Int) {
        prefs(context).edit {
            putInt(KEY_ACTION, action)
        }
    }

    fun getAppPackage(context: Context): String =
        prefs(context).getString(KEY_APP_PACKAGE, "").orEmpty()

    fun getAppActivity(context: Context): String =
        prefs(context).getString(KEY_APP_ACTIVITY, "").orEmpty()

    fun getAppUser(context: Context): String =
        prefs(context).getString(KEY_APP_USER, "").orEmpty()

    fun setApp(
        context: Context,
        packageName: String,
        activityClassName: String,
        userString: String
    ) {
        prefs(context).edit {
            putString(KEY_APP_PACKAGE, packageName)
            putString(KEY_APP_ACTIVITY, activityClassName)
            putString(KEY_APP_USER, userString)
        }
    }

    fun execute(
        context: Context,
        lockPhone: () -> Unit,
        toggleFlashlight: () -> Unit
    ) {
        when (getAction(context)) {
            Constants.GestureAction.OPEN_APP -> {
                val packageName = getAppPackage(context)
                val activityName = getAppActivity(context)
                val userString = getAppUser(context)
                if (packageName.isNotEmpty() && activityName.isNotEmpty()) {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val component = ComponentName(packageName, activityName)
                    val userHandle = getUserHandleFromString(context, userString)
                    try {
                        launcherApps.startMainActivity(component, userHandle, null, null)
                    } catch (e: Exception) {
                        context.showToast(context.getString(R.string.unable_to_open_app))
                    }
                }
            }
            Constants.GestureAction.OPEN_NOTIFICATIONS -> expandNotificationDrawer(context)
            Constants.GestureAction.OPEN_SEARCH -> context.openSearch()
            Constants.GestureAction.LOCK_SCREEN -> lockPhone()
            Constants.GestureAction.OPEN_CAMERA -> openCameraApp(context)
            Constants.GestureAction.TOGGLE_FLASHLIGHT -> toggleFlashlight()
            Constants.GestureAction.NONE -> { /* do nothing */ }
        }
    }
}
