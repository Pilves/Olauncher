package app.olauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hasBeenHours
import app.olauncher.helper.isDarkThemeOn
import app.olauncher.helper.isDefaultLauncher
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isTablet
import app.olauncher.helper.resetLauncherViaFakeActivity
import app.olauncher.helper.setPlainWallpaper
import app.olauncher.helper.showLauncherSelector
import app.olauncher.helper.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null
    private var themeCheckRetries = 0

    lateinit var appWidgetHost: AppWidgetHost
    lateinit var appWidgetManager: AppWidgetManager
    var pendingWidgetId: Int = -1
    var pendingWidgetInfo: AppWidgetProviderInfo? = null

    var onWidgetBindResult: ((Boolean) -> Unit)? = null
    var onWidgetConfigureResult: ((Boolean) -> Unit)? = null

    lateinit var bindWidgetLauncher: ActivityResultLauncher<Intent>
    lateinit var configureWidgetLauncher: ActivityResultLauncher<Intent>
    lateinit var enableAdminLauncher: ActivityResultLauncher<Intent>
    lateinit var launcherSelectorLauncher: ActivityResultLauncher<Intent>

//    override fun onBackPressed() {
//        if (navController.currentDestination?.id != R.id.mainFragment)
//            super.onBackPressed()
//    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, Constants.APPWIDGET_HOST_ID)

        savedInstanceState?.let {
            pendingWidgetId = it.getInt("pendingWidgetId", -1)
        }

        bindWidgetLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val success = result.resultCode == Activity.RESULT_OK
            onWidgetBindResult?.invoke(success)
            onWidgetBindResult = null
        }

        configureWidgetLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val success = result.resultCode == Activity.RESULT_OK
            onWidgetConfigureResult?.invoke(success)
            onWidgetConfigureResult = null
        }

        enableAdminLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK)
                prefs.lockModeOn = true
        }

        launcherSelectorLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK)
                resetLauncherViaFakeActivity()
        }

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id != R.id.mainFragment) {
                    if (navController.popBackStack()) {
                        // Successfully popped back
                    } else {
                        // if you want other system/activity level handling
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            viewModel.setDefaultClockApp()
            viewModel.resetLauncherLiveData.call()
        }

        initObservers(viewModel)
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pendingWidgetId", pendingWidgetId)
    }

    override fun onDestroy() {
        onWidgetBindResult = null
        onWidgetConfigureResult = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        themeCheckRetries = 0
        appWidgetHost.startListening()
        restartLauncherOrCheckTheme()
    }

    override fun onStop() {
        appWidgetHost.stopListening()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        backToHomeScreen()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.dailyWallpaper && AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            setPlainWallpaper()
            viewModel.setWallpaperWorker()
            recreate()
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(launcherSelectorLauncher)
        }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.HIDDEN -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.hidden_apps)
                        .setMessage(R.string.hidden_apps_message)
                        .setPositiveButton(R.string.okay, null)
                        .show()
                }

                Constants.Dialog.KEYBOARD -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.keyboard_message)
                        .setPositiveButton(R.string.okay, null)
                        .show()
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.screen_time)
                        .setMessage(R.string.app_usage_message)
                        .setPositiveButton(R.string.okay) { _, _ ->
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                        .setNegativeButton(R.string.not_now, null)
                        .show()
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        if (this.isDarkThemeOn())
            setPlainWallpaper(this, android.R.color.black)
        else setPlainWallpaper(this, android.R.color.white)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun restartLauncherOrCheckTheme(forceRestart: Boolean = false) {
        if (forceRestart || prefs.launcherRestartTimestamp.hasBeenHours(4)) {
            prefs.launcherRestartTimestamp = System.currentTimeMillis()
            lifecycleScope.launch(Dispatchers.IO) {
                cacheDir.deleteRecursively()
                withContext(Dispatchers.Main) {
                    recreate()
                }
            }
        } else
            checkTheme()
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            if ((prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.white))
                || (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_NO && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.black))
            ) {
                if (themeCheckRetries++ < 2)
                    restartLauncherOrCheckTheme(true)
            }
        }
    }

}