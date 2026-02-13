package app.olauncher.ui

import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.MainActivity
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.FolderApp
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getChangedAppTheme
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.openSearch
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.helper.showToast
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import app.olauncher.helper.DoubleTapActionManager
import app.olauncher.helper.FocusModeManager
import app.olauncher.helper.FolderManager
import app.olauncher.helper.GestureLetterManager
import app.olauncher.helper.IconPackManager
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.AppLimitManager
import app.olauncher.helper.GrayscaleManager
import app.olauncher.helper.QuickNoteManager
import app.olauncher.helper.UsageStatsHelper
import app.olauncher.helper.SwipeUpAppManager
import app.olauncher.helper.WeatherManager
import app.olauncher.ui.FocusModeDialog
import app.olauncher.ui.ScreenTimeGraphDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var screenTouchListener: OnSwipeTouchListener? = null
    private val viewTouchListeners = mutableListOf<ViewSwipeTouchListener>()
    private lateinit var folderManager: FolderManager
    private var expandedFolderSlot: Int = -1
    private var effectiveNoteSlot: Int = -1

    private val widgetGestureDetectors = mutableListOf<android.view.GestureDetector>()

    private val mainActivityOrNull: MainActivity?
        get() = activity as? MainActivity

    companion object {
        private var cachedLocale: Locale? = null
        private var dateFormatter: DateTimeFormatter? = null
        fun getDateFormatter(): DateTimeFormatter {
            val current = Locale.getDefault()
            if (current != cachedLocale || dateFormatter == null) {
                cachedLocale = current
                dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", current)
            }
            return dateFormatter!!
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        folderManager = FolderManager(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
        initGestureLetterOverlay()
        viewLifecycleOwner.lifecycleScope.launch {
            restoreWidget()
        }

        if (!prefs.onboardingComplete) {
            prefs.onboardingComplete = true
            findNavController().navigate(R.id.action_mainFragment_to_onboardingFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // Cancel any pending long-press timers from before the app was backgrounded
        if (widgetGestureDetectors.isNotEmpty()) {
            val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            widgetGestureDetectors.forEach { it.onTouchEvent(cancel) }
            cancel.recycle()
        }
        populateHomeScreen(false)
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        FocusModeManager.checkAndExpire(requireContext())
        viewModel.getWeather()
        updateGestureLetterOverlay()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Failed to launch app", e)
                }
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        val homeAppIds = mapOf(
            R.id.homeApp1 to 1, R.id.homeApp2 to 2, R.id.homeApp3 to 3, R.id.homeApp4 to 4,
            R.id.homeApp5 to 5, R.id.homeApp6 to 6, R.id.homeApp7 to 7, R.id.homeApp8 to 8
        )
        val slot = homeAppIds[view.id]
        if (slot != null) {
            showHomeSlotMenu(slot)
            return true
        }
        when (view.id) {
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isOlauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
            if (getActiveContainer()?.childCount == 0 && prefs.getWidgetIdList().isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch { restoreWidget() }
            }
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it != true) {
                if (prefs.dailyWallpaper) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        viewModel.weatherValue.observe(viewLifecycleOwner) {
            populateDateTime()
        }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        viewTouchListeners.forEach { it.cleanup() }
        viewTouchListeners.clear()
        screenTouchListener = getSwipeGestureListener(context)
        binding.mainLayout.setOnTouchListener(screenTouchListener)
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        binding.homeApp1.gravity = horizontalGravity
        binding.homeApp2.gravity = horizontalGravity
        binding.homeApp3.gravity = horizontalGravity
        binding.homeApp4.gravity = horizontalGravity
        binding.homeApp5.gravity = horizontalGravity
        binding.homeApp6.gravity = horizontalGravity
        binding.homeApp7.gravity = horizontalGravity
        binding.homeApp8.gravity = horizontalGravity
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        val weatherTemp = WeatherManager.getDisplayString(requireContext())
        var dateText = LocalDate.now().format(getDateFormatter())
        if (weatherTemp.isNotEmpty()) dateText = "$weatherTemp  $dateText"

        if (!prefs.showStatusBar) {
            val ctx = requireContext()
            val batteryIntent = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val battery = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        val displayDate = dateText.replace(".,", ",")
        binding.date.text = displayDate
        binding.date.contentDescription = displayDate
        binding.clock.contentDescription = binding.clock.text
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppViews = listOf(
            binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
            binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8
        )
        val homeAppsNum = prefs.homeAppsNum.coerceAtMost(homeAppViews.size)

        val noteEnabled = QuickNoteManager.isEnabled(requireContext())
        effectiveNoteSlot = if (noteEnabled) homeAppsNum else -1

        for (i in 0 until homeAppsNum) {
            val slot = i + 1
            val view = homeAppViews[i]
            view.visibility = View.VISIBLE

            if (noteEnabled && slot == homeAppsNum) {
                // Quick Note always pins to the last visible slot
                view.text = QuickNoteManager.getPreviewText(requireContext())
                view.contentDescription = getString(R.string.quick_note)
                view.setCompoundDrawablesRelative(null, null, null, null)
            } else if (folderManager.isFolderSlot(slot)) {
                val folder = folderManager.getFolderGroup(slot)
                view.text = folder?.name ?: ""
                view.contentDescription = folder?.name ?: getString(R.string.long_press_to_select_app)
            } else {
                val appName = prefs.getHomeAppName(slot)
                if (!setHomeAppText(view, appName, prefs.getHomeAppPackage(slot), prefs.getHomeAppUser(slot))) {
                    prefs.setHomeAppName(slot, "")
                    prefs.setHomeAppPackage(slot, "")
                }
                view.contentDescription = appName.ifEmpty { getString(R.string.long_press_to_select_app) }
            }
        }

        // Dynamic fitting: adjust padding to clear date/time, then hide overflow apps
        if (homeAppsNum > 0) {
            binding.homeAppsLayout.post {
                if (!isAdded || _binding == null) return@post
                val layout = binding.homeAppsLayout

                // Adjust top padding to clear the dateTimeLayout
                val dtLayout = binding.dateTimeLayout
                val minTopPadding = if (dtLayout.isVisible) {
                    (dtLayout.top + dtLayout.height + (8 * resources.displayMetrics.density).toInt())
                } else {
                    (56 * resources.displayMetrics.density).toInt()
                }
                if (layout.paddingTop != minTopPadding) {
                    layout.setPadding(layout.paddingLeft, minTopPadding, layout.paddingRight, layout.paddingBottom)
                }

                // Run fitting after the padding change has been laid out
                layout.post fitApps@{
                    if (!isAdded || _binding == null) return@fitApps

                    val availableHeight = layout.height - layout.paddingTop - layout.paddingBottom
                    if (availableHeight <= 0) return@fitApps

                    // Subtract widget container height
                    val widgetHeight = when {
                        prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE ->
                            binding.widgetScrollViewAbove?.let { if (it.isVisible) it.height else 0 } ?: 0
                        else ->
                            binding.widgetScrollViewBelow?.let { if (it.isVisible) it.height else 0 } ?: 0
                    }

                    val usableHeight = availableHeight - widgetHeight
                    if (usableHeight <= 0) return@fitApps

                    // Measure height of a single app view
                    val firstVisible = homeAppViews.firstOrNull { it.isVisible }
                    val singleAppHeight = firstVisible?.height ?: return@fitApps
                    if (singleAppHeight <= 0) return@fitApps

                    val maxFitting = usableHeight / singleAppHeight
                    if (maxFitting < homeAppsNum) {
                        // Hide excess apps from the bottom, but keep Quick Note at last visible slot
                        for (j in homeAppViews.indices.reversed()) {
                            if (j >= maxFitting && homeAppViews[j].isVisible) {
                                homeAppViews[j].visibility = View.GONE
                            }
                        }
                        // If note is enabled, ensure it shows at the last visible slot
                        if (noteEnabled && maxFitting > 0) {
                            val ctx = context ?: return@fitApps
                            val noteView = homeAppViews[maxFitting - 1]
                            noteView.visibility = View.VISIBLE
                            noteView.text = QuickNoteManager.getPreviewText(ctx)
                            noteView.contentDescription = getString(R.string.quick_note)
                            noteView.setCompoundDrawablesRelative(null, null, null, null)
                            effectiveNoteSlot = maxFitting
                        }
                    }
                }
            }
        }
    }

    private fun setHomeAppText(textView: TextView, appName: String, packageName: String, userString: String): Boolean {
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            if (prefs.showIcons && packageName.isNotEmpty()) {
                val iconSize = 20.dpToPx()
                val icon = if (prefs.iconPackPackage.isNotEmpty()) {
                    IconPackManager.getIconForApp(requireContext(), prefs.iconPackPackage, packageName, null)
                } else null
                val drawable = icon ?: try {
                    requireContext().packageManager.getApplicationIcon(packageName)
                } catch (_: Exception) { null }
                drawable?.setBounds(0, 0, iconSize, iconSize)
                textView.setCompoundDrawablesRelative(drawable, null, null, null)
                textView.compoundDrawablePadding = 8.dpToPx()
            } else {
                textView.setCompoundDrawablesRelative(null, null, null, null)
            }
            return true
        }
        textView.text = ""
        textView.setCompoundDrawablesRelative(null, null, null, null)
        return false
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
    }

    private fun homeAppClicked(location: Int) {
        if (folderManager.isFolderSlot(location)) {
            toggleFolderExpansion(location)
            return
        }
        if (effectiveNoteSlot > 0 && location == effectiveNoteSlot) {
            showQuickNoteDialog()
            return
        }
        if (prefs.getAppName(location).isEmpty()) showLongPressToast()
        else checkBadHabitAndLaunch(
            prefs.getAppName(location),
            prefs.getAppPackage(location),
            prefs.getAppActivityClassName(location),
            prefs.getAppUser(location)
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel(
                appName,
                null,
                packageName,
                activityClassName,
                false,
                getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun checkBadHabitAndLaunch(name: String, pkg: String, activity: String?, user: String) {
        val ctx = context ?: return
        if (!AppLimitManager.hasLimit(ctx, pkg)) {
            launchApp(name, pkg, activity, user)
            return
        }
        val limitMinutes = AppLimitManager.getLimit(ctx, pkg) ?: run {
            launchApp(name, pkg, activity, user)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val usageMs = withContext(Dispatchers.IO) {
                UsageStatsHelper.getUsageForApp(ctx, pkg)
            }
            if (!isAdded) return@launch
            val usageMinutes = usageMs / 60_000
            if (usageMinutes >= limitMinutes) {
                showBadHabitWarningDialog(name, pkg, activity, user, usageMinutes, limitMinutes)
            } else {
                launchApp(name, pkg, activity, user)
            }
        }
    }

    private fun showBadHabitWarningDialog(
        name: String, pkg: String, activity: String?, user: String,
        usageMinutes: Long, limitMinutes: Int
    ) {
        val ctx = context ?: return
        val appName = name.ifEmpty { pkg }
        val hours = usageMinutes / 60
        val mins = usageMinutes % 60
        val usageText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        val limitText = if (limitMinutes >= 60) "${limitMinutes / 60}h ${limitMinutes % 60}m" else "${limitMinutes}m"

        val dialog = BottomSheetDialog(ctx)
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(ctx.getColorFromAttr(R.attr.primaryInverseColor))
            setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
        }

        val handle = View(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(ctx.getColorFromAttr(R.attr.primaryColorTrans50))
        }
        container.addView(handle)

        val message = TextView(ctx).apply {
            text = ctx.getString(R.string.app_limit_warning, appName, usageText, limitText)
            textSize = 16f
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 16.dpToPx())
        }
        container.addView(message)

        val openAnyway = TextView(ctx).apply {
            text = ctx.getString(R.string.open_anyway)
            textSize = 16f
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            setOnClickListener {
                dialog.dismiss()
                launchApp(name, pkg, activity, user)
            }
        }
        container.addView(openAnyway)

        val goBack = TextView(ctx).apply {
            text = ctx.getString(R.string.go_back)
            textSize = 16f
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColorTrans50))
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            setOnClickListener { dialog.dismiss() }
        }
        container.addView(goBack)

        dialog.setContentView(container)
        dialog.show()
    }

    private fun showBadHabitTimePicker(pkg: String) {
        val ctx = context ?: return
        val dialog = BottomSheetDialog(ctx)
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(ctx.getColorFromAttr(R.attr.primaryInverseColor))
            setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
        }

        val handle = View(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(ctx.getColorFromAttr(R.attr.primaryColorTrans50))
        }
        container.addView(handle)

        val title = TextView(ctx).apply {
            text = ctx.getString(R.string.select_time_limit)
            textSize = 16f
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 12.dpToPx())
        }
        container.addView(title)

        val options = listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 120 to "2 hours")
        for ((minutes, label) in options) {
            val tv = TextView(ctx).apply {
                text = label
                textSize = 16f
                setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
                setOnClickListener {
                    AppLimitManager.setLimit(ctx, pkg, minutes)
                    dialog.dismiss()
                    populateHomeScreen(false)
                }
            }
            container.addView(tv)
        }

        dialog.setContentView(container)
        dialog.show()
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            Log.e("HomeFragment", "Navigation to app list failed, using fallback", e)
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> requireContext().openSearch()
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        executeGestureAction(prefs.getEffectiveSwipeRightAction()) {
            // Fallback for OPEN_APP action
            if (prefs.appPackageSwipeRight.isNotEmpty())
                launchApp(
                    prefs.appNameSwipeRight,
                    prefs.appPackageSwipeRight,
                    prefs.appActivityClassNameRight,
                    prefs.appUserSwipeRight
                )
            else openDialerApp(requireContext())
        }
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        executeGestureAction(prefs.getEffectiveSwipeLeftAction()) {
            // Fallback for OPEN_APP action
            if (prefs.appPackageSwipeLeft.isNotEmpty())
                launchApp(
                    prefs.appNameSwipeLeft,
                    prefs.appPackageSwipeLeft,
                    prefs.appActivityClassNameSwipeLeft,
                    prefs.appUserSwipeLeft
                )
            else openCameraApp(requireContext())
        }
    }

    private fun executeGestureAction(action: Int, openAppFallback: () -> Unit) {
        when (action) {
            Constants.GestureAction.OPEN_APP -> openAppFallback()
            Constants.GestureAction.OPEN_NOTIFICATIONS -> expandNotificationDrawer(requireContext())
            Constants.GestureAction.OPEN_SEARCH -> requireContext().openSearch()
            Constants.GestureAction.LOCK_SCREEN -> lockPhone()
            Constants.GestureAction.OPEN_CAMERA -> openCameraApp(requireContext())
            Constants.GestureAction.TOGGLE_FLASHLIGHT -> toggleFlashlight()
            Constants.GestureAction.NONE -> { /* do nothing */ }
        }
    }

    private fun toggleFlashlight() {
        try {
            val cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            flashlightOn = !flashlightOn
            cameraManager.setTorchMode(cameraId, flashlightOn)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to toggle flashlight", e)
        }
    }

    private fun lockPhone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isAccessServiceEnabled(requireContext())) {
                binding.lock.performClick()
            } else {
                deviceManager.lockNow()
            }
        } catch (e: SecurityException) {
            prefs.lockModeOn = false
            requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
        } catch (e: Exception) {
            requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun changeAppTheme() {
        if (prefs.dailyWallpaper.not()) return
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.dailyWallpaper) {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun openScreenTimeDigitalWellbeing() {
        try {
            ScreenTimeGraphDialog(requireContext()).show()
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to show screen time graph", e)
            // Fallback to Digital Wellbeing
            val intent = Intent()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_ACTIVITY
                )
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e("HomeFragment", "Failed to open Digital Wellbeing", e2)
            }
        }
    }

    private fun toggleFolderExpansion(slot: Int) {
        val folder = folderManager.getFolderGroup(slot) ?: return
        if (folder.apps.isEmpty()) return

        val dialog = BottomSheetDialog(requireContext())
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(requireContext().getColorFromAttr(R.attr.primaryInverseColor))
            setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
        }

        // Drag handle
        val handle = View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 8.dpToPx()
            }
            setBackgroundColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        }
        container.addView(handle)

        // Folder title
        val title = TextView(requireContext()).apply {
            text = folder.name
            textSize = 14f
            setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }
        container.addView(title)

        for (app in folder.apps) {
            val tv = TextView(requireContext()).apply {
                text = app.appName
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
                setOnClickListener {
                    dialog.dismiss()
                    launchApp(app.appName, app.packageName, app.activityClassName, app.userString)
                }
            }
            container.addView(tv)
        }

        dialog.setContentView(container)
        dialog.show()
    }

    private fun showHomeSlotMenu(slot: Int) {
        val hasApp = prefs.getHomeAppName(slot).isNotEmpty()
        val isFolder = folderManager.isFolderSlot(slot)
        val isNote = effectiveNoteSlot > 0 && slot == effectiveNoteSlot

        val dialog = BottomSheetDialog(requireContext())
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(requireContext().getColorFromAttr(R.attr.primaryInverseColor))
            setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
        }

        val handle = View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        }
        container.addView(handle)

        fun addOption(text: String, onClick: () -> Unit) {
            val tv = TextView(requireContext()).apply {
                this.text = text
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
                setOnClickListener { dialog.dismiss(); onClick() }
            }
            container.addView(tv)
        }

        // Set / change app
        addOption(if (hasApp) getString(R.string.rename) else getString(R.string.open_app)) {
            showAppList(slot, hasApp, true)
        }

        // Create folder
        addOption(getString(R.string.create_folder)) {
            showCreateFolderDialog(slot)
        }

        // App limit toggle (only for regular apps, not folders or notes)
        if (hasApp && !isFolder && !isNote) {
            val pkg = prefs.getHomeAppPackage(slot)
            if (pkg.isNotEmpty()) {
                val hasLimit = AppLimitManager.hasLimit(requireContext(), pkg)
                addOption(if (hasLimit) getString(R.string.remove_time_limit) else getString(R.string.set_time_limit)) {
                    if (hasLimit) {
                        AppLimitManager.removeLimit(requireContext(), pkg)
                    } else {
                        showBadHabitTimePicker(pkg)
                    }
                }
            }
        }

        // Remove (if occupied)
        if (hasApp || isFolder || isNote) {
            addOption(getString(R.string.delete)) {
                if (isFolder) folderManager.removeFolder(slot)
                if (isNote) QuickNoteManager.setEnabled(requireContext(), false)
                prefs.setHomeAppName(slot, "")
                prefs.setHomeAppPackage(slot, "")
                prefs.setHomeAppActivityClassName(slot, "")
                prefs.setHomeAppUser(slot, "")
                populateHomeScreen(false)
            }
        }

        dialog.setContentView(container)
        dialog.show()
    }

    private fun showCreateFolderDialog(slot: Int) {
        val pm = requireContext().packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val resolveInfos = withContext(Dispatchers.IO) {
                pm.queryIntentActivities(intent, 0)
                    .sortedBy { it.loadLabel(pm).toString().lowercase() }
                    .take(50)
            }

            if (!isAdded || _binding == null) return@launch

            val dialog = BottomSheetDialog(requireContext())
            val container = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(requireContext().getColorFromAttr(R.attr.primaryInverseColor))
                setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 24.dpToPx())
            }

            // Title
            val title = TextView(requireContext()).apply {
                text = getString(R.string.create_folder)
                textSize = 18f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 12.dpToPx())
            }
            container.addView(title)

            // Folder name input
            val nameLabel = TextView(requireContext()).apply {
                text = getString(R.string.folder_name)
                textSize = 14f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            }
            container.addView(nameLabel)

            val nameInput = android.widget.EditText(requireContext()).apply {
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setHintTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
                hint = "Social, Work, etc."
                background = null
                setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
            }
            container.addView(nameInput)

            // App selection label
            val appsLabel = TextView(requireContext()).apply {
                text = getString(R.string.select_apps)
                textSize = 14f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
                setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
            }
            container.addView(appsLabel)

            val selectedApps = mutableListOf<FolderApp>()
            val checkboxes = mutableListOf<android.widget.CheckBox>()

            val scrollView = android.widget.ScrollView(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 300.dpToPx()
                )
            }
            val appsContainer = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }

            for (ri in resolveInfos) {
                val appLabel = ri.loadLabel(pm).toString()
                val pkg = ri.activityInfo.packageName
                val activity = ri.activityInfo.name
                val cb = android.widget.CheckBox(requireContext()).apply {
                    text = appLabel
                    textSize = 14f
                    setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                    setPadding(8.dpToPx(), 2.dpToPx(), 0, 2.dpToPx())
                    setOnCheckedChangeListener { _, isChecked ->
                        val app = FolderApp(appLabel, pkg, activity, android.os.Process.myUserHandle().toString())
                        if (isChecked) {
                            if (selectedApps.size < 4) selectedApps.add(app)
                            else this.isChecked = false
                        } else {
                            selectedApps.removeAll { it.packageName == pkg }
                        }
                    }
                }
                checkboxes.add(cb)
                appsContainer.addView(cb)
            }
            scrollView.addView(appsContainer)
            container.addView(scrollView)

            // Save button
            val saveButton = TextView(requireContext()).apply {
                text = getString(R.string.save)
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 16.dpToPx(), 0, 0)
                setOnClickListener {
                    val folderName = nameInput.text.toString().trim()
                    if (folderName.isEmpty() || selectedApps.isEmpty()) {
                        requireContext().showToast("Enter a name and select at least one app")
                        return@setOnClickListener
                    }
                    // Clear any existing app at this slot
                    prefs.setHomeAppName(slot, "")
                    prefs.setHomeAppPackage(slot, "")
                    folderManager.createFolder(slot, folderName, selectedApps)
                    populateHomeScreen(false)
                    dialog.dismiss()
                }
            }
            container.addView(saveButton)

            dialog.setContentView(container)
            dialog.show()
        }
    }

    private fun showQuickNoteDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_quick_note, null)
        val editText = view.findViewById<android.widget.EditText>(R.id.noteEditText)
        val saveButton = view.findViewById<TextView>(R.id.noteSaveButton)
        val clearButton = view.findViewById<TextView>(R.id.noteClearButton)
        editText.setText(QuickNoteManager.getText(requireContext()))
        saveButton.setOnClickListener {
            QuickNoteManager.setText(requireContext(), editText.text.toString())
            populateHomeScreen(false)
            dialog.dismiss()
        }
        clearButton?.setOnClickListener {
            QuickNoteManager.setText(requireContext(), "")
            editText.setText("")
            populateHomeScreen(false)
        }
        dialog.setContentView(view)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun initGestureLetterOverlay() {
        binding.gestureLetterOverlay?.onLetterDetected = { letter ->
            val mapping = GestureLetterManager.getMapping(requireContext(), letter)
            if (mapping != null) {
                launchApp("", mapping.first, mapping.second, mapping.third)
            } else {
                requireContext().showToast(getString(R.string.no_app_for_letter, letter.toString()))
            }
        }
    }

    private fun updateGestureLetterOverlay() {
        val enabled = GestureLetterManager.isEnabled(requireContext())
        binding.gestureLetterOverlay?.isGestureLettersEnabled = enabled
        binding.gestureLetterOverlay?.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): OnSwipeTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                binding.gestureLetterOverlay?.forwardTouchEvent(motionEvent)
                return super.onTouch(view, motionEvent)
            }

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    showHomeLongPressMenu()
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Failed to show home long press menu", e)
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                DoubleTapActionManager.execute(
                    requireContext(),
                    lockPhone = { lockPhone() },
                    toggleFlashlight = { toggleFlashlight() }
                )
            }

            override fun onClick() {
                super.onClick()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): ViewSwipeTouchListener {
        val listener = object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                val slot = try { view.tag.toString().toInt() } catch (_: Exception) { 0 }
                if (slot in 1..8 && SwipeUpAppManager.hasSwipeUpApp(requireContext(), slot)) {
                    launchApp(
                        SwipeUpAppManager.getSwipeUpAppName(requireContext(), slot),
                        SwipeUpAppManager.getSwipeUpAppPackage(requireContext(), slot),
                        SwipeUpAppManager.getSwipeUpAppActivity(requireContext(), slot),
                        SwipeUpAppManager.getSwipeUpAppUser(requireContext(), slot)
                    )
                } else {
                    showAppList(Constants.FLAG_LAUNCH_APP)
                }
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
        viewTouchListeners.add(listener)
        return listener
    }

    private fun showHomeLongPressMenu() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_home_menu, null)
        view.findViewById<TextView>(R.id.menuAddWidget).setOnClickListener {
            dialog.dismiss()
            showWidgetPicker()
        }
        view.findViewById<TextView>(R.id.menuQuickNote).setOnClickListener {
            dialog.dismiss()
            QuickNoteManager.setEnabled(requireContext(), true)
            showQuickNoteDialog()
        }
        view.findViewById<TextView>(R.id.menuSettings).setOnClickListener {
            dialog.dismiss()
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showFocusModeDialog() {
        val topApps = viewModel.perAppScreenTime.value?.entries
            ?.sortedByDescending { it.value }
            ?.take(10)
            ?.map { it.key to (requireContext().packageManager.let { pm ->
                try { pm.getApplicationLabel(pm.getApplicationInfo(it.key, 0)).toString() } catch (_: Exception) { it.key }
            }) } ?: emptyList()
        FocusModeDialog(requireContext(), topApps).show()
    }

    private fun toggleGrayscale() {
        if (!GrayscaleManager.toggle(requireContext())) {
            requireContext().showToast(getString(R.string.grayscale_open_settings))
            try {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) { }
        }
    }

    // ─── Multi-widget system ───

    private var flashlightOn: Boolean = false

    private fun getActiveContainer(): android.widget.LinearLayout? {
        return if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE)
            binding.widgetContainerAbove
        else
            binding.widgetContainerBelow
    }

    private fun getActiveScrollView(): android.widget.ScrollView? {
        return if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE)
            binding.widgetScrollViewAbove
        else
            binding.widgetScrollViewBelow
    }

    // Queue of (oldWidgetId, providerComponentString) for widgets that need rebinding with permission
    private val widgetRestoreQueue = mutableListOf<Pair<Int, String>>()
    private var isRestoringWidgets = false

    private suspend fun restoreWidget() {
        if (isRestoringWidgets) return
        isRestoringWidgets = true
        try {
            val mainActivity = mainActivityOrNull ?: return
            prefs.migrateWidgetIfNeeded(mainActivity.appWidgetManager)
            val ids = prefs.getWidgetIdList()
            if (ids.isEmpty()) return
            val savedProviders = prefs.getAllWidgetProviders()
            widgetRestoreQueue.clear()

            // Clear existing widget views to prevent duplicates
            getActiveContainer()?.removeAllViews()

            // Gather widget info on background thread
            data class WidgetRestoreInfo(val wid: Int, val info: AppWidgetProviderInfo?, val providerStr: String?)
            val restoreInfoList = withContext(Dispatchers.IO) {
                ids.map { wid ->
                    val info = try {
                        mainActivity.appWidgetManager.getAppWidgetInfo(wid)
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "restoreWidget: failed to get info for id=$wid", e)
                        null
                    }
                    WidgetRestoreInfo(wid, info, savedProviders[wid])
                }
            }

            // Process results on main thread
            val validIds = mutableListOf<Int>()
            for (restoreInfo in restoreInfoList) {
                try {
                    if (restoreInfo.info != null) {
                        // Widget still valid
                        val hostView = mainActivity.appWidgetHost.createView(
                            requireContext().applicationContext, restoreInfo.wid, restoreInfo.info
                        )
                        addWidgetToContainer(hostView, restoreInfo.wid)
                        validIds.add(restoreInfo.wid)
                    } else {
                        // Widget invalidated — queue for rebind if we know the provider
                        mainActivity.appWidgetHost.deleteAppWidgetId(restoreInfo.wid)
                        if (restoreInfo.providerStr != null) {
                            widgetRestoreQueue.add(restoreInfo.wid to restoreInfo.providerStr)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeFragment", "restoreWidget failed for id=${restoreInfo.wid}", e)
                }
            }
            prefs.setWidgetIdList(validIds)
            updateWidgetContainerVisibility()

            // Process queued widgets that need permission-based rebinding
            if (widgetRestoreQueue.isNotEmpty()) {
                processNextWidgetRestore()
            }
        } finally {
            isRestoringWidgets = false
        }
    }

    private fun processNextWidgetRestore() {
        if (widgetRestoreQueue.isEmpty()) return
        val (oldId, providerStr) = widgetRestoreQueue.removeAt(0)
        val component = android.content.ComponentName.unflattenFromString(providerStr)
        if (component == null) {
            prefs.removeWidgetProvider(oldId)
            processNextWidgetRestore()
            return
        }

        val mainActivity = mainActivityOrNull ?: return
        val newId = mainActivity.appWidgetHost.allocateAppWidgetId()

        // Try silent bind first (works if app has system permission)
        if (mainActivity.appWidgetManager.bindAppWidgetIdIfAllowed(newId, component)) {
            completeWidgetRestore(oldId, newId, providerStr)
            return
        }

        // Need user permission — launch bind dialog
        mainActivity.pendingWidgetId = newId
        mainActivity.onWidgetBindResult = result@{ success ->
            if (!isAdded || _binding == null) return@result
            if (success) {
                completeWidgetRestore(oldId, newId, providerStr)
            } else {
                mainActivity.appWidgetHost.deleteAppWidgetId(newId)
                prefs.removeWidgetProvider(oldId)
                processNextWidgetRestore()
            }
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
        }
        mainActivity.bindWidgetLauncher.launch(intent)
    }

    private fun completeWidgetRestore(oldId: Int, newId: Int, providerStr: String) {
        val mainActivity = mainActivityOrNull ?: return
        val info = mainActivity.appWidgetManager.getAppWidgetInfo(newId)
        if (info == null) {
            mainActivity.appWidgetHost.deleteAppWidgetId(newId)
            prefs.removeWidgetProvider(oldId)
            processNextWidgetRestore()
            return
        }

        // Migrate saved height and provider to new ID
        val height = prefs.getWidgetHeight(oldId)
        prefs.setWidgetHeight(newId, height)
        prefs.setWidgetProvider(newId, providerStr)
        prefs.removeWidgetProvider(oldId)

        // Add to container and update ID list
        val hostView = mainActivity.appWidgetHost.createView(
            requireContext().applicationContext, newId, info
        )
        addWidgetToContainer(hostView, newId)
        val ids = prefs.getWidgetIdList()
        ids.add(newId)
        prefs.setWidgetIdList(ids)
        updateWidgetContainerVisibility()

        // Process next queued widget
        processNextWidgetRestore()
    }

    private fun addWidgetToContainer(hostView: android.appwidget.AppWidgetHostView, widgetId: Int) {
        val container = getActiveContainer() ?: return
        val scrollView = getActiveScrollView() ?: return

        scrollView.layoutParams = (scrollView.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
            height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        }

        val heightDp = prefs.getWidgetHeight(widgetId)
        val heightPx = (heightDp * resources.displayMetrics.density).toInt()

        val wrapper = FrameLayout(requireContext()).apply {
            tag = widgetId
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                heightPx
            )
            clipChildren = true
            clipToPadding = true
        }

        hostView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.addView(hostView)

        // Invisible overlay to capture long-press; forwards all other events to widget
        var longPressDetected = false
        val gestureDetector = android.view.GestureDetector(requireContext(),
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: android.view.MotionEvent) {
                    longPressDetected = true
                    showWidgetOptionsDialog(widgetId)
                }
            })
        widgetGestureDetectors.add(gestureDetector)
        val overlay = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            contentDescription = "Widget"
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> longPressDetected = false
                    MotionEvent.ACTION_CANCEL -> longPressDetected = false
                }
                gestureDetector.onTouchEvent(event)
                if (longPressDetected) {
                    true
                } else {
                    // Forward event to the widget view underneath
                    hostView.dispatchTouchEvent(event)
                    // Must return true to keep receiving ACTION_UP/MOVE,
                    // otherwise GestureDetector never sees UP and the
                    // long-press timer always fires.
                    true
                }
            }
        }
        wrapper.addView(overlay)

        container.addView(wrapper)

        wrapper.post {
            if (!isAdded || _binding == null) return@post
            val widthDp = (wrapper.width / resources.displayMetrics.density).toInt()
            val heightDp = (wrapper.height / resources.displayMetrics.density).toInt()
            if (widthDp > 0 && heightDp > 0) {
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                }
                hostView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
            }
        }
    }

    private fun updateWidgetContainerVisibility() {
        val ids = prefs.getWidgetIdList()
        val hasWidgets = ids.isNotEmpty()

        if (prefs.widgetPlacement == Constants.WidgetPlacement.ABOVE) {
            binding.widgetScrollViewAbove?.visibility = if (hasWidgets) View.VISIBLE else View.GONE
            binding.widgetScrollViewBelow?.visibility = View.GONE
            binding.widgetContainerBelow?.removeAllViews()
        } else {
            binding.widgetScrollViewBelow?.visibility = if (hasWidgets) View.VISIBLE else View.GONE
            binding.widgetScrollViewAbove?.visibility = View.GONE
            binding.widgetContainerAbove?.removeAllViews()
        }

        resizeWidgetWrappers()
    }

    private fun resizeWidgetWrappers() {
        val container = getActiveContainer() ?: return
        val count = container.childCount
        if (count == 0) return

        val density = resources.displayMetrics.density
        for (i in 0 until count) {
            val wrapper = container.getChildAt(i)
            val widgetId = wrapper.tag as? Int ?: continue
            val heightDp = prefs.getWidgetHeight(widgetId)
            wrapper.layoutParams = (wrapper.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                height = (heightDp * density).toInt()
            }
            // Notify the widget of its new size so it can re-render
            wrapper.post {
                if (!isAdded || _binding == null) return@post
                val hostView = (wrapper as? FrameLayout)?.getChildAt(0) as? android.appwidget.AppWidgetHostView
                    ?: return@post
                val widthDp = (wrapper.width / density).toInt()
                if (widthDp > 0 && heightDp > 0) {
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                    }
                    hostView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
                }
            }
        }
    }

    private fun showWidgetOptionsDialog(widgetId: Int) {
        val ids = prefs.getWidgetIdList()
        val index = ids.indexOf(widgetId)

        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_widget_options, null)
        val container = view.findViewById<android.widget.LinearLayout>(R.id.widgetOptionsContainer)

        fun addMenuItem(text: String, action: () -> Unit) {
            val tv = TextView(requireContext()).apply {
                this.text = text
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
                setOnClickListener { dialog.dismiss(); action() }
            }
            container.addView(tv)
        }

        addMenuItem(getString(R.string.swap_widget)) {
            showWidgetPicker { providerInfo -> bindWidget(providerInfo, replaceIndex = index) }
        }
        addMenuItem(getString(R.string.resize_widget)) { showWidgetResizeDialog(widgetId) }
        addMenuItem(getString(R.string.remove_widget)) { removeWidget(widgetId) }
        if (ids.size > 1 && index > 0)
            addMenuItem(getString(R.string.move_up)) { moveWidget(index, index - 1) }
        if (ids.size > 1 && index < ids.size - 1)
            addMenuItem(getString(R.string.move_down)) { moveWidget(index, index + 1) }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun moveWidget(fromIndex: Int, toIndex: Int) {
        val ids = prefs.getWidgetIdList()
        val id = ids.removeAt(fromIndex)
        ids.add(toIndex, id)
        prefs.setWidgetIdList(ids)
        rebuildWidgetContainer()
    }

    private fun showWidgetResizeDialog(widgetId: Int) {
        val sizes = arrayOf(
            getString(R.string.widget_size_small) to 100,
            getString(R.string.widget_size_medium) to 200,
            getString(R.string.widget_size_large) to 300,
            getString(R.string.widget_size_extra_large) to 400,
            getString(R.string.widget_size_full) to 500
        )
        val currentHeight = prefs.getWidgetHeight(widgetId)

        val dialog = BottomSheetDialog(requireContext())
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(requireContext().getColorFromAttr(R.attr.primaryInverseColor))
            setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
        }

        // Drag handle
        val handle = View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        }
        container.addView(handle)

        for ((label, heightVal) in sizes) {
            val tv = TextView(requireContext()).apply {
                text = if (heightVal == currentHeight) "$label  \u2713" else label
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
                setOnClickListener {
                    prefs.setWidgetHeight(widgetId, heightVal)
                    resizeWidgetWrappers()
                    dialog.dismiss()
                }
            }
            container.addView(tv)
        }

        dialog.setContentView(container)
        dialog.show()
    }

    private fun rebuildWidgetContainer() {
        val container = getActiveContainer() ?: return
        container.removeAllViews()
        val scrollView = getActiveScrollView() ?: return
        scrollView.visibility = View.GONE

        val ids = prefs.getWidgetIdList()
        if (ids.isEmpty()) return

        val mainActivity = mainActivityOrNull ?: return
        val validIds = mutableListOf<Int>()

        for (wid in ids) {
            try {
                val info = mainActivity.appWidgetManager.getAppWidgetInfo(wid) ?: continue
                val hostView = mainActivity.appWidgetHost.createView(
                    requireContext().applicationContext, wid, info
                )
                addWidgetToContainer(hostView, wid)
                validIds.add(wid)
            } catch (e: Exception) {
                Log.e("HomeFragment", "rebuildWidget failed for id=$wid", e)
            }
        }
        prefs.setWidgetIdList(validIds)
        updateWidgetContainerVisibility()
    }

    private fun removeWidget(widgetId: Int) {
        val mainActivity = mainActivityOrNull ?: return
        mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
        prefs.removeWidgetProvider(widgetId)

        val ids = prefs.getWidgetIdList()
        ids.remove(widgetId)
        prefs.setWidgetIdList(ids)

        // Remove from container
        val container = getActiveContainer() ?: return
        for (i in 0 until container.childCount) {
            if (container.getChildAt(i).tag == widgetId) {
                container.removeViewAt(i)
                break
            }
        }
        updateWidgetContainerVisibility()
    }

    private fun showWidgetPicker(onSelected: (AppWidgetProviderInfo) -> Unit = { bindWidget(it) }) {
        val mainActivity = mainActivityOrNull ?: return
        val pm = requireContext().packageManager

        viewLifecycleOwner.lifecycleScope.launch {
            data class WidgetEntry(val appName: String, val widgetLabel: String, val provider: AppWidgetProviderInfo)
            val allEntries = withContext(Dispatchers.IO) {
                val installedProviders = mainActivity.appWidgetManager.installedProviders
                installedProviders.map { provider ->
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(provider.provider.packageName, 0)).toString()
                    } catch (e: Exception) {
                        provider.provider.packageName
                    }
                    WidgetEntry(appName, provider.loadLabel(pm), provider)
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
            }

            if (!isAdded || _binding == null) return@launch

            if (allEntries.isEmpty()) {
                requireContext().showToast(getString(R.string.no_widgets_available))
                return@launch
            }

            fun buildItems(query: String): MutableList<Pair<String, AppWidgetProviderInfo?>> {
                val items = mutableListOf<Pair<String, AppWidgetProviderInfo?>>()
                val filtered = if (query.isBlank()) allEntries
                else allEntries.filter {
                    it.appName.contains(query, true) || it.widgetLabel.contains(query, true)
                }
                var lastApp = ""
                for (entry in filtered) {
                    if (entry.appName != lastApp) {
                        items.add(Pair(entry.appName, null))
                        lastApp = entry.appName
                    }
                    items.add(Pair(entry.widgetLabel, entry.provider))
                }
                return items
            }

            val dialog = BottomSheetDialog(requireContext())
            val bgColor = requireContext().getColorFromAttr(R.attr.primaryInverseColor)
            val container = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(bgColor)
            }
            val searchField = android.widget.EditText(requireContext()).apply {
                hint = getString(R.string.search_widgets)
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                textSize = 16f
                setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                setHintTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
                background = null
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                isSingleLine = true
            }
            val listView = android.widget.ListView(requireContext())

            val textColor = requireContext().getColorFromAttr(R.attr.primaryColor)
            val headerColor = textColor
            var currentItems = buildItems("")

            val adapter = object : android.widget.BaseAdapter() {
                override fun getCount() = currentItems.size
                override fun getItem(position: Int) = currentItems[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getViewTypeCount() = 2
                override fun getItemViewType(position: Int) = if (currentItems[position].second == null) 0 else 1
                override fun isEnabled(position: Int) = currentItems[position].second != null

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val item = currentItems[position]
                    val isHeader = item.second == null
                    val textView = (convertView as? TextView) ?: TextView(requireContext())

                    if (isHeader) {
                        textView.text = item.first
                        textView.textSize = 14f
                        textView.setTypeface(null, android.graphics.Typeface.BOLD)
                        textView.setTextColor(headerColor)
                        textView.alpha = 0.6f
                        textView.setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 4.dpToPx())
                    } else {
                        textView.text = item.first
                        textView.textSize = 16f
                        textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                        textView.setTextColor(textColor)
                        textView.alpha = 1.0f
                        textView.setPadding(24.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                    }
                    return textView
                }
            }
            listView.adapter = adapter

            searchField.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    currentItems = buildItems(s?.toString() ?: "")
                    adapter.notifyDataSetChanged()
                }
            })

            listView.setOnItemClickListener { _, _, position, _ ->
                val providerInfo = currentItems[position].second ?: return@setOnItemClickListener
                dialog.dismiss()
                onSelected(providerInfo)
            }

            container.addView(searchField)
            container.addView(listView, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            ))
            dialog.setContentView(container)
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.show()

            // Expand to full height so search + list stay visible above keyboard
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
    }

    private fun bindWidget(providerInfo: AppWidgetProviderInfo, replaceIndex: Int = -1) {
        if (replaceIndex == -1 && prefs.getWidgetIdList().size >= 6) {
            requireContext().showToast(getString(R.string.max_widgets_reached), Toast.LENGTH_SHORT)
            return
        }
        try {
            val mainActivity = mainActivityOrNull ?: return
            val widgetId = mainActivity.appWidgetHost.allocateAppWidgetId()
            mainActivity.pendingWidgetId = widgetId
            mainActivity.pendingWidgetInfo = providerInfo

            val allowed = mainActivity.appWidgetManager.bindAppWidgetIdIfAllowed(
                widgetId, providerInfo.provider
            )

            if (allowed) {
                onWidgetBound(widgetId, providerInfo, replaceIndex)
            } else {
                val capturedReplaceIndex = replaceIndex
                mainActivity.onWidgetBindResult = result@{ success ->
                    if (!isAdded || _binding == null) return@result
                    if (success) {
                        mainActivity.pendingWidgetInfo?.let { widgetInfo ->
                            onWidgetBound(mainActivity.pendingWidgetId, widgetInfo, capturedReplaceIndex)
                        }
                    } else {
                        mainActivity.appWidgetHost.deleteAppWidgetId(mainActivity.pendingWidgetId)
                        requireContext().showToast(getString(R.string.widget_bind_permission_denied))
                    }
                }
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                }
                mainActivity.bindWidgetLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "bindWidget failed", e)
            requireContext().showToast(getString(R.string.couldnt_bind_widget, e.message))
        }
    }

    private fun onWidgetBound(widgetId: Int, providerInfo: AppWidgetProviderInfo, replaceIndex: Int) {
        try {
            val mainActivity = mainActivityOrNull ?: return

            if (providerInfo.configure != null) {
                val capturedReplaceIndex = replaceIndex
                mainActivity.onWidgetConfigureResult = result@{ success ->
                    if (!isAdded || _binding == null) return@result
                    if (success) {
                        finishWidgetSetup(widgetId, providerInfo, capturedReplaceIndex)
                    } else {
                        val stillValid = mainActivity.appWidgetManager.getAppWidgetInfo(widgetId)
                        if (stillValid != null) {
                            finishWidgetSetup(widgetId, providerInfo, capturedReplaceIndex)
                        } else {
                            mainActivity.appWidgetHost.deleteAppWidgetId(widgetId)
                        }
                    }
                }
                val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = providerInfo.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                mainActivity.configureWidgetLauncher.launch(configIntent)
            } else {
                finishWidgetSetup(widgetId, providerInfo, replaceIndex)
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "onWidgetBound failed", e)
            requireContext().showToast(getString(R.string.widget_setup_failed, e.message))
        }
    }

    private fun finishWidgetSetup(widgetId: Int, providerInfo: AppWidgetProviderInfo, replaceIndex: Int) {
        try {
            val mainActivity = mainActivityOrNull ?: return
            val ids = prefs.getWidgetIdList()

            if (replaceIndex in ids.indices) {
                // Swap: remove old widget, insert new one at same position
                val oldId = ids[replaceIndex]
                mainActivity.appWidgetHost.deleteAppWidgetId(oldId)
                prefs.removeWidgetProvider(oldId)
                ids[replaceIndex] = widgetId
            } else {
                // Add new
                ids.add(widgetId)
            }
            prefs.setWidgetIdList(ids)
            prefs.setWidgetProvider(widgetId, providerInfo.provider.flattenToString())

            rebuildWidgetContainer()
        } catch (e: Exception) {
            Log.e("HomeFragment", "finishWidgetSetup failed", e)
            requireContext().showToast(getString(R.string.couldnt_add_widget, e.message))
        }
    }

    override fun onDestroyView() {
        widgetGestureDetectors.clear()
        widgetRestoreQueue.clear()
        mainActivityOrNull?.let {
            it.onWidgetBindResult = null
            it.onWidgetConfigureResult = null
        }
        screenTouchListener?.cleanup()
        screenTouchListener = null
        viewTouchListeners.forEach { it.cleanup() }
        viewTouchListeners.clear()
        binding.widgetContainerAbove?.removeAllViews()
        binding.widgetContainerBelow?.removeAllViews()
        super.onDestroyView()
        _binding = null
    }
}