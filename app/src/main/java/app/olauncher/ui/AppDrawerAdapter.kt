package app.olauncher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.helper.AppLimitManager
import app.olauncher.helper.IconPackManager
import app.olauncher.helper.getColorFromAttr
import com.google.android.material.bottomsheet.BottomSheetDialog
import app.olauncher.helper.dpToPx
import app.olauncher.helper.formattedTimeSpent
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
) : ListAdapter<AppModel, AppDrawerAdapter.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }

        private val DIACRITICAL_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val SEPARATOR_REGEX = Regex("[-_+,. ]")
    }

    @Volatile private var autoLaunch = true
    @Volatile private var isBangSearch = false
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()

    @Volatile var usageStats: Map<String, Long> = emptyMap()
    var sortByUsage: Boolean = false
    var showIcons: Boolean = false
    var iconPackPackage: String = ""

    @Volatile var appsList: MutableList<AppModel> = mutableListOf()
    @Volatile var appFilteredList: MutableList<AppModel> = mutableListOf()
    @Volatile private var normalizedLabels: Map<String, String> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            if (itemCount == 0 || position == RecyclerView.NO_POSITION) return
            val appModel = getItem(position)
            holder.bind(
                flag,
                appLabelGravity,
                myUserHandle,
                appModel,
                appClickListener,
                appDeleteListener,
                appInfoListener,
                appHideListener,
                appRenameListener,
                usageStats,
                showIcons,
                iconPackPackage
            )
        } catch (e: Exception) {
            Log.e("AppDrawerAdapter", "Error binding view holder", e)
        }
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = charSearch?.startsWith("!") ?: false
                autoLaunch = charSearch?.startsWith(" ")?.not() ?: true

                val snapshot = appsList.toList()
                val statsSnapshot = usageStats

                val searchText = charSearch?.toString()?.trim() ?: ""
                var appFilteredList = (if (searchText.isBlank()) snapshot
                else snapshot.filter { app ->
                    appLabelMatches(app.appLabel, searchText)
                }).toMutableList()

                if (sortByUsage && statsSnapshot.isNotEmpty()) {
                    appFilteredList = appFilteredList.sortedByDescending { statsSnapshot[it.appPackage] ?: 0L }.toMutableList()
                }

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.values?.let {
                    val items = (it as? MutableList<AppModel>) ?: (it as? List<AppModel>)?.toMutableList() ?: return
                    appFilteredList = items
                    val currentFiltered = appFilteredList.toList()
                    submitList(currentFiltered) {
                        autoLaunch(currentFiltered)
                    }
                }
            }
        }
    }

    private fun autoLaunch(filteredSnapshot: List<AppModel>) {
        try {
            if (itemCount == 1
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
                && filteredSnapshot.isNotEmpty()
            ) {
                Handler(Looper.getMainLooper()).post {
                    try { appClickListener(filteredSnapshot[0]) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("AppDrawerAdapter", "Error during auto launch", e)
        }
    }

    private fun appLabelMatches(appLabel: String, charSearch: String): Boolean {
        return appLabel.contains(charSearch, true) ||
                (normalizedLabels[appLabel] ?: appLabel).contains(charSearch, true)
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        // Add empty app for bottom padding in recyclerview
        val list = appsList.toMutableList()
        list.add(AppModel("", null, "", "", false, android.os.Process.myUserHandle()))
        this.appsList = list
        val newLabels = mutableMapOf<String, String>()
        for (app in list) {
            newLabels[app.appLabel] = Normalizer.normalize(app.appLabel, Normalizer.Form.NFD)
                .replace(DIACRITICAL_REGEX, "")
                .replace(SEPARATOR_REGEX, "")
        }
        normalizedLabels = newLabels
        if (sortByUsage && usageStats.isNotEmpty()) {
            filter.filter("")
        } else {
            this.appFilteredList = list.toMutableList()
            submitList(list.toList())
        }
    }

    fun launchFirstInList() {
        if (appFilteredList.size > 0)
            appClickListener(appFilteredList[0])
    }

    fun removeApp(position: Int) {
        if (position < 0 || position >= appFilteredList.size) return
        val app = appFilteredList[position]
        appFilteredList = appFilteredList.toMutableList().also { it.removeAt(position) }
        appsList = appsList.toMutableList().also { it.remove(app) }
        submitList(appFilteredList.toList())
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(binding.root) {

        private var currentTextWatcher: TextWatcher? = null

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            usageStats: Map<String, Long> = emptyMap(),
            showIcons: Boolean = false,
            iconPackPackage: String = "",
        ) =
            with(binding) {
                if (appModel.appPackage.isEmpty()) {
                    appTitle.text = ""
                    appTitle.setOnClickListener(null)
                    appTitle.setOnLongClickListener(null)
                    appUsageTime.visibility = View.GONE
                    appTitle.setCompoundDrawablesRelative(null, null, null, null)
                    otherProfileIndicator.isVisible = false
                    appHideLayout.visibility = View.GONE
                    renameLayout.visibility = View.GONE
                    return
                }
                appHideLayout.visibility = View.GONE
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
                appTitle.text = appModel.appLabel + if (appModel.isNew == true) " ✦" else ""
                appTitle.gravity = appLabelGravity
                otherProfileIndicator.isVisible = appModel.user != myUserHandle

                // Show app icon if enabled
                if (showIcons && appModel.appPackage.isNotEmpty()) {
                    val iconSize = 20.dpToPx()
                    val icon = if (iconPackPackage.isNotEmpty()) {
                        IconPackManager.getIconForApp(root.context, iconPackPackage, appModel.appPackage, appModel.activityClassName)
                    } else null
                    val drawable = icon ?: try {
                        root.context.packageManager.getApplicationIcon(appModel.appPackage)
                    } catch (_: Exception) { null }
                    drawable?.setBounds(0, 0, iconSize, iconSize)
                    appTitle.setCompoundDrawablesRelative(drawable, null, null, null)
                    appTitle.compoundDrawablePadding = 8.dpToPx()
                } else {
                    appTitle.setCompoundDrawablesRelative(null, null, null, null)
                }

                val timeMs = usageStats[appModel.appPackage] ?: 0L
                if (timeMs > 0 && appModel.appPackage.isNotEmpty()) {
                    appUsageTime.text = root.context.formattedTimeSpent(timeMs)
                    appUsageTime.visibility = View.VISIBLE
                } else {
                    appUsageTime.visibility = View.GONE
                }

                appTitle.setOnClickListener { clickListener(appModel) }
                appTitle.setOnLongClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        appDelete.alpha = if (root.context.isSystemApp(appModel.appPackage)) 0.5f else 1.0f
                        appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS)
                            root.context.getString(R.string.adapter_show)
                        else
                            root.context.getString(R.string.adapter_hide)
                        appTitle.visibility = View.INVISIBLE
                        appHideLayout.visibility = View.VISIBLE
                        appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                        appHabit.isVisible = false
                        appBadHabit.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                        if (appBadHabit.isVisible) {
                            appBadHabit.text = if (AppLimitManager.hasLimit(root.context, appModel.appPackage))
                                root.context.getString(R.string.remove_time_limit)
                            else
                                root.context.getString(R.string.set_time_limit)
                        }
                    }
                    true
                }
                appRename.setOnClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        val appNameHint = getAppName(etAppRename.context, appModel.appPackage)
                        etAppRename.hint = appNameHint
                        etAppRename.setText(appModel.appLabel)
                        etAppRename.setSelectAllOnFocus(true)
                        renameLayout.visibility = View.VISIBLE
                        appHideLayout.visibility = View.GONE
                        etAppRename.showKeyboard()
                        etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE;

                        currentTextWatcher?.let { etAppRename.removeTextChangedListener(it) }
                        val watcher = object : TextWatcher {
                            override fun afterTextChanged(s: Editable?) {
                                etAppRename.hint = appNameHint
                            }

                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) {
                            }

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) {
                                etAppRename.hint = ""
                            }
                        }
                        currentTextWatcher = watcher
                        etAppRename.addTextChangedListener(watcher)
                    }
                }
                etAppRename.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus)
                        appTitle.visibility = View.INVISIBLE
                    else
                        appTitle.visibility = View.VISIBLE
                }
                etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                    if (actionCode == EditorInfo.IME_ACTION_DONE) {
                        val renameLabel = etAppRename.text.toString().trim()
                        if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                            appRenameListener(appModel, renameLabel)
                            renameLayout.visibility = View.GONE
                        }
                        true
                    } else {
                        false
                    }
                }
                tvSaveRename.setOnClickListener {
                    etAppRename.hideKeyboard()
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        renameLayout.visibility = View.GONE
                    } else {
                        val fallbackLabel = try {
                            val packageManager = etAppRename.context.packageManager
                            packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(appModel.appPackage, 0)
                            ).toString()
                        } catch (e: Exception) {
                            appModel.appPackage
                        }
                        appRenameListener(appModel, fallbackLabel)
                        renameLayout.visibility = View.GONE
                    }
                }
                appInfo.setOnClickListener { appInfoListener(appModel) }
                appBadHabit.setOnClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        if (AppLimitManager.hasLimit(root.context, appModel.appPackage)) {
                            AppLimitManager.removeLimit(root.context, appModel.appPackage)
                            appHideLayout.visibility = View.GONE
                            appTitle.visibility = View.VISIBLE
                        } else {
                            showTimeLimitPicker(root.context, appModel.appPackage) {
                                appHideLayout.visibility = View.GONE
                                appTitle.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                appDelete.setOnClickListener { appDeleteListener(appModel) }
                appMenuClose.setOnClickListener {
                    appHideLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                }
                appRenameClose.setOnClickListener {
                    renameLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                }
                appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
            }

        private fun getAppName(context: Context, appPackage: String): String {
            return try {
                val packageManager = context.packageManager
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(appPackage, 0)
                ).toString()
            } catch (e: Exception) {
                appPackage
            }
        }

        private fun showTimeLimitPicker(context: Context, packageName: String, onDone: () -> Unit) {
            val dialog = BottomSheetDialog(context)
            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(context.getColorFromAttr(R.attr.primaryInverseColor))
                setPadding(0, 12.dpToPx(), 0, 24.dpToPx())
            }

            val handle = View(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    bottomMargin = 16.dpToPx()
                }
                setBackgroundColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            }
            container.addView(handle)

            val title = android.widget.TextView(context).apply {
                text = context.getString(R.string.select_time_limit)
                textSize = 16f
                setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 12.dpToPx())
            }
            container.addView(title)

            val options = listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 120 to "2 hours")
            for ((minutes, label) in options) {
                val tv = android.widget.TextView(context).apply {
                    text = label
                    textSize = 16f
                    setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                    setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
                    setOnClickListener {
                        AppLimitManager.setLimit(context, packageName, minutes)
                        dialog.dismiss()
                        onDone()
                    }
                }
                container.addView(tv)
            }

            dialog.setContentView(container)
            dialog.show()
        }
    }
}
