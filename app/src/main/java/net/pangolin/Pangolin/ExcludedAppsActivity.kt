package net.pangolin.Pangolin

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pangolin.Pangolin.databinding.ActivityExcludedAppsBinding
import net.pangolin.Pangolin.databinding.ItemExcludedAppBinding
import net.pangolin.Pangolin.util.ExcludedApplications
import net.pangolin.Pangolin.util.ExcludedApplications.InstalledApp

/**
 * Lets the user choose which apps use the VPN, in one of two modes:
 * - exclude: every app uses the VPN except the ticked ones (addDisallowedApplication);
 * - only: only the ticked apps use the VPN (addAllowedApplication).
 * Each mode keeps its own list. Changes are saved on every toggle and applied on the next connect.
 */
class ExcludedAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExcludedAppsBinding
    private lateinit var prefs: SharedPreferences

    // Ticked packages of the current mode's list
    private val excluded = HashSet<String>()
    private var mode = ExcludedApplications.MODE_EXCLUDE
    private var installed: List<InstalledApp> = emptyList()
    private var allApps: List<InstalledApp> = emptyList()
    private val icons = HashMap<String, Drawable>()
    private var loaded = false
    private var showSystem = false
    private val adapter = AppAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExcludedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        mode = prefs.getString(ExcludedApplications.MODE_PREF_KEY, null) ?: ExcludedApplications.MODE_EXCLUDE
        excluded += prefs.getStringSet(listKey(), null).orEmpty()
        showSystem = prefs.getBoolean(SHOW_SYSTEM_PREF_KEY, false)

        binding.modeGroup.check(if (mode == ExcludedApplications.MODE_ONLY) R.id.mode_only else R.id.mode_exclude)
        updateModeHint()
        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = if (checkedId == R.id.mode_only) ExcludedApplications.MODE_ONLY else ExcludedApplications.MODE_EXCLUDE
            if (newMode == mode) return@addOnButtonCheckedListener
            mode = newMode
            prefs.edit { putString(ExcludedApplications.MODE_PREF_KEY, mode) }
            excluded.clear()
            excluded += prefs.getStringSet(listKey(), null).orEmpty()
            updateModeHint()
            if (loaded) {
                allApps = ExcludedApplications.displayOrder(installed, excluded)
                applyFilter()
            }
        }

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.search.doAfterTextChanged { applyFilter() }

        lifecycleScope.launch {
            installed = withContext(Dispatchers.IO) { loadInstalledApps() }
            // Ordered once, so rows don't jump around while the user toggles them
            allApps = ExcludedApplications.displayOrder(installed, excluded)
            loaded = true
            binding.progress.isVisible = false
            applyFilter()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.excluded_apps, menu)
        menu.findItem(R.id.action_show_system).isChecked = showSystem
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_show_system -> {
                showSystem = !showSystem
                item.isChecked = showSystem
                prefs.edit { putBoolean(SHOW_SYSTEM_PREF_KEY, showSystem) }
                applyFilter()
            }
            R.id.action_clear -> {
                excluded.clear()
                persist()
                // Re-filter: system apps that were only shown because they were excluded disappear
                applyFilter()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** Apps the user could meaningfully exclude: visible to us and able to use the network. */
    @SuppressLint("QueryPermissionsNeeded") // Visibility is granted by the manifest's <queries>
    private fun loadInstalledApps(): List<InstalledApp> {
        val pm = packageManager
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
        return packages.mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            if (info.packageName == packageName) return@mapNotNull null
            if (info.requestedPermissions?.contains(Manifest.permission.INTERNET) != true) return@mapNotNull null
            synchronized(icons) { icons[info.packageName] = appInfo.loadIcon(pm) }
            InstalledApp(
                packageName = info.packageName,
                label = appInfo.loadLabel(pm).toString(),
                // Also set for preinstalled apps updated through the store
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            )
        }
    }

    private fun applyFilter() {
        if (!loaded) return
        val shown = ExcludedApplications.filter(
            allApps, binding.search.text?.toString().orEmpty(), showSystem, excluded
        )
        adapter.submit(shown)
        binding.empty.isVisible = shown.isEmpty()
        binding.empty.setText(
            if (showSystem) R.string.excluded_apps_empty else R.string.excluded_apps_empty_system_hidden
        )
    }

    private fun toggle(packageName: String) {
        if (!excluded.remove(packageName)) excluded.add(packageName)
        persist()
    }

    private fun persist() {
        // ConfigManager listens for these keys and reloads the config
        prefs.edit { putStringSet(listKey(), HashSet(excluded)) }
        updateModeHint()
    }

    private fun listKey() =
        if (mode == ExcludedApplications.MODE_ONLY) ExcludedApplications.ALLOWED_PREF_KEY else ExcludedApplications.PREF_KEY

    private fun updateModeHint() {
        binding.modeHint.setText(
            when {
                mode != ExcludedApplications.MODE_ONLY -> R.string.vpn_app_mode_exclude_hint
                excluded.isEmpty() -> R.string.vpn_app_mode_only_empty_hint
                else -> R.string.vpn_app_mode_only_hint
            }
        )
    }

    private inner class AppAdapter : RecyclerView.Adapter<AppViewHolder>() {
        private var items: List<InstalledApp> = emptyList()

        fun submit(newItems: List<InstalledApp>) {
            items = newItems
            @Suppress("NotifyDataSetChanged") // Whole list changes on every search keystroke
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AppViewHolder(ItemExcludedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = items[position]
            with(holder.binding) {
                label.text = app.label
                packageName.text = app.packageName
                packageName.isVisible = app.label != app.packageName
                val appIcon = synchronized(icons) { icons[app.packageName] }
                if (appIcon != null) icon.setImageDrawable(appIcon) else icon.setImageResource(android.R.drawable.sym_def_app_icon)
                checkbox.isChecked = app.packageName in excluded
                root.setOnClickListener {
                    toggle(app.packageName)
                    checkbox.isChecked = app.packageName in excluded
                }
            }
        }
    }

    private class AppViewHolder(val binding: ItemExcludedAppBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        // Picker display preference only; not part of the tunnel config
        const val SHOW_SYSTEM_PREF_KEY = "excludedAppsShowSystem"
    }
}
