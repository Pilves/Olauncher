package app.olauncher.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

/**
 * Discovers installed icon packs and loads individual icons from them.
 *
 * Icon packs are discovered via the `org.adw.launcher.THEMES` intent action.
 * Each pack ships an `appfilter.xml` resource that maps component names to
 * drawable names.
 *
 * Icons are intended to be shown as 20 dp compound drawables on TextViews
 * (set via [TextView.setCompoundDrawablesRelativeWithIntrinsicBounds]).
 */
object IconPackManager {

    /** Intent action used by ADW-compatible icon packs. */
    private const val ADW_THEMES_ACTION = "org.adw.launcher.THEMES"

    /**
     * Cache: icon-pack package name  ->  (componentName -> drawableName).
     * Cleared when the process dies; that is acceptable for a launcher.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    /** Secondary index: icon-pack package name -> (app package name -> drawableName) for O(1) fallback lookups. */
    private val packageIndex = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    /**
     * Returns a list of installed icon packs as (packageName, appLabel) pairs.
     */
    fun getAvailableIconPacks(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(ADW_THEMES_ACTION)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        return resolveInfos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            val label = ri.loadLabel(pm)?.toString() ?: pkg
            pkg to label
        }.distinctBy { it.first }
    }

    /**
     * Attempts to load the icon for a specific app from the given icon pack.
     *
     * @param context            Application or activity context.
     * @param iconPackPackage    Package name of the icon pack.
     * @param appPackage         Package name of the target app.
     * @param activityClassName  Fully-qualified activity class name (optional).
     * @return The icon [Drawable], or null if the pack does not contain one.
     */
    fun getIconForApp(
        context: Context,
        iconPackPackage: String,
        appPackage: String,
        activityClassName: String?
    ): Drawable? {
        return try {
            val map = loadIconPack(context, iconPackPackage)

            // Try exact component match first: "ComponentInfo{pkg/activity}"
            val componentKey = if (activityClassName != null) {
                "ComponentInfo{$appPackage/$activityClassName}"
            } else null

            val drawableName = (componentKey?.let { map[it] }
                // Some packs use a shorter form without ComponentInfo wrapper
                ?: componentKey?.let { map["$appPackage/$activityClassName"] }
                // Fallback: O(1) lookup by package name via secondary index
                ?: packageIndex[iconPackPackage]?.get(appPackage))
                ?: return null

            val packResources = context.packageManager
                .getResourcesForApplication(iconPackPackage)
            val resId = packResources.getIdentifier(
                drawableName, "drawable", iconPackPackage
            )
            if (resId == 0) return null

            @Suppress("DEPRECATION")
            packResources.getDrawable(resId, null)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `appfilter.xml` from the icon pack identified by [packageName]
     * and returns a map of component name -> drawable name.
     *
     * Results are cached in-memory so repeated calls are cheap.
     */
    fun loadIconPack(context: Context, packageName: String): Map<String, String> {
        cache[packageName]?.let { return it }

        val result = mutableMapOf<String, String>()
        try {
            val pm = context.packageManager
            val packResources = pm.getResourcesForApplication(packageName)

            val resId = packResources.getIdentifier(
                "appfilter", "xml", packageName
            )
            if (resId == 0) {
                // Some icon packs store appfilter.xml as a raw asset instead.
                // Attempt to read from assets as a fallback.
                val rawResult = parseAppFilterFromAssets(packResources, packageName)
                if (rawResult != null) {
                    cache[packageName] = rawResult
                    buildPackageIndex(packageName, rawResult)
                    return rawResult
                }
                cache[packageName] = result
                buildPackageIndex(packageName, result)
                return result
            }

            val parser = packResources.getXml(resId)
            parseAppFilterXml(parser, result)
        } catch (_: PackageManager.NameNotFoundException) {
            // Icon pack not installed.
        } catch (_: Exception) {
            // Malformed XML or other issue — return whatever we parsed so far.
        }

        cache[packageName] = result
        buildPackageIndex(packageName, result)
        return result
    }

    /**
     * Builds a package-name -> drawableName index from the component map
     * for O(1) fallback lookups by package name.
     */
    private fun buildPackageIndex(iconPackPackage: String, map: Map<String, String>) {
        val pkgMap = mutableMapOf<String, String>()
        for ((component, icon) in map) {
            val pkg = component.substringBefore("/")
                .removePrefix("ComponentInfo{")
            if (pkg !in pkgMap) {
                pkgMap[pkg] = icon
            }
        }
        packageIndex[iconPackPackage] = pkgMap
    }

    // ── Internal parsing helpers ────────────────────────────────────────

    /**
     * Walks an [XmlPullParser] over `appfilter.xml` content and fills [out].
     *
     * Expected element:
     * ```xml
     * <item component="ComponentInfo{com.pkg/com.pkg.Activity}" drawable="icon_name"/>
     * ```
     */
    private fun parseAppFilterXml(parser: XmlPullParser, out: MutableMap<String, String>) {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                    out[component] = drawable
                }
            }
            eventType = parser.next()
        }
    }

    /**
     * Fallback: some icon packs ship `appfilter.xml` only inside their assets
     * folder rather than as a compiled XML resource.
     */
    private fun parseAppFilterFromAssets(
        packResources: Resources,
        packageName: String
    ): Map<String, String>? {
        return try {
            packResources.assets.open("appfilter.xml").use { stream ->
                val result = mutableMapOf<String, String>()
                val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(stream, "UTF-8")
                parseAppFilterXml(parser, result)
                result
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        cache.clear()
        packageIndex.clear()
    }
}
