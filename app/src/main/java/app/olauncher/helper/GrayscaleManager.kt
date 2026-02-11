package app.olauncher.helper

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View

/**
 * Applies or removes a grayscale filter on the root view using a hardware layer.
 * The enabled state is persisted in SharedPreferences ("app.olauncher").
 */
object GrayscaleManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val KEY_GRAYSCALE_ENABLED = "GRAYSCALE_ENABLED"

    /**
     * Applies a full grayscale (saturation 0) filter to the given root view
     * using a hardware layer.
     */
    fun apply(rootView: View) {
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        rootView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }

    /**
     * Removes any layer-based filter from the given root view.
     */
    fun remove(rootView: View) {
        rootView.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    /**
     * Returns whether grayscale mode is enabled in preferences.
     */
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_GRAYSCALE_ENABLED, false)
    }

    /**
     * Sets the grayscale enabled preference.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_GRAYSCALE_ENABLED, enabled)
            .apply()
    }

    /**
     * Toggles the grayscale enabled preference.
     */
    fun toggle(context: Context) {
        setEnabled(context, !isEnabled(context))
    }
}
