package com.das.bambuwatch.wear.complication

import com.das.bambuwatch.shared.prettyJobName
import com.das.bambuwatch.wear.Received
import com.das.bambuwatch.wear.tile.TileModel
import com.das.bambuwatch.wear.tile.TileTone

/**
 * What the complication shows, independent of the complication APIs (unit tested).
 * Built on [TileModel] so the tile and complication always agree, including the color.
 *
 * @property value 0..100 for the ranged (ring) type.
 * @property text short text, e.g. "67%" or "Done" (max ~7 chars to fit most faces).
 * @property title second short line, e.g. "1h12m".
 */
data class ComplicationContent(
    val value: Float,
    val text: String,
    val title: String?,
    val longText: String,
    val tone: TileTone,
    val hasData: Boolean,
) {
    companion object {
        fun from(received: Received?, now: Long): ComplicationContent {
            val m = TileModel.from(received, now)
            val s = received?.state
                ?: return ComplicationContent(0f, "--", null, "Bambu: no data from phone", m.tone, hasData = false)

            val printing = s.gcodeState in setOf("RUNNING", "PAUSE", "PREPARE")
            val pct = s.percent.coerceIn(0, 100)
            val text = when {
                m.tone == TileTone.ERROR && !printing -> "Error"
                s.gcodeState == "PREPARE" -> "Prep"
                printing -> "$pct%"
                s.gcodeState == "FINISH" -> "Done"
                else -> "Idle"
            }
            val title = when {
                m.tone == TileTone.ERROR && printing -> "Error"
                s.gcodeState == "PAUSE" -> "Paused"
                printing -> shortTime(s.remainingMin)
                else -> null
            }
            val value = when {
                printing || s.gcodeState == "FAILED" -> pct.toFloat()
                else -> 100f // idle/done: full ring, like the tile
            }
            val job = prettyJobName(s.jobName)
            val longText = listOfNotNull(
                m.label,
                job,
                if (printing) "$pct%" else null,
                if (printing && s.gcodeState != "PREPARE") compactTime(s.remainingMin) else null,
                m.secondary?.takeIf { m.tone == TileTone.ERROR || !s.connected },
            ).joinToString(" · ")
            return ComplicationContent(value, text, title, longText, m.tone, hasData = true)
        }

        /**
         * At most 3 characters ("4h", "45m") for the short-text title: some faces (e.g. MD307)
         * truncate anything longer ("4H1…"). Long text and the ongoing activity keep [compactTime].
         */
        fun shortTime(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"

        /** "1h12m" / "45m". */
        fun compactTime(minutes: Int): String =
            if (minutes >= 60) "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}m" else "${minutes}m"
    }
}
