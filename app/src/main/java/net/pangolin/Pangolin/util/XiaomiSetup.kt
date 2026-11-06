package net.pangolin.Pangolin.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * MIUI / HyperOS keep their own background-kill rules on top of Android's: an
 * "Autostart" permission and a per-app battery saver. Neither can be granted by the
 * app, so these helpers open the right screens and fall back to the app's details.
 */
object XiaomiSetup {
    private const val TAG = "XiaomiSetup"
    private const val NOTIFICATION_DEFAULT_APPLIED = "xiaomiNotificationDefaultApplied"

    val isXiaomi: Boolean by lazy {
        val brand = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
            !systemProperty("ro.miui.ui.version.name").isNullOrBlank()
    }

    /**
     * MIUI kills a VPN without a visible notification far more readily. Turn the
     * persistent notification on once by default; the user can still switch it off.
     */
    fun applyDefaults(context: Context) {
        if (!isXiaomi) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(NOTIFICATION_DEFAULT_APPLIED, false)) return
        prefs.edit()
            .putBoolean("persistentVpnNotification", true)
            .putBoolean(NOTIFICATION_DEFAULT_APPLIED, true)
            .apply()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun openBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        startFirst(context, request, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openAutostart(context: Context) {
        startFirst(
            context,
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            ),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            appDetails(context),
        )
    }

    fun openMiuiBatterySaver(context: Context) {
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        startFirst(
            context,
            Intent().setComponent(
                ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
            ).putExtra("package_name", context.packageName).putExtra("package_label", label),
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT),
            appDetails(context),
        )
    }

    fun openVpnSettings(context: Context) {
        startFirst(context, Intent(Settings.ACTION_VPN_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun startFirst(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "No activity for $intent")
            } catch (e: SecurityException) {
                Log.d(TAG, "Not allowed to open $intent")
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): String? = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name) as? String
    } catch (e: Exception) {
        null
    }
}
