package com.das.bambuwatch.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.das.bambuwatch.shared.WatchState
import com.das.bambuwatch.wear.MainActivity
import com.das.bambuwatch.wear.R
import com.das.bambuwatch.wear.Received
import com.das.bambuwatch.wear.WearApp
import com.das.bambuwatch.wear.tile.PrinterTileService

/**
 * Watch-face complication: ring (RANGED_VALUE), short text and long text.
 * Pushed via [requestUpdate] whenever a state arrives, with a 5 min fallback
 * period in the manifest so staleness still shows up.
 */
class PrinterComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val received = (application as WearApp).cache.latest.value
        return build(this, request.complicationType, ComplicationContent.from(received, System.currentTimeMillis()))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val sample = WatchState(
            connected = true, gcodeState = "RUNNING", percent = 67, remainingMin = 72,
            layer = 142, totalLayers = 210, nozzleTemp = 220, nozzleTarget = 220,
            bedTemp = 60, bedTarget = 60, jobName = "Benchy", updatedAt = 0,
        )
        val now = System.currentTimeMillis()
        return build(this, type, ComplicationContent.from(Received(sample, now), now))
    }

    companion object {
        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, PrinterComplicationService::class.java))
                .requestUpdateAll()
        }

        private fun text(value: String): ComplicationText = PlainComplicationText.Builder(value).build()

        internal fun build(context: Context, type: ComplicationType, c: ComplicationContent): ComplicationData? {
            val description = text(c.longText)
            val icon = MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_printer)).build()
            val tap = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val color = PrinterTileService.toneColor(c.tone)

            return when (type) {
                ComplicationType.RANGED_VALUE ->
                    RangedValueComplicationData.Builder(c.value, 0f, 100f, description)
                        .setText(text(c.text))
                        .apply { c.title?.let { setTitle(text(it)) } }
                        .setMonochromaticImage(icon)
                        // Faces that honor color ramps draw the ring green/yellow/red like the tile.
                        .setColorRamp(ColorRamp(intArrayOf(color, color), false))
                        .setTapAction(tap)
                        .build()
                ComplicationType.SHORT_TEXT ->
                    ShortTextComplicationData.Builder(text(c.text), description)
                        .apply { c.title?.let { setTitle(text(it)) } }
                        .setMonochromaticImage(icon)
                        .setTapAction(tap)
                        .build()
                ComplicationType.LONG_TEXT ->
                    LongTextComplicationData.Builder(text(c.longText), description)
                        .setMonochromaticImage(icon)
                        .setTapAction(tap)
                        .build()
                else -> null
            }
        }
    }
}
