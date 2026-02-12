package app.olauncher.helper

import android.content.Context

object QuickNoteManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val QUICK_NOTE_TEXT = "QUICK_NOTE_TEXT"
    private const val QUICK_NOTE_ENABLED = "QUICK_NOTE_ENABLED"
    private const val QUICK_NOTE_SLOT = "QUICK_NOTE_SLOT" // old key for migration

    @Volatile private var migrated = false

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)

    private fun migrateIfNeeded(context: Context) {
        if (migrated) return
        val p = prefs(context)
        if (p.contains(QUICK_NOTE_SLOT)) {
            val oldSlot = p.getInt(QUICK_NOTE_SLOT, -1)
            p.edit()
                .putBoolean(QUICK_NOTE_ENABLED, oldSlot != -1)
                .remove(QUICK_NOTE_SLOT)
                .apply()
        }
        migrated = true
    }

    fun isEnabled(context: Context): Boolean {
        migrateIfNeeded(context)
        return prefs(context).getBoolean(QUICK_NOTE_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(QUICK_NOTE_ENABLED, enabled).apply()
    }

    fun getText(context: Context): String =
        prefs(context).getString(QUICK_NOTE_TEXT, "") ?: ""

    fun setText(context: Context, text: String) {
        prefs(context).edit().putString(QUICK_NOTE_TEXT, text).apply()
    }

    fun getPreviewText(context: Context): String {
        val text = getText(context)
        return when {
            text.isEmpty() -> "Note"
            text.length > 20 -> text.substring(0, 20) + "\u2026"
            else -> text
        }
    }
}
