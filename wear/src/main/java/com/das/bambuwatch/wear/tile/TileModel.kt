package com.das.bambuwatch.wear.tile

import com.das.bambuwatch.shared.WatchState
import com.das.bambuwatch.shared.formatErrorCode
import com.das.bambuwatch.shared.prettyJobName
import com.das.bambuwatch.wear.Received

/** Ring and label color: green when idle, yellow while printing, red on any error. */
enum class TileTone { IDLE, PRINTING, ERROR }

/**
 * What the tile shows, decided without any tile APIs so it can be unit tested.
 *
 * @property progress 0..1 to draw the edge ring, or null for a layout without a ring.
 */
data class TileModel(
    val label: String,
    val headline: String,
    val detail: String?,
    val secondary: String?,
    val progress: Float?,
    val tone: TileTone,
) {
    companion object {
        /** No message from the phone for this long means the phone or app is gone (heartbeat is 5 min). */
        const val STALE_AFTER_MS = 12 * 60_000L

        fun from(received: Received?, now: Long): TileModel {
            if (received == null) {
                return TileModel("Bambu", "No data", "Open Bambu Watch on your phone", null, null, TileTone.IDLE)
            }
            val s = received.state
            val stale = received.sentAt > 0 && now - received.sentAt > STALE_AFTER_MS
            val pct = s.percent.coerceIn(0, 100)
            val job = prettyJobName(s.jobName)

            val base = when (s.gcodeState) {
                "RUNNING" -> TileModel("Printing", "$pct%", job, progressLine(s), pct / 100f, TileTone.PRINTING)
                "PREPARE" -> TileModel("Preparing", "Heating", job, "Nozzle ${s.nozzleTemp}° · Bed ${s.bedTemp}°", 0f, TileTone.PRINTING)
                "PAUSE" -> TileModel("Paused", "$pct%", job, progressLine(s), pct / 100f, TileTone.PRINTING)
                "FINISH" -> TileModel("Done", "100%", job, "Clear the plate", 1f, TileTone.IDLE)
                "FAILED" -> TileModel("Stopped", "$pct%", job, null, pct / 100f, TileTone.ERROR)
                else -> TileModel("Idle", "Ready", "Nozzle ${s.nozzleTemp}° · Bed ${s.bedTemp}°", null, 1f, TileTone.IDLE)
            }

            // Warnings replace the bottom line, most important first. Errors turn the ring red;
            // connection problems only change the text, since the last known state is still shown.
            return when {
                stale -> base.copy(secondary = "Phone not updating")
                !s.connected -> base.copy(secondary = "Printer offline")
                s.errorCode != null -> base.copy(secondary = "Error ${formatErrorCode(s.errorCode!!)}", tone = TileTone.ERROR)
                s.hmsCount > 0 && s.gcodeState != "FINISH" ->
                    base.copy(secondary = if (s.hmsCount == 1) "1 alert" else "${s.hmsCount} alerts", tone = TileTone.ERROR)
                else -> base
            }
        }

        private fun progressLine(s: WatchState): String {
            val time = if (s.remainingMin >= 60) "${s.remainingMin / 60}h ${s.remainingMin % 60}m" else "${s.remainingMin}m"
            return if (s.totalLayers > 0) "$time · L${s.layer}/${s.totalLayers}" else "$time left"
        }
    }
}
