package app.olauncher.helper

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.data.Constants
import app.olauncher.data.Prefs

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val prefs by lazy { Prefs(applicationContext) }

    override suspend fun doWork(): Result {
        val success =
            if (isOlauncherDefault(applicationContext).not())
                true
            else if (prefs.dailyWallpaper) {
                val wallType = checkWallpaperType()
                val wallpaperUrl = getTodaysWallpaper(wallType, prefs.firstOpenTime)
                if (prefs.dailyWallpaperUrl == wallpaperUrl)
                    true
                else {
                    prefs.dailyWallpaperUrl = wallpaperUrl
                    setWallpaper(applicationContext, wallpaperUrl)
                }
            } else
                true

        return if (success) {
            Result.success()
        } else {
            Log.w("WallpaperWorker", "Wallpaper update failed, retrying")
            Result.retry()
        }
    }

    private fun checkWallpaperType(): String {
        return when (prefs.appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> Constants.WALL_TYPE_DARK
            AppCompatDelegate.MODE_NIGHT_NO -> Constants.WALL_TYPE_LIGHT
            else -> if (applicationContext.isDarkThemeOn())
                Constants.WALL_TYPE_DARK
            else
                Constants.WALL_TYPE_LIGHT
        }
    }
}
