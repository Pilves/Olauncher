package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import app.olauncher.R
import app.olauncher.helper.FocusModeManager
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Bottom sheet dialog for enabling/disabling Focus Mode.
 *
 * When focus mode is active, shows current status with remaining time and a disable button.
 * When inactive, shows duration options, app whitelist checkboxes, and an enable button.
 *
 * @param context The activity context.
 * @param topApps List of (packageName, appLabel) pairs for whitelist selection. Up to 10 shown.
 */
class FocusModeDialog(
    context: Context,
    private val topApps: List<Pair<String, String>> = emptyList()
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_focus_mode)

        val container = findViewById<LinearLayout>(R.id.focusModeContainer) ?: return
        val ctx = container.context

        val primaryColor = ctx.getColorFromAttr(R.attr.primaryColor)
        val inversePrimaryColor = ctx.getColorFromAttr(R.attr.primaryInverseColor)

        if (FocusModeManager.isActive(ctx)) {
            buildActiveView(container, ctx, primaryColor)
        } else {
            buildInactiveView(container, ctx, primaryColor, inversePrimaryColor)
        }
    }

    /**
     * Builds the UI shown when focus mode is currently active:
     * status text, remaining time, and a disable button.
     */
    private fun buildActiveView(
        container: LinearLayout,
        ctx: Context,
        primaryColor: Int
    ) {
        // Title
        container.addView(createTitle(ctx, "Focus Mode", primaryColor))

        // Status text
        val statusText = TextView(ctx).apply {
            text = "Focus mode is ON"
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
        container.addView(statusText)

        // Remaining time
        val remaining = FocusModeManager.getRemainingTimeFormatted(ctx)
        if (remaining != null) {
            val timeText = TextView(ctx).apply {
                text = "Time remaining: $remaining"
                setTextColor(primaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, 0, 16.dpToPx())
            }
            container.addView(timeText)
        } else {
            val timeText = TextView(ctx).apply {
                text = "Until manually disabled"
                setTextColor(primaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, 0, 16.dpToPx())
            }
            container.addView(timeText)
        }

        // Disable button
        val disableButton = Button(ctx).apply {
            text = "Disable"
            setTextColor(primaryColor)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dpToPx()
            }
            setOnClickListener {
                FocusModeManager.disable(ctx)
                dismiss()
            }
        }
        container.addView(disableButton)
    }

    /**
     * Builds the UI shown when focus mode is inactive:
     * duration radio group, app whitelist checkboxes, and an enable button.
     */
    private fun buildInactiveView(
        container: LinearLayout,
        ctx: Context,
        primaryColor: Int,
        inversePrimaryColor: Int
    ) {
        // Title
        container.addView(createTitle(ctx, "Focus Mode", primaryColor))

        // Duration section label
        val durationLabel = TextView(ctx).apply {
            text = "Duration"
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
        }
        container.addView(durationLabel)

        // Radio group for timer options
        val radioGroup = RadioGroup(ctx).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 0, 0, 12.dpToPx())
        }

        data class DurationOption(val label: String, val minutes: Int)

        val options = listOf(
            DurationOption("25 minutes", 25),
            DurationOption("1 hour", 60),
            DurationOption("2 hours", 120),
            DurationOption("Until I disable", -1)
        )

        options.forEachIndexed { index, option ->
            val radioButton = RadioButton(ctx).apply {
                id = index + 1
                text = option.label
                setTextColor(primaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(8.dpToPx(), 4.dpToPx(), 0, 4.dpToPx())
            }
            radioGroup.addView(radioButton)
        }
        radioGroup.check(1) // Default: 25 minutes
        container.addView(radioGroup)

        // Allowed apps section
        val whitelistCheckboxes = mutableListOf<Pair<CheckBox, String>>()

        if (topApps.isNotEmpty()) {
            val appsLabel = TextView(ctx).apply {
                text = "Allowed apps (max 5)"
                setTextColor(primaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
            }
            container.addView(appsLabel)

            val currentWhitelist = FocusModeManager.getWhitelist(ctx)
            val displayApps = topApps.take(10)

            displayApps.forEach { (packageName, appLabel) ->
                val checkBox = CheckBox(ctx).apply {
                    text = appLabel
                    setTextColor(primaryColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    isChecked = currentWhitelist.contains(packageName)
                    setPadding(8.dpToPx(), 2.dpToPx(), 0, 2.dpToPx())
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            val checkedCount = whitelistCheckboxes.count { it.first.isChecked }
                            if (checkedCount > 5) {
                                this.isChecked = false
                            }
                        }
                    }
                }
                whitelistCheckboxes.add(checkBox to packageName)
                container.addView(checkBox)
            }
        }

        // Enable button
        val enableButton = Button(ctx).apply {
            text = "Enable"
            setTextColor(primaryColor)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dpToPx()
            }
            setOnClickListener {
                val selectedId = radioGroup.checkedRadioButtonId
                val durationMinutes = if (selectedId in 1..options.size) {
                    options[selectedId - 1].minutes
                } else {
                    25
                }

                val selectedPackages = whitelistCheckboxes
                    .filter { it.first.isChecked }
                    .map { it.second }
                    .toSet()

                FocusModeManager.setWhitelist(ctx, selectedPackages)
                FocusModeManager.enable(ctx, durationMinutes)
                dismiss()
            }
        }
        container.addView(enableButton)
    }

    private fun createTitle(ctx: Context, text: String, color: Int): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
            setPadding(0, 0, 0, 8.dpToPx())
        }
    }
}
