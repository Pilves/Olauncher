package app.olauncher.helper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope

class ThemeScheduleWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private companion object {
        const val PREFS_NAME = "app.olauncher"
        const val APP_THEME = "APP_THEME"
        const val LAUNCHER_RECREATE_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    }

    override suspend fun doWork(): Result = coroutineScope {
        val mode = ThemeScheduleManager.getMode(applicationContext)

        // If manual mode, nothing to do
        if (mode == ThemeScheduleManager.MODE_MANUAL) {
            return@coroutineScope Result.success()
        }

        val shouldBeDark = ThemeScheduleManager.shouldBeDark(applicationContext)
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, 0)
        val currentTheme = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)

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
                .putInt(APP_THEME, newTheme)
                .putLong(LAUNCHER_RECREATE_TIMESTAMP, System.currentTimeMillis())
                .apply()
        }

        Result.success()
    }
}
