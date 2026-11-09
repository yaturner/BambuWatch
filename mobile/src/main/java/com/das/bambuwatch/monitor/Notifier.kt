package com.das.bambuwatch.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.das.bambuwatch.MainActivity
import com.das.bambuwatch.shared.ConnectionState
import com.das.bambuwatch.shared.GcodeState
import com.das.bambuwatch.shared.PrintEvent
import com.das.bambuwatch.shared.PrinterState
import com.das.bambuwatch.shared.describe

/**
 * All notification channels and notifications.
 *
 * Alerts are left bridgeable so Wear OS mirrors them to the watch with no
 * watch code. The ongoing monitor notification is local-only: the watch gets
 * its own Ongoing Activity in milestone 6.
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_MONITOR, "Print monitor", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing print progress while monitoring is on"
                    setShowBadge(false)
                },
                NotificationChannel(CH_FINISHED, "Print finished", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A print completed"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 120, 200)
                },
                NotificationChannel(CH_PROBLEMS, "Print problems", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Failures, pauses, errors and HMS alerts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 600)
                },
                NotificationChannel(CH_PROGRESS, "Print milestones", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "First layer done"
                },
                NotificationChannel(CH_CONNECTION, "Connection", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Lost contact with the printer during a print"
                },
            ),
        )
    }

    fun ongoing(state: PrinterState): Notification {
        val builder = NotificationCompat.Builder(context, CH_MONITOR)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop monitoring", stopIntent())

        val job = state.jobName ?: "Print"
        val pct = state.percent ?: 0
        when {
            state.connection != ConnectionState.CONNECTED -> builder
                .setContentTitle(if (state.connection == ConnectionState.DISCONNECTED) "Not connected" else "Connecting to printer…")
                .setContentText(state.lastError ?: "Waiting for printer")
            state.gcodeState == GcodeState.PREPARE -> builder
                .setContentTitle("Preparing $job")
                .setProgress(0, 0, true)
            state.gcodeState == GcodeState.RUNNING || state.gcodeState == GcodeState.PAUSE -> builder
                .setContentTitle(if (state.gcodeState == GcodeState.PAUSE) "Paused: $job, $pct%" else "$job: $pct%")
                .setContentText(progressDetail(state))
                .setProgress(100, pct, false)
            state.gcodeState == GcodeState.FINISH -> builder
                .setContentTitle("Finished: $job")
                .setContentText("Watching for the next print")
            state.gcodeState == GcodeState.FAILED -> builder
                .setContentTitle("Stopped: $job")
                .setContentText("Watching for the next print")
            else -> builder
                .setContentTitle("Printer idle")
                .setContentText("Watching for print events")
        }
        return builder.build()
    }

    fun updateOngoing(state: PrinterState) = post(ID_ONGOING, ongoing(state))

    fun alert(event: PrintEvent) {
        val (title, text) = event.describe()
        val (channel, id) = when (event) {
            is PrintEvent.Finished -> CH_FINISHED to ID_STATE_ALERT
            is PrintEvent.Stopped, is PrintEvent.Paused -> CH_PROBLEMS to ID_STATE_ALERT
            is PrintEvent.ErrorRaised, is PrintEvent.HmsAlert -> CH_PROBLEMS to ID_ERROR_ALERT
            is PrintEvent.FirstLayerDone -> CH_PROGRESS to ID_PROGRESS_ALERT
        }
        post(
            id,
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(if (channel == CH_FINISHED) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(if (channel == CH_PROGRESS) NotificationCompat.CATEGORY_PROGRESS else NotificationCompat.CATEGORY_ALARM)
                .setPriority(if (channel == CH_PROGRESS) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build(),
        )
    }

    fun connectionLost(minutes: Long) = post(
        ID_CONNECTION,
        NotificationCompat.Builder(context, CH_CONNECTION)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Lost connection to printer")
            .setContentText("No updates for $minutes min during a print.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build(),
    )

    fun clearConnectionLost() = manager.cancel(ID_CONNECTION)

    private fun progressDetail(state: PrinterState): String = buildList {
        state.remainingMinutes?.let { add(if (it >= 60) "${it / 60}h ${it % 60}m left" else "${it}m left") }
        if (state.layer != null && state.totalLayers != null) add("Layer ${state.layer}/${state.totalLayers}")
    }.joinToString(" · ")

    private fun post(id: Int, notification: Notification) {
        if (!canPost()) return
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        context, 1,
        Intent(context, PrinterMonitorService::class.java).setAction(PrinterMonitorService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ID_ONGOING = 1
        private const val ID_STATE_ALERT = 2
        private const val ID_ERROR_ALERT = 3
        private const val ID_PROGRESS_ALERT = 4
        private const val ID_CONNECTION = 5

        private const val CH_MONITOR = "monitor"
        private const val CH_FINISHED = "finished"
        private const val CH_PROBLEMS = "problems"
        private const val CH_PROGRESS = "progress"
        private const val CH_CONNECTION = "connection"
    }
}
