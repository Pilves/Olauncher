package app.olauncher.helper

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.olauncher.R
import app.olauncher.data.Prefs

class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        Prefs(applicationContext).lockModeOn = true
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val source: AccessibilityNodeInfo = event.source ?: return
            try {
                if ((source.className == "android.widget.FrameLayout") &&
                    (source.contentDescription == getString(R.string.lock_layout_description))
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    }
                }
            } finally {
                if (Build.VERSION.SDK_INT < 34) {
                    @Suppress("DEPRECATION")
                    source.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e("AccessibilityService", "Error handling accessibility event", e)
        }
    }

    override fun onInterrupt() {

    }
}