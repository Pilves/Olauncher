package app.olauncher.helper

import android.content.Context
import android.provider.Settings

/**
 * Manages device-wide grayscale via Android's built-in display daltonizer.
 *
 * If WRITE_SECURE_SETTINGS is granted, toggles it directly.
 * Otherwise, the caller should open accessibility settings for the user.
 */
object GrayscaleManager {

    private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DALTONIZER_MODE = "accessibility_display_daltonizer"
    private const val DALTONIZER_GRAYSCALE = 0

    /**
     * Whether device-wide grayscale is currently active (reads system state, no permission needed).
     */
    fun isEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, DALTONIZER_ENABLED, 0) == 1 &&
                Settings.Secure.getInt(context.contentResolver, DALTONIZER_MODE, -1) == DALTONIZER_GRAYSCALE
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Try to toggle device-wide grayscale via the display daltonizer.
     * Returns true if successful, false if WRITE_SECURE_SETTINGS is not granted.
     */
    fun toggle(context: Context): Boolean {
        val enable = !isEnabled(context)
        return try {
            if (enable) {
                Settings.Secure.putInt(context.contentResolver, DALTONIZER_ENABLED, 1)
                Settings.Secure.putInt(context.contentResolver, DALTONIZER_MODE, DALTONIZER_GRAYSCALE)
            } else {
                Settings.Secure.putInt(context.contentResolver, DALTONIZER_ENABLED, 0)
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
