package app.olauncher.helper

import android.content.Context

object QuickNoteManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val QUICK_NOTE_TEXT = "QUICK_NOTE_TEXT"
    private const val QUICK_NOTE_SLOT = "QUICK_NOTE_SLOT"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)

    fun getText(context: Context): String =
        prefs(context).getString(QUICK_NOTE_TEXT, "") ?: ""

    fun setText(context: Context, text: String) {
        prefs(context).edit().putString(QUICK_NOTE_TEXT, text).apply()
    }

    fun getSlot(context: Context): Int =
        prefs(context).getInt(QUICK_NOTE_SLOT, -1)

    fun setSlot(context: Context, slot: Int) {
        prefs(context).edit().putInt(QUICK_NOTE_SLOT, slot).apply()
    }

    fun clearSlot(context: Context) {
        prefs(context).edit().putInt(QUICK_NOTE_SLOT, -1).apply()
    }

    fun isNoteSlot(context: Context, slot: Int): Boolean =
        getSlot(context) == slot

    fun getPreviewText(context: Context): String {
        val text = getText(context)
        return when {
            text.isEmpty() -> "Note"
            text.length > 20 -> text.substring(0, 20) + "\u2026"
            else -> text
        }
    }
}
