package app.olauncher

import android.app.Application
import android.util.Log
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.FocusModeManager
import app.olauncher.helper.GestureLetterManager
import app.olauncher.helper.HabitStreakManager
import app.olauncher.helper.ScreenTimeLimitManager
import app.olauncher.helper.SingleLiveEvent
import app.olauncher.helper.ThemeScheduleManager
import app.olauncher.helper.WallpaperWorker
import app.olauncher.helper.WeatherManager
import app.olauncher.helper.formattedTimeSpent
import app.olauncher.helper.getAppsList
import app.olauncher.helper.hasBeenMinutes
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.showToast
import app.olauncher.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)
    private val launcherApps by lazy {
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }
    private val eventLogWrapper by lazy { EventLogWrapper(appContext) }

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Boolean>()
    val toggleDateTime = MutableLiveData<Unit>()
    val updateSwipeApps = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()
    val isOlauncherDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Int>()
    val screenTimeValue = MutableLiveData<String>()
    val perAppScreenTime = MutableLiveData<Map<String, Long>>()
    val weatherValue = MutableLiveData<String>()

    var pendingGestureLetter: Char? = null
    private var lastWeatherFetch: Long = 0L

    val showDialog = SingleLiveEvent<String>()
    val resetLauncherLiveData = SingleLiveEvent<Unit?>()

    fun selectedApp(appModel: AppModel, flag: Int) {
        when (flag) {
            Constants.FLAG_LAUNCH_APP, Constants.FLAG_HIDDEN_APPS -> {
                if (!FocusModeManager.isAppAllowed(appContext, appModel.appPackage)) {
                    appContext.showToast(appContext.getString(R.string.app_blocked_focus))
                    return
                }
                launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
                viewModelScope.launch(Dispatchers.IO) {
                    try { ScreenTimeLimitManager.checkAndWarn(appContext, appModel.appPackage) } catch (e: Exception) { e.printStackTrace() }
                    try { HabitStreakManager.recordLaunch(appContext, appModel.appPackage) } catch (e: Exception) { e.printStackTrace() }
                }
            }

            in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8 -> {
                prefs.setHomeAppName(flag, appModel.appLabel)
                prefs.setHomeAppPackage(flag, appModel.appPackage)
                prefs.setHomeAppUser(flag, appModel.user.toString())
                prefs.setHomeAppActivityClassName(flag, appModel.activityClassName)
                refreshHome(false)
            }

            Constants.FLAG_SET_SWIPE_LEFT_APP -> {
                prefs.appNameSwipeLeft = appModel.appLabel
                prefs.appPackageSwipeLeft = appModel.appPackage
                prefs.appUserSwipeLeft = appModel.user.toString()
                prefs.appActivityClassNameSwipeLeft = appModel.activityClassName
                updateSwipeApps()
            }

            Constants.FLAG_SET_SWIPE_RIGHT_APP -> {
                prefs.appNameSwipeRight = appModel.appLabel
                prefs.appPackageSwipeRight = appModel.appPackage
                prefs.appUserSwipeRight = appModel.user.toString()
                prefs.appActivityClassNameRight = appModel.activityClassName
                updateSwipeApps()
            }

            Constants.FLAG_SET_CLOCK_APP -> {
                prefs.clockAppPackage = appModel.appPackage
                prefs.clockAppUser = appModel.user.toString()
                prefs.clockAppClassName = appModel.activityClassName
            }

            Constants.FLAG_SET_CALENDAR_APP -> {
                prefs.calendarAppPackage = appModel.appPackage
                prefs.calendarAppUser = appModel.user.toString()
                prefs.calendarAppClassName = appModel.activityClassName
            }

            in Constants.FLAG_SET_SWIPE_UP_APP_1..Constants.FLAG_SET_SWIPE_UP_APP_8 -> {
                val slot = flag - Constants.FLAG_SET_SWIPE_UP_APP_1 + 1
                app.olauncher.helper.SwipeUpAppManager.setSwipeUpApp(
                    appContext, slot,
                    appModel.appLabel, appModel.appPackage,
                    appModel.activityClassName ?: "", appModel.user.toString()
                )
                refreshHome(false)
            }

            Constants.FLAG_SET_DOUBLE_TAP_APP -> {
                app.olauncher.helper.DoubleTapActionManager.setApp(
                    appContext,
                    appModel.appPackage,
                    appModel.activityClassName ?: "",
                    appModel.user.toString()
                )
            }

            Constants.FLAG_SET_GESTURE_LETTER_APP -> {
                pendingGestureLetter?.let { letter ->
                    GestureLetterManager.setMapping(
                        appContext, letter,
                        appModel.appPackage,
                        appModel.activityClassName ?: "",
                        appModel.user.toString()
                    )
                    pendingGestureLetter = null
                }
            }
        }
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun refreshHome(appCountUpdated: Boolean) {
        refreshHome.postValue(appCountUpdated)
    }

    fun toggleDateTime() {
        toggleDateTime.postValue(Unit)
    }

    private fun updateSwipeApps() {
        updateSwipeApps.postValue(Unit)
    }

    private fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        viewModelScope.launch {
            val component = withContext(Dispatchers.IO) {
                try {
                    val activityInfo = launcherApps.getActivityList(packageName, userHandle)
                    if (activityClassName.isNullOrBlank()) {
                        when (activityInfo.size) {
                            0 -> null
                            1 -> ComponentName(packageName, activityInfo[0].name)
                            else -> ComponentName(packageName, activityInfo[activityInfo.size - 1].name)
                        }
                    } else {
                        ComponentName(packageName, activityClassName)
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to resolve activity for $packageName", e)
                    null
                }
            }
            if (component == null) {
                appContext.showToast(appContext.getString(R.string.app_not_found))
                return@launch
            }
            try {
                launcherApps.startMainActivity(component, userHandle, null, null)
            } catch (e: SecurityException) {
                try {
                    launcherApps.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
                } catch (e: Exception) {
                    appContext.showToast(appContext.getString(R.string.unable_to_open_app))
                }
            } catch (e: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        }
    }

    fun getAppList(includeHiddenApps: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            appList.postValue(getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps))
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch(Dispatchers.IO) {
            hiddenApps.postValue(getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true))
        }
    }

    fun isOlauncherDefault() {
        isOlauncherDefault.postValue(isOlauncherDefault(appContext))
    }

    fun setWallpaperWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val uploadWorkRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(8, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                Constants.WALLPAPER_WORKER_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                uploadWorkRequest
            )
    }

    fun cancelWallpaperWorker() {
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME)
        prefs.dailyWallpaperUrl = ""
        prefs.dailyWallpaper = false
    }

    fun getWeather() {
        if (!WeatherManager.isEnabled(appContext)) return
        val now = System.currentTimeMillis()
        if (now - lastWeatherFetch < 30 * 60 * 1000L) return  // 30 min throttle
        lastWeatherFetch = now
        viewModelScope.launch(Dispatchers.IO) {
            val temp = WeatherManager.fetchWeather(appContext)
            if (temp != null) {
                weatherValue.postValue(temp)
            } else {
                val cached = WeatherManager.getDisplayString(appContext)
                if (cached.isNotEmpty()) weatherValue.postValue(cached)
            }
        }
    }

    fun startThemeScheduleWorker() {
        ThemeScheduleManager.startWorker(appContext)
    }

    fun cancelThemeScheduleWorker() {
        ThemeScheduleManager.cancelWorker(appContext)
    }

    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = gravity
    }

    fun getTodaysScreenTime() {
        if (prefs.screenTimeLastUpdated.hasBeenMinutes(1).not()) return

        viewModelScope.launch(Dispatchers.IO) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val timeSpent = eventLogWrapper.aggregateSimpleUsageStats(
                eventLogWrapper.aggregateForegroundStats(
                    eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
                )
            )
            val viewTimeSpent = appContext.formattedTimeSpent(timeSpent)
            screenTimeValue.postValue(viewTimeSpent)
            prefs.screenTimeLastUpdated = endTime
        }
    }

    fun getPerAppScreenTime() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return

        viewModelScope.launch(Dispatchers.IO) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val usageStats = eventLogWrapper.aggregateForegroundStats(
                eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
            )
            val map = mutableMapOf<String, Long>()
            for (stat in usageStats) {
                map[stat.applicationId] = (map[stat.applicationId] ?: 0L) + stat.timeUsed
            }
            perAppScreenTime.postValue(map)
        }
    }

    fun setDefaultClockApp() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Constants.CLOCK_APP_PACKAGES.firstOrNull { appContext.isPackageInstalled(it) }?.let { packageName ->
                    appContext.packageManager.getLaunchIntentForPackage(packageName)?.component?.className?.let {
                        prefs.clockAppPackage = packageName
                        prefs.clockAppClassName = it
                        prefs.clockAppUser = android.os.Process.myUserHandle().toString()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to set default clock app", e)
            }
        }
    }
}