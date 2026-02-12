package app.olauncher.helper

import android.content.Context
import app.olauncher.data.FolderApp
import app.olauncher.data.FolderGroup
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages folder CRUD operations with JSON storage in SharedPreferences.
 *
 * Storage format (under key "FOLDER_DATA"):
 * {
 *   "1": {
 *     "name": "Social",
 *     "apps": [
 *       {"appName":"...", "packageName":"...", "activityClassName":"...", "userString":"..."}
 *     ]
 *   },
 *   ...
 * }
 *
 * Thread-safe: all reads/writes are synchronized on [lock].
 */
class FolderManager(context: Context) {

    companion object {
        private val lock = Any()
        private const val PREFS_NAME = "app.olauncher"
        private const val KEY_FOLDER_DATA = "FOLDER_DATA"
        private const val MAX_APPS_PER_FOLDER = 4

        private const val JSON_NAME = "name"
        private const val JSON_APPS = "apps"
        private const val JSON_APP_NAME = "appName"
        private const val JSON_PACKAGE_NAME = "packageName"
        private const val JSON_ACTIVITY_CLASS_NAME = "activityClassName"
        private const val JSON_USER_STRING = "userString"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, 0)

    /** Returns true if the given home-app [slot] is currently occupied by a folder. */
    fun isFolderSlot(slot: Int): Boolean {
        synchronized(lock) {
            val root = readJson()
            return root.has(slot.toString())
        }
    }

    /** Returns the [FolderGroup] stored at [slot], or null if none exists. */
    fun getFolderGroup(slot: Int): FolderGroup? {
        synchronized(lock) {
            val root = readJson()
            val obj = root.optJSONObject(slot.toString()) ?: return null
            return parseFolderGroup(obj)
        }
    }

    /**
     * Creates (or replaces) a folder at [slot].
     *
     * @param slot  The home-screen slot index.
     * @param name  Display name for the folder.
     * @param apps  Apps to include (max [MAX_APPS_PER_FOLDER]; extras are silently dropped).
     */
    fun createFolder(slot: Int, name: String, apps: List<FolderApp>) {
        synchronized(lock) {
            val root = readJson()
            val folderObj = JSONObject()
            folderObj.put(JSON_NAME, name)

            val appsArray = JSONArray()
            val capped = apps.take(MAX_APPS_PER_FOLDER)
            for (app in capped) {
                val appObj = JSONObject()
                appObj.put(JSON_APP_NAME, app.appName)
                appObj.put(JSON_PACKAGE_NAME, app.packageName)
                appObj.put(JSON_ACTIVITY_CLASS_NAME, app.activityClassName)
                appObj.put(JSON_USER_STRING, app.userString)
                appsArray.put(appObj)
            }
            folderObj.put(JSON_APPS, appsArray)

            root.put(slot.toString(), folderObj)
            writeJson(root)
        }
    }

    /** Removes the folder at [slot], if any. */
    fun removeFolder(slot: Int) {
        synchronized(lock) {
            val root = readJson()
            root.remove(slot.toString())
            writeJson(root)
        }
    }

    /** Returns every stored folder keyed by slot number. */
    fun getAllFolders(): Map<Int, FolderGroup> {
        synchronized(lock) {
            val root = readJson()
            val result = mutableMapOf<Int, FolderGroup>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val slotNum = key.toIntOrNull() ?: continue
                val obj = root.optJSONObject(key) ?: continue
                result[slotNum] = parseFolderGroup(obj)
            }
            return result
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun readJson(): JSONObject {
        val raw = prefs.getString(KEY_FOLDER_DATA, null)
        return if (raw.isNullOrBlank()) JSONObject() else try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeJson(json: JSONObject) {
        prefs.edit().putString(KEY_FOLDER_DATA, json.toString()).apply()
    }

    private fun parseFolderGroup(obj: JSONObject): FolderGroup {
        val name = obj.optString(JSON_NAME, "")
        val appsArray = obj.optJSONArray(JSON_APPS) ?: JSONArray()
        val apps = mutableListOf<FolderApp>()
        for (i in 0 until appsArray.length()) {
            val a = appsArray.optJSONObject(i) ?: continue
            apps.add(
                FolderApp(
                    appName = a.optString(JSON_APP_NAME, ""),
                    packageName = a.optString(JSON_PACKAGE_NAME, ""),
                    activityClassName = a.optString(JSON_ACTIVITY_CLASS_NAME, ""),
                    userString = a.optString(JSON_USER_STRING, "")
                )
            )
        }
        return FolderGroup(name, apps)
    }
}
