package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import app.olauncher.R
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr

/**
 * Custom View that draws 7 vertical bars representing daily screen time.
 * Each bar is labeled with a day abbreviation below and hours text above.
 */
class ScreenTimeGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Pair<String, Long>> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(R.attr.primaryColor)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(R.attr.primaryColor)
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(R.attr.primaryColor)
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val barCornerRadius = 4.dpToPx().toFloat()
    private val intrinsicHeightPx = 200.dpToPx()

    /**
     * Set the data to display. Each pair is (dayLabel, milliseconds).
     * Expects up to 7 entries; oldest on the left.
     */
    fun setData(data: List<Pair<String, Long>>) {
        this.data = data
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val desiredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> intrinsicHeightPx.coerceAtMost(heightSize)
            else -> intrinsicHeightPx
        }
        setMeasuredDimension(width, desiredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val barCount = data.size
        if (barCount == 0) return

        val topPadding = 24.dpToPx().toFloat()    // space for hour labels above bars
        val bottomPadding = 20.dpToPx().toFloat()  // space for day labels below bars
        val sidePadding = 12.dpToPx().toFloat()

        val availableWidth = width.toFloat() - sidePadding * 2
        val availableHeight = height.toFloat() - topPadding - bottomPadding

        val slotWidth = availableWidth / barCount
        val barWidth = (slotWidth * 0.55f).coerceAtMost(40.dpToPx().toFloat())

        val maxValue = data.maxOf { it.second }.coerceAtLeast(1L)

        val rect = RectF()

        for (i in data.indices) {
            val (label, millis) = data[i]
            val centerX = sidePadding + slotWidth * i + slotWidth / 2f

            // Bar height proportional to max value
            val barHeight = if (maxValue > 0) {
                (millis.toFloat() / maxValue.toFloat()) * availableHeight
            } else {
                0f
            }

            // Draw bar (from bottom up)
            val barTop = topPadding + availableHeight - barHeight
            val barBottom = topPadding + availableHeight
            val barLeft = centerX - barWidth / 2f
            val barRight = centerX + barWidth / 2f

            if (barHeight > 0f) {
                rect.set(barLeft, barTop, barRight, barBottom)
                canvas.drawRoundRect(rect, barCornerRadius, barCornerRadius, barPaint)
            }

            // Draw hours text above bar
            val hours = millis / 3_600_000.0
            val hoursText = if (hours >= 1.0) {
                String.format("%.1fh", hours)
            } else {
                val mins = millis / 60_000
                "${mins}m"
            }
            canvas.drawText(
                hoursText,
                centerX,
                barTop - 4.dpToPx().toFloat(),
                textPaint
            )

            // Draw day label below bar
            canvas.drawText(
                label,
                centerX,
                height.toFloat() - 2.dpToPx().toFloat(),
                labelPaint
            )
        }
    }
}
