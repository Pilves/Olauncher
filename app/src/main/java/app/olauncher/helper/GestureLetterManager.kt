package app.olauncher.helper

import android.content.Context

/**
 * Manages gesture-letter mappings: which letter launches which app.
 * Each letter maps to a Triple of (packageName, activityClassName, userString).
 *
 * All state is persisted directly in SharedPreferences ("app.olauncher").
 */
object GestureLetterManager {

    private const val PREFS_NAME = "app.olauncher"
    private const val KEY_ENABLED = "GESTURE_LETTERS_ENABLED"
    private const val KEY_MAPPINGS = "GESTURE_LETTER_MAPPINGS"

    private val lock = Any()

    private val supportedLetters = listOf('A', 'C', 'L', 'M', 'N', 'O', 'S', 'V', 'W', 'Z')

    /**
     * Returns true if gesture letters are enabled.
     */
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_ENABLED, false)
    }

    /**
     * Enables or disables gesture letters.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /**
     * Sets a mapping from a letter to an app (package, activity, user).
     */
    fun setMapping(
        context: Context,
        letter: Char,
        packageName: String,
        activityClassName: String,
        userString: String
    ) {
        synchronized(lock) {
            val mappings = parseMappings(context).toMutableMap()
            mappings[letter.uppercaseChar()] = Triple(packageName, activityClassName, userString)
            context.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putString(KEY_MAPPINGS, serializeMappings(mappings))
                .apply()
        }
    }

    /**
     * Returns the mapping for a letter, or null if not set.
     * Returns Triple of (packageName, activityClassName, userString).
     */
    fun getMapping(context: Context, letter: Char): Triple<String, String, String>? {
        synchronized(lock) {
            return parseMappings(context)[letter.uppercaseChar()]
        }
    }

    /**
     * Removes the mapping for a letter.
     */
    fun removeMapping(context: Context, letter: Char) {
        synchronized(lock) {
            val mappings = parseMappings(context).toMutableMap()
            mappings.remove(letter.uppercaseChar())
            context.getSharedPreferences(PREFS_NAME, 0)
                .edit()
                .putString(KEY_MAPPINGS, serializeMappings(mappings))
                .apply()
        }
    }

    /**
     * Returns all current letter-to-app mappings.
     */
    fun getAllMappings(context: Context): Map<Char, Triple<String, String, String>> {
        synchronized(lock) {
            return parseMappings(context)
        }
    }

    /**
     * Returns the list of letters that the gesture recognizer can detect.
     */
    fun getSupportedLetters(): List<Char> = supportedLetters

    // --- Internal helpers ---

    /**
     * Parses mappings from prefs.
     * Format: "A:com.pkg:activity:user,B:com.pkg2:activity2:user2,..."
     */
    private fun parseMappings(context: Context): Map<Char, Triple<String, String, String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val csv = prefs.getString(KEY_MAPPINGS, "") ?: ""
        if (csv.isBlank()) return emptyMap()

        val result = mutableMapOf<Char, Triple<String, String, String>>()
        for (entry in csv.split(",")) {
            val parts = entry.trim().split(":")
            if (parts.size == 4) {
                val letter = parts[0].firstOrNull() ?: continue
                result[letter.uppercaseChar()] = Triple(parts[1], parts[2], parts[3])
            }
        }
        return result
    }

    /**
     * Serializes mappings to CSV format.
     */
    private fun serializeMappings(data: Map<Char, Triple<String, String, String>>): String {
        return data.entries.joinToString(",") { (letter, triple) ->
            "$letter:${triple.first}:${triple.second}:${triple.third}"
        }
    }
}
