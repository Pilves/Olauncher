package app.olauncher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import app.olauncher.R
import app.olauncher.helper.AppLimitManager
import app.olauncher.helper.dpToPx
import app.olauncher.helper.formattedTimeSpent
import app.olauncher.helper.getColorFromAttr
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * A BottomSheetDialog that shows the most-used apps with preset limit options.
 * Users can assign per-app daily time limits with dialog-based warnings.
 *
 * @param context The activity context
 * @param appUsageMap Map of packageName to today's usage in milliseconds, from the ViewModel
 */
class ScreenTimeLimitDialog(
    context: Context,
    private val appUsageMap: Map<String, Long>
) : BottomSheetDialog(context) {

    private data class LimitOption(val label: String, val minutes: Int)

    private val limitOptions = listOf(
        LimitOption("15m", 15),
        LimitOption("30m", 30),
        LimitOption("1h", 60),
        LimitOption("2h", 120),
        LimitOption("Unlimited", -1)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bgColor = context.getColorFromAttr(R.attr.primaryInverseColor)
        val textColor = context.getColorFromAttr(R.attr.primaryColor)
        val secondaryTextColor = context.getColorFromAttr(R.attr.primaryColorTrans50)

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            setBackgroundColor(bgColor)
        }

        // Title
        val titleView = TextView(context).apply {
            text = context.getString(R.string.app_limits)
            textSize = 18f
            setTextColor(textColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16.dpToPx())
        }
        rootLayout.addView(titleView)

        // Scrollable app list
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                height = LinearLayout.LayoutParams.WRAP_CONTENT
            }
        }

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Sort apps by usage descending, take top 15
        val currentLimits = AppLimitManager.getAllLimits(context)
        val sortedApps = appUsageMap.entries
            .sortedByDescending { it.value }
            .take(15)

        val pm = context.packageManager

        for ((packageName, usageMs) in sortedApps) {
            val appName = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // App name
            val nameView = TextView(context).apply {
                text = appName
                textSize = 15f
                setTextColor(textColor)
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            row.addView(nameView)

            // Current usage
            val usageView = TextView(context).apply {
                text = context.formattedTimeSpent(usageMs)
                textSize = 13f
                setTextColor(secondaryTextColor)
                setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
            }
            row.addView(usageView)

            // Limit selector (clickable text that opens a popup menu)
            val currentLimit = currentLimits[packageName]
            val limitLabel = when {
                currentLimit == null -> "No limit"
                currentLimit < 0 -> "Unlimited"
                currentLimit >= 60 -> "${currentLimit / 60}h"
                else -> "${currentLimit}m"
            }

            val limitView = TextView(context).apply {
                text = limitLabel
                textSize = 14f
                setTextColor(textColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                gravity = Gravity.CENTER
                minWidth = 72.dpToPx()
            }

            limitView.setOnClickListener { anchor ->
                showLimitPopup(anchor, packageName, limitView)
            }

            row.addView(limitView)
            listLayout.addView(row)
        }

        scrollView.addView(listLayout)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
    }

    private fun showLimitPopup(anchor: View, packageName: String, limitView: TextView) {
        val popup = PopupMenu(context, anchor)
        limitOptions.forEachIndexed { index, option ->
            popup.menu.add(0, index, index, option.label)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val selected = limitOptions[menuItem.itemId]
            if (selected.minutes < 0) {
                // "Unlimited" means remove the limit
                AppLimitManager.removeLimit(context, packageName)
                limitView.text = "Unlimited"
            } else {
                AppLimitManager.setLimit(context, packageName, selected.minutes)
                limitView.text = selected.label
            }
            true
        }

        popup.show()
    }
}
