package com.das.bambuwatch.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.das.bambuwatch.BambuWatchApp
import com.das.bambuwatch.shared.ConnectionState
import com.das.bambuwatch.shared.GcodeState
import com.das.bambuwatch.shared.PrintEventDetector
import com.das.bambuwatch.shared.PrinterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the printer connection alive while the app is in the background and
 * turns state changes into notifications.
 *
 * Runs whenever monitoring is enabled, including while the printer is idle.
 * It can't stop when idle and restart itself when a print begins, because
 * Android 12+ won't let a background app start a foreground service from a
 * timer or WorkManager job. An idle MQTT connection costs little.
 */
class PrinterMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app get() = application as BambuWatchApp
    private lateinit var notifier: Notifier
    private var monitoring = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = Notifier(this).also { it.ensureChannels() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must run on every start command, within 5 s of startForegroundService.
        ServiceCompat.startForeground(
            this,
            Notifier.ID_ONGOING,
            notifier.ongoing(app.printerRepository.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )

        if (intent?.action == ACTION_STOP) {
            scope.launch {
                app.configStore.setMonitoringEnabled(false)
                stopSelfCompat()
            }
            return START_NOT_STICKY
        }

        if (!monitoring) {
            monitoring = true
            scope.launch { monitor() }
        }
        return START_STICKY
    }

    private suspend fun monitor() {
        app.configStore.restore()
        val config = app.configStore.config.first()
        if (config == null || !config.isComplete) {
            Log.i(TAG, "No printer configured; stopping")
            stopSelfCompat()
            return
        }
        val repo = app.printerRepository
        if (repo.state.value.connection == ConnectionState.DISCONNECTED) repo.start(config)

        // Ongoing notification: only re-post when something visible changes.
        scope.launch {
            repo.state
                .map { OngoingKey(it) }
                .distinctUntilChanged()
                .collect { notifier.updateOngoing(repo.state.value) }
        }

        // Event alerts.
        scope.launch {
            var prev = repo.state.value
            repo.state.collect { curr ->
                PrintEventDetector.detect(prev, curr).forEach(notifier::alert)
                prev = curr
            }
        }

        // Connection lost for a while during a print.
        scope.launch {
            repo.state
                .map { it.connection != ConnectionState.CONNECTED && it.isPrinting }
                .distinctUntilChanged()
                .collectLatest { lost ->
                    if (lost) {
                        delay(CONNECTION_LOST_GRACE_MS)
                        notifier.connectionLost(CONNECTION_LOST_GRACE_MS / 60_000)
                    } else {
                        notifier.clearConnectionLost()
                    }
                }
        }
    }

    private fun stopSelfCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        app.printerRepository.stop()
        super.onDestroy()
    }

    /** The fields shown in the ongoing notification. */
    private data class OngoingKey(
        val connection: ConnectionState,
        val gcodeState: GcodeState,
        val percent: Int?,
        val remainingMinutes: Int?,
        val layer: Int?,
        val jobName: String?,
        val lastError: String?,
    ) {
        constructor(s: PrinterState) : this(
            s.connection, s.gcodeState, s.percent, s.remainingMinutes, s.layer, s.jobName, s.lastError,
        )
    }

    companion object {
        private const val TAG = "PrinterMonitorService"
        const val ACTION_STOP = "com.das.bambuwatch.action.STOP_MONITORING"
        private const val CONNECTION_LOST_GRACE_MS = 2 * 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PrinterMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrinterMonitorService::class.java))
        }
    }
}
