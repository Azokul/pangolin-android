package net.pangolin.Pangolin.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.pangolin.Pangolin.PangolinApplication

/**
 * Remembers that the user wants the VPN up, so the app can bring it back after the
 * system (or an aggressive OEM battery manager such as MIUI's) kills the process.
 *
 * The flag is set when the user connects and cleared only when the user disconnects,
 * signs out or another VPN takes over. A periodic inexact alarm, boot and app updates
 * recreate the process; [net.pangolin.Pangolin.PangolinRuntime] then reconnects.
 */
object KeepAlive {
    private const val TAG = "KeepAlive"
    const val WANTED_PREF_KEY = "keepAliveWanted"
    const val ENABLED_PREF_KEY = "autoReconnect"
    const val ACTION_TICK = "net.pangolin.Pangolin.action.KEEP_ALIVE_TICK"
    private const val INTERVAL_MS = AlarmManager.INTERVAL_FIFTEEN_MINUTES

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED_PREF_KEY, true)

    fun isWanted(context: Context): Boolean = prefs(context).getBoolean(WANTED_PREF_KEY, false)

    /** True when the app should keep (re)connecting on its own. */
    fun isActive(context: Context): Boolean = isActive(isEnabled(context), isWanted(context))

    fun isActive(enabled: Boolean, wanted: Boolean): Boolean = enabled && wanted

    fun setWanted(context: Context, wanted: Boolean) {
        if (isWanted(context) != wanted) {
            Log.i(TAG, "User wants VPN up: $wanted")
            prefs(context).edit().putBoolean(WANTED_PREF_KEY, wanted).apply()
        }
        syncAlarm(context)
    }

    /** Arms the periodic check while keep-alive is active, cancels it otherwise. */
    fun syncAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, KeepAliveReceiver::class.java).setAction(ACTION_TICK)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (isActive(context)) {
            // Inexact: no exact-alarm permission needed, and Doze batches it into
            // maintenance windows, which is fine for a safety net.
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pending,
            )
        } else {
            alarmManager.cancel(pending)
        }
    }
}

/** Boot, app update and periodic tick: wake the runtime so it can reconnect. */
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PangolinApplication ?: return
        Log.d("KeepAlive", "Received ${intent.action}")
        KeepAlive.syncAlarm(app)
        if (!KeepAlive.isActive(app)) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Keep the process alive while the tunnel starts; onReceive has ~10 s.
                withTimeoutOrNull(8_000) { app.runtime.keepAliveTick() }
            } finally {
                pending.finish()
            }
        }
    }
}
