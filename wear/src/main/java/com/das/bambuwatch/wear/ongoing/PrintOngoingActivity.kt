package com.das.bambuwatch.wear.ongoing

import android.Manifest
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
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.das.bambuwatch.shared.prettyJobName
import com.das.bambuwatch.wear.MainActivity
import com.das.bambuwatch.wear.R
import com.das.bambuwatch.wear.Received
import com.das.bambuwatch.wear.complication.ComplicationContent
import com.das.bambuwatch.wear.tile.TileModel

/**
 * While a print is running, shows a Wear OS Ongoing Activity: an icon on the
 * watch face and an entry in Recents, with "67% · 1h12m" status, that opens the app.
 * It rides on a silent, watch-only ongoing notification and is removed when the print ends.
 */
object PrintOngoingActivity {
    private const val CHANNEL_ID = "print_progress"
    private const val NOTIFICATION_ID = 10

    fun update(context: Context, received: Received?) {
        val nm = NotificationManagerCompat.from(context)
        val s = received?.state
        val now = System.currentTimeMillis()
        val stale = received != null && received.sentAt > 0 && now - received.sentAt > TileModel.STALE_AFTER_MS
        val printing = s != null && s.gcodeState in setOf("PREPARE", "RUNNING", "PAUSE") && !stale

        if (!printing || !canPost(context)) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel(context)

        val c = ComplicationContent.from(received, now)
        val status = when (s!!.gcodeState) {
            "PREPARE" -> "Preparing"
            "PAUSE" -> "Paused · ${s.percent}%"
            else -> "${s.percent}% · ${ComplicationContent.compactTime(s.remainingMin)}"
        } + if (c.title == "Error") " · Error" else ""

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_printer)
            .setContentTitle(prettyJobName(s.jobName) ?: "Printing")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)

        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_printer)
            .setTouchIntent(open)
            .setStatus(Status.Builder().addTemplate("#status#").addPart("status", Status.TextPart(status)).build())
            .build()
            .apply(context)

        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission revoked.
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Print in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live print progress on the watch face"
                setShowBadge(false)
            },
        )
    }
}
