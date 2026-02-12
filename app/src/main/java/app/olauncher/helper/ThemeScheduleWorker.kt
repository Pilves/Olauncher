package app.olauncher.helper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.data.Prefs

class ThemeScheduleWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val mode = ThemeScheduleManager.getMode(applicationContext)

        if (mode == ThemeScheduleManager.MODE_MANUAL) {
            return Result.success()
        }

        val shouldBeDark = try {
            ThemeScheduleManager.shouldBeDark(applicationContext)
        } catch (_: Exception) {
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences(Prefs.PREFS_NAME, 0)
        val currentTheme = prefs.getInt(Prefs.KEY_APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)

        val isDark = currentTheme == AppCompatDelegate.MODE_NIGHT_YES
        val isLight = currentTheme == AppCompatDelegate.MODE_NIGHT_NO

        val needsChange = (shouldBeDark && isLight) || (!shouldBeDark && isDark)

        if (needsChange) {
            val newTheme = if (shouldBeDark) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            prefs.edit()
                .putInt(Prefs.KEY_APP_THEME, newTheme)
                .putLong(Prefs.KEY_LAUNCHER_RECREATE_TIMESTAMP, System.currentTimeMillis())
                .apply()
        }

        return Result.success()
    }
}
