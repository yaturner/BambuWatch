package com.das.bambuwatch.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.das.bambuwatch.BambuWatchApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts monitoring after a reboot or app update, if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as BambuWatchApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.configStore.restore()
                val enabled = app.configStore.monitoringEnabled.first()
                val config = app.configStore.config.first()
                if (enabled && config?.isComplete == true) PrinterMonitorService.start(app)
            } catch (e: Exception) {
                Log.w("BootReceiver", "Could not start monitoring", e)
            } finally {
                pending.finish()
            }
        }
    }
}
