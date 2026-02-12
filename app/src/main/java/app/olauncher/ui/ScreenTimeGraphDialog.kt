package app.olauncher.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import app.olauncher.R
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.usageStats.EventLogWrapper
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.DialogInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A BottomSheetDialog that displays a 7-day screen time graph.
 * Fetches usage data via EventLogWrapper on a background thread.
 */
class ScreenTimeGraphDialog(context: Context) : BottomSheetDialog(context) {

    private var loadJob: Job? = null
    private lateinit var graphView: ScreenTimeGraphView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bgColor = context.getColorFromAttr(R.attr.primaryInverseColor)
        val textColor = context.getColorFromAttr(R.attr.primaryColor)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            setBackgroundColor(bgColor)
        }

        val titleView = TextView(context).apply {
            text = "Screen Time - Last 7 Days"
            textSize = 18f
            setTextColor(textColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
            setPadding(0, 0, 0, 16.dpToPx())
        }
        container.addView(titleView)

        graphView = ScreenTimeGraphView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200.dpToPx()
            )
        }
        container.addView(graphView)

        setContentView(container)

        loadData()
    }

    private fun loadData() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!context.appUsagePermissionGranted()) return

        val appContext = context.applicationContext
        loadJob = CoroutineScope(Dispatchers.Main + Job()).launch {
            val data = withContext(Dispatchers.IO) {
                val wrapper = EventLogWrapper(appContext)
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val calendar = Calendar.getInstance()
                val now = System.currentTimeMillis()

                val result = mutableListOf<Pair<String, Long>>()

                for (offset in 6 downTo 0) {
                    val foregroundStats = wrapper.getForegroundStatsByRelativeDay(offset)
                    val aggregated = wrapper.aggregateForegroundStats(foregroundStats)
                    val totalMs = wrapper.aggregateSimpleUsageStats(aggregated)

                    // Get the day abbreviation
                    calendar.timeInMillis = now
                    calendar.add(Calendar.DAY_OF_YEAR, -offset)
                    val dayLabel = dayFormat.format(calendar.time)

                    result.add(Pair(dayLabel, totalMs))
                }

                result
            }

            graphView.setData(data)
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()
        super.onDestroy()
    }

    override fun onStop() {
        loadJob?.cancel()
        super.onStop()
    }

    override fun dismiss() {
        loadJob?.cancel()
        super.dismiss()
    }
}
