package net.pangolin.Pangolin.util

/**
 * Decides which apps use the VPN interface.
 *
 * Two modes, each with its own list so switching mode never loses a selection:
 * - [MODE_EXCLUDE]: every app uses the VPN except the excluded ones (addDisallowedApplication).
 * - [MODE_ONLY]: only the selected apps use the VPN; everything else uses the network directly
 *   (addAllowedApplication). Android does not allow mixing the two on one builder.
 */
internal object ExcludedApplications {
    /** SharedPreferences key of the in-app exclusion list (a string set of package names). */
    const val PREF_KEY = "excludedApplications"

    /** SharedPreferences key of the "only these apps" list (a string set of package names). */
    const val ALLOWED_PREF_KEY = "allowedApplications"

    /** SharedPreferences key of the mode: [MODE_EXCLUDE] or [MODE_ONLY]. */
    const val MODE_PREF_KEY = "vpnAppMode"
    const val MODE_EXCLUDE = "exclude"
    const val MODE_ONLY = "only"

    /** Whether the tunnel will run in "only these apps" mode with this config. */
    fun isOnlyMode(config: Config): Boolean =
        config.vpnAppMode == MODE_ONLY && config.allowedApplications.any { it.isNotBlank() }

    /**
     * Packages to pass to VpnService.Builder.addDisallowedApplication(). Pangolin is never
     * excluded, so the app's API traffic keeps following the tunnel. Empty in "only" mode.
     */
    fun forConfig(config: Config, ownPackage: String): List<String> =
        if (isOnlyMode(config)) emptyList()
        else config.excludedApplications.filter { it.isNotBlank() && it != ownPackage }.sorted()

    /**
     * Packages to pass to VpnService.Builder.addAllowedApplication(). Pangolin itself is always
     * added, so its own API traffic follows the tunnel as in the default mode. Empty unless the
     * mode is "only" and at least one app is selected: an empty allow list would mean every app.
     */
    fun allowedForConfig(config: Config, ownPackage: String): List<String> =
        if (!isOnlyMode(config)) emptyList()
        else (config.allowedApplications.filter { it.isNotBlank() } + ownPackage).distinct().sorted()

    /**
     * An installed app the user can pick; icons are kept by the UI. [isSystem] marks preinstalled
     * apps (including ones updated through the store, such as Android Auto).
     */
    data class InstalledApp(val packageName: String, val label: String, val isSystem: Boolean = false)

    /**
     * Excluded apps first so the current choice is visible at a glance, then alphabetical.
     * Excluded packages that aren't installed (or visible) any more are kept as rows labelled
     * by package name, so they can still be removed.
     */
    fun displayOrder(installed: List<InstalledApp>, excluded: Set<String>): List<InstalledApp> {
        val known = installed.mapTo(HashSet()) { it.packageName }
        val missing = excluded.filter { it !in known }.map { InstalledApp(it, it) }
        return (installed + missing).sortedWith(
            compareBy<InstalledApp> { it.packageName !in excluded }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy { it.packageName }
        )
    }

    /**
     * Case-insensitive match on label or package name; order is preserved. Hidden system apps
     * stay visible while they're excluded, so an active exclusion is never out of sight.
     */
    fun filter(
        apps: List<InstalledApp>,
        query: String,
        showSystem: Boolean = true,
        excluded: Set<String> = emptySet(),
    ): List<InstalledApp> {
        val q = query.trim()
        return apps.filter { app ->
            (showSystem || !app.isSystem || app.packageName in excluded) &&
                (q.isEmpty() || app.label.contains(q, ignoreCase = true) || app.packageName.contains(q, ignoreCase = true))
        }
    }
}
