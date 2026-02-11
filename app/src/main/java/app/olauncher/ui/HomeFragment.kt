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
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
        viewLifecycleOwner.lifecycleScope.launch {
            restoreWidget()
        }
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen(false)
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
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
            showAppList(slot, prefs.getHomeAppName(slot).isNotEmpty(), true)
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

        val formatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
        var dateText = LocalDate.now().format(formatter)

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
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

        val homeAppsNum = prefs.homeAppsNum
        val homeAppViews = listOf(
            binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
            binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8
        )

        for (i in 0 until homeAppsNum) {
            val slot = i + 1
            val view = homeAppViews[i]
            view.visibility = View.VISIBLE
            val appName = prefs.getHomeAppName(slot)
            if (!setHomeAppText(view, appName, prefs.getHomeAppPackage(slot), prefs.getHomeAppUser(slot))) {
                prefs.setHomeAppName(slot, "")
                prefs.setHomeAppPackage(slot, "")
            }
            view.contentDescription = appName.ifEmpty { getString(R.string.long_press_to_select_app) }
        }
    }

    private fun setHomeAppText(textView: TextView, appName: String, packageName: String, userString: String): Boolean {
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            return true
        }
        textView.text = ""
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
        if (prefs.getAppName(location).isEmpty()) showLongPressToast()
        else launchApp(
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
            Constants.SwipeDownAction.SEARCH -> openSearch(requireContext())
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
            Constants.GestureAction.OPEN_SEARCH -> openSearch(requireContext())
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
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
                prefs.lockModeOn = false
            }
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
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to open Digital Wellbeing", e)
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("HomeFragment", "Failed to open Samsung Digital Wellbeing", e)
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): OnSwipeTouchListener {
        return object : OnSwipeTouchListener(context) {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    binding.lock.performClick()
                else if (prefs.lockModeOn)
                    lockPhone()
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
                showAppList(Constants.FLAG_LAUNCH_APP)
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
        val options = arrayOf(
            getString(R.string.add_widget),
            getString(R.string.settings)
        )
        AlertDialog.Builder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showWidgetPicker()
                    1 -> {
                        findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                        viewModel.firstOpen(false)
                    }
                }
            }
            .show()
    }

    // ─── Multi-widget system ───

    private var flashlightOn: Boolean = false
    private var pendingSwapIndex: Int = -1

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

    private suspend fun restoreWidget() {
        prefs.migrateWidgetIfNeeded()
        val ids = prefs.getWidgetIdList()
        if (ids.isEmpty()) return

        val mainActivity = requireActivity() as MainActivity
        val savedProviders = prefs.getAllWidgetProviders()
        widgetRestoreQueue.clear()

        // Gather widget info on background thread
        data class WidgetRestoreInfo(val wid: Int, val info: AppWidgetProviderInfo?, val providerStr: String?)
        val restoreInfoList = withContext(Dispatchers.Default) {
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

        val mainActivity = requireActivity() as MainActivity
        val newId = mainActivity.appWidgetHost.allocateAppWidgetId()

        // Try silent bind first (works if app has system permission)
        if (mainActivity.appWidgetManager.bindAppWidgetIdIfAllowed(newId, component)) {
            completeWidgetRestore(oldId, newId, providerStr)
            return
        }

        // Need user permission — launch bind dialog
        mainActivity.pendingWidgetId = newId
        mainActivity.onWidgetBindResult = { success ->
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
        val mainActivity = requireActivity() as MainActivity
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

        // Invisible overlay to capture long-press even on tappable widgets (e.g. Spotify)
        val gestureDetector = android.view.GestureDetector(requireContext(),
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: android.view.MotionEvent) {
                    showWidgetOptionsDialog(widgetId)
                }
                override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                    // Forward tap to widget underneath
                    val down = android.view.MotionEvent.obtain(
                        e.downTime, e.eventTime,
                        android.view.MotionEvent.ACTION_DOWN, e.x, e.y, 0
                    )
                    hostView.dispatchTouchEvent(down)
                    down.recycle()
                    val up = android.view.MotionEvent.obtain(
                        e.downTime, e.eventTime,
                        android.view.MotionEvent.ACTION_UP, e.x, e.y, 0
                    )
                    hostView.dispatchTouchEvent(up)
                    up.recycle()
                    return true
                }
            })
        val overlay = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            contentDescription = "Widget"
            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }
        wrapper.addView(overlay)

        container.addView(wrapper)

        wrapper.post {
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

        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add(getString(R.string.swap_widget))
        actions.add {
            pendingSwapIndex = index
            showWidgetPicker { providerInfo -> bindWidget(providerInfo, replaceIndex = index) }
        }

        options.add(getString(R.string.resize_widget))
        actions.add { showWidgetResizeDialog(widgetId) }

        options.add(getString(R.string.remove_widget))
        actions.add { removeWidget(widgetId) }

        if (ids.size > 1 && index > 0) {
            options.add(getString(R.string.move_up))
            actions.add { moveWidget(index, index - 1) }
        }
        if (ids.size > 1 && index < ids.size - 1) {
            options.add(getString(R.string.move_down))
            actions.add { moveWidget(index, index + 1) }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.widget_options)
            .setItems(options.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(R.string.not_now, null)
            .show()
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
        val currentIndex = sizes.indexOfFirst { it.second == currentHeight }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.resize_widget)
            .setSingleChoiceItems(sizes.map { it.first }.toTypedArray(), currentIndex) { dialog, which ->
                prefs.setWidgetHeight(widgetId, sizes[which].second)
                resizeWidgetWrappers()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun rebuildWidgetContainer() {
        val container = getActiveContainer() ?: return
        container.removeAllViews()
        val scrollView = getActiveScrollView() ?: return
        scrollView.visibility = View.GONE

        val ids = prefs.getWidgetIdList()
        if (ids.isEmpty()) return

        val mainActivity = requireActivity() as MainActivity
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
        val mainActivity = requireActivity() as MainActivity
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
        val mainActivity = requireActivity() as MainActivity
        val installedProviders = mainActivity.appWidgetManager.installedProviders
        if (installedProviders.isEmpty()) {
            requireContext().showToast(getString(R.string.no_widgets_available))
            return
        }

        val pm = requireContext().packageManager

        // Build grouped data: map of appName -> list of (widgetLabel, providerInfo)
        data class WidgetEntry(val appName: String, val widgetLabel: String, val provider: AppWidgetProviderInfo)
        val allEntries = installedProviders.map { provider ->
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(provider.provider.packageName, 0)).toString()
            } catch (e: Exception) {
                provider.provider.packageName
            }
            WidgetEntry(appName, provider.loadLabel(pm), provider)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })

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

    private fun bindWidget(providerInfo: AppWidgetProviderInfo, replaceIndex: Int = -1) {
        try {
            val mainActivity = requireActivity() as MainActivity
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
                mainActivity.onWidgetBindResult = { success ->
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
            val mainActivity = requireActivity() as MainActivity

            if (providerInfo.configure != null) {
                val capturedReplaceIndex = replaceIndex
                mainActivity.onWidgetConfigureResult = { success ->
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
            val mainActivity = requireActivity() as MainActivity
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
        screenTouchListener?.cleanup()
        screenTouchListener = null
        viewTouchListeners.forEach { it.cleanup() }
        viewTouchListeners.clear()
        super.onDestroyView()
        _binding = null
    }
}