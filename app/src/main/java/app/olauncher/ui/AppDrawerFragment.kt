package app.olauncher.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.data.AppModel
import app.olauncher.helper.AppLimitManager
import app.olauncher.helper.UsageStatsHelper
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope


class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
        }
        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
    }

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            binding.search.queryHint = "Please select an app"
        try {
            val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
            if (searchTextView != null) searchTextView.gravity = prefs.appLabelAlignment
        } catch (e: Exception) {
            Log.e("AppDrawerFragment", "Failed to set search text alignment", e)
        }
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val ctx = context ?: return true
                if (query?.startsWith("!") == true)
                    ctx.openUrl(Constants.URL_DUCK_SEARCH + java.net.URLEncoder.encode(query, "UTF-8"))
                else if (adapter.itemCount == 0)
                    ctx.openSearch(query?.trim())
                else
                    adapter.launchFirstInList()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                _binding?.appRename?.visibility = if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    try {
                        adapter.filter.filter(newText) {
                            _binding?.let { b ->
                                if (adapter.itemCount == 0 && newText.isNotBlank()) {
                                    b.appDrawerTip.text = getString(R.string.no_apps_found)
                                    b.appDrawerTip.visibility = View.VISIBLE
                                } else {
                                    b.appDrawerTip.visibility = View.GONE
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AppDrawerFragment", "Error filtering app list", e)
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, 150)
                return true
            }
        })
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = {
                if (!isAdded || it.appPackage.isEmpty())
                    return@AppDrawerAdapter
                if (flag == Constants.FLAG_LAUNCH_APP && checkBadHabitAndLaunch(it)) {
                    // Bad habit check is handling launch asynchronously
                } else {
                    viewModel.selectedApp(it, flag)
                    if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                        findNavController().popBackStack(R.id.mainFragment, false)
                    else
                        findNavController().popBackStack()
                }
            },
            appInfoListener = {
                if (!isAdded) return@AppDrawerAdapter
                val ctx = context ?: return@AppDrawerAdapter
                openAppInfo(
                    ctx,
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = {
                val ctx = context ?: return@AppDrawerAdapter
                ctx.apply {
                    if (isSystemApp(it.appPackage))
                        showToast(getString(R.string.system_app_cannot_delete))
                    else
                        uninstall(it.appPackage)
                }
            },
            appHideListener = { appModel, position ->
                if (!isAdded) return@AppDrawerAdapter
                adapter.removeApp(position)

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage) // for backward compatibility
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                } else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    _binding?.search?.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                prefs.setAppRenameLabel(appModel.appPackage, renameLabel)
                viewModel.getAppList()
            }
        )
        adapter.showIcons = prefs.showIcons
        adapter.iconPackPackage = prefs.iconPackPackage

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                val rv = _binding?.recyclerView ?: return scrollRange
                if (overScroll < -10 && rv.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        scrollListener = getRecyclerViewOnScrollListener()
        binding.recyclerView.addOnScrollListener(scrollListener!!)
        binding.recyclerView.itemAnimator = null
        if (requireContext().isEinkDisplay().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) {
            if (it && flag == Constants.FLAG_LAUNCH_APP) {
                binding.appDrawerTip.visibility = View.VISIBLE
                binding.appDrawerTip.isSelected = true
            }
        }
        val wantSortByUsage = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
                && requireContext().appUsagePermissionGranted()
                && prefs.appDrawerSortByUsage
        adapter.sortByUsage = wantSortByUsage

        // Load cached usage stats immediately so sort works on first render
        if (wantSortByUsage) {
            val cached = prefs.getCachedUsageStats()
            if (cached.isNotEmpty()) adapter.usageStats = cached
        }

        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                it?.let { appModels ->
                    adapter.setAppList(appModels.toMutableList())
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
            && requireContext().appUsagePermissionGranted()
        ) {
            viewModel.perAppScreenTime.observe(viewLifecycleOwner) { stats ->
                adapter.usageStats = stats
                prefs.setCachedUsageStats(stats)
                adapter.filter.filter(binding.search.query)
            }
            viewModel.getPerAppScreenTime()
        }
    }

    private fun initClickListeners() {
        binding.appDrawerTip.setOnClickListener {
            binding.appDrawerTip.isSelected = false
            binding.appDrawerTip.isSelected = true
        }
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8) {
                prefs.setHomeAppName(flag, name)
            }
            findNavController().popBackStack()
        }
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop)
                            _binding?.search?.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1))
                            _binding?.search?.hideKeyboard()
                        else if (!recyclerView.canScrollVertically(-1))
                            if (!onTop && isRemoving.not())
                                _binding?.search?.showKeyboard(prefs.autoShowKeyboard)
                    }
                }
            }
        }
    }

    private fun checkMessageAndExit() {
        if (!isAdded) return
        findNavController().popBackStack()
    }

    /**
     * Returns true if the app is a bad habit and we're handling it (async check),
     * false if we should proceed with normal launch.
     */
    private fun checkBadHabitAndLaunch(appModel: AppModel): Boolean {
        val ctx = context ?: return false
        if (!AppLimitManager.hasLimit(ctx, appModel.appPackage)) return false
        val limitMinutes = AppLimitManager.getLimit(ctx, appModel.appPackage) ?: return false

        viewLifecycleOwner.lifecycleScope.launch {
            val usageMs = withContext(Dispatchers.IO) {
                UsageStatsHelper.getUsageForApp(ctx, appModel.appPackage)
            }
            if (!isAdded) return@launch
            val usageMinutes = usageMs / 60_000
            if (usageMinutes >= limitMinutes) {
                showBadHabitWarningDialog(appModel, usageMinutes, limitMinutes)
            } else {
                viewModel.selectedApp(appModel, flag)
                findNavController().popBackStack(R.id.mainFragment, false)
            }
        }
        return true
    }

    private fun showBadHabitWarningDialog(appModel: AppModel, usageMinutes: Long, limitMinutes: Int) {
        val ctx = context ?: return
        val appName = appModel.appLabel.ifEmpty { appModel.appPackage }
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

        val handle = android.view.View(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(ctx.getColorFromAttr(R.attr.primaryColorTrans50))
        }
        container.addView(handle)

        val message = android.widget.TextView(ctx).apply {
            text = ctx.getString(R.string.app_limit_warning, appName, usageText, limitText)
            textSize = 16f
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 16.dpToPx())
        }
        container.addView(message)

        val openAnyway = android.widget.TextView(ctx).apply {
            text = ctx.getString(R.string.open_anyway)
            textSize = 16f
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            setOnClickListener {
                dialog.dismiss()
                if (!isAdded) return@setOnClickListener
                viewModel.selectedApp(appModel, flag)
                findNavController().popBackStack(R.id.mainFragment, false)
            }
        }
        container.addView(openAnyway)

        val goBack = android.widget.TextView(ctx).apply {
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

    override fun onStart() {
        super.onStart()
        binding.search.showKeyboard(prefs.autoShowKeyboard)
    }

    override fun onStop() {
        _binding?.search?.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        scrollListener?.let { binding.recyclerView.removeOnScrollListener(it) }
        scrollListener = null
        super.onDestroyView()
        _binding = null
    }
}