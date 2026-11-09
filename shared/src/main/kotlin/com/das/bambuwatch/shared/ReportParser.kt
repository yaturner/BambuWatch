package com.das.bambuwatch.shared

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Folds a raw `device/<serial>/report` payload into the previous [PrinterState].
 *
 * Reports are mostly partial: after a `pushall` the printer sends a full
 * snapshot, then only the fields that changed. Any field missing from a
 * payload keeps its previous value. Numbers are read leniently because the
 * printer sometimes sends them as strings (e.g. `"mc_percent": "45"`).
 */
object ReportParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun merge(payload: String, previous: PrinterState, nowMillis: Long): PrinterState {
        val root = try {
            json.parseToJsonElement(payload) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return previous
        val print = root["print"] as? JsonObject ?: return previous

        val ams = print["ams"] as? JsonObject
        val trays = ams?.let(::parseTrays)

        return previous.copy(
            gcodeState = print.string("gcode_state")?.let(GcodeState::fromRaw) ?: previous.gcodeState,
            percent = print.int("mc_percent") ?: previous.percent,
            // Minutes on P1/X1 firmware (community-confirmed); verify against a known ETA.
            remainingMinutes = print.int("mc_remaining_time") ?: previous.remainingMinutes,
            layer = print.int("layer_num") ?: previous.layer,
            totalLayers = print.int("total_layer_num") ?: previous.totalLayers,
            jobName = print.string("subtask_name")?.ifBlank { null } ?: previous.jobName,
            speedLevel = print.int("spd_lvl") ?: previous.speedLevel,

            nozzleTemp = print.double("nozzle_temper") ?: previous.nozzleTemp,
            nozzleTarget = print.double("nozzle_target_temper") ?: previous.nozzleTarget,
            bedTemp = print.double("bed_temper") ?: previous.bedTemp,
            bedTarget = print.double("bed_target_temper") ?: previous.bedTarget,
            chamberTemp = print.double("chamber_temper") ?: previous.chamberTemp,

            lightOn = parseChamberLight(print) ?: previous.lightOn,

            amsTrays = trays ?: previous.amsTrays,
            activeTray = if (ams != null && ams.containsKey("tray_now")) {
                parseActiveTray(ams.string("tray_now"))
            } else {
                previous.activeTray
            },
            amsHumidityPercent = ams?.firstUnit()?.int("humidity_raw") ?: previous.amsHumidityPercent,

            printError = print.long("print_error") ?: previous.printError,
            hmsCount = (print["hms"] as? JsonArray)?.size ?: previous.hmsCount,

            updatedAt = nowMillis,
        )
    }

    /** Only returns a list when the payload carries the unit list itself;
     * a delta containing just `tray_now` must not wipe the known trays. */
    private fun parseTrays(ams: JsonObject): List<AmsTray>? {
        val units = ams["ams"] as? JsonArray ?: return null
        return units.flatMapIndexed { unitIndex, unitEl ->
            val unit = unitEl as? JsonObject ?: return@flatMapIndexed emptyList()
            val unitId = unit.int("id") ?: unitIndex
            val trays = unit["tray"] as? JsonArray ?: return@flatMapIndexed emptyList()
            trays.mapIndexedNotNull { trayIndex, trayEl ->
                val tray = trayEl as? JsonObject ?: return@mapIndexedNotNull null
                val trayId = tray.int("id") ?: trayIndex
                val type = tray.string("tray_type")?.ifBlank { null }
                AmsTray(
                    slot = unitId * 4 + trayId,
                    filamentType = type,
                    colorHex = normalizeColor(tray.string("tray_color")),
                    remainPercent = tray.int("remain")?.takeIf { it in 0..100 },
                    isEmpty = type == null,
                )
            }
        }
    }

    /** tray_now: "255" = nothing loaded, "254" = external spool, else unit*4+tray. */
    private fun parseActiveTray(raw: String?): Int? {
        val n = raw?.toIntOrNull() ?: return null
        return when {
            n == 255 -> null
            n == PrinterState.EXTERNAL_SPOOL -> n
            n in 0..15 -> n
            else -> null
        }
    }

    private fun parseChamberLight(print: JsonObject): Boolean? {
        val lights = print["lights_report"] as? JsonArray ?: return null
        val chamber = lights.filterIsInstance<JsonObject>()
            .firstOrNull { it.string("node") == "chamber_light" }
            ?: lights.firstOrNull() as? JsonObject
            ?: return null
        return when (chamber.string("mode")) {
            "on" -> true
            "off" -> false
            else -> null
        }
    }

    /** "RRGGBBAA" (no '#') -> "#RRGGBB". */
    internal fun normalizeColor(raw: String?): String? {
        val hex = raw?.removePrefix("#") ?: return null
        if (hex.length < 6 || !hex.take(6).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return "#" + hex.take(6).uppercase()
    }

    private fun JsonObject.firstUnit(): JsonObject? = (this["ams"] as? JsonArray)?.firstOrNull() as? JsonObject

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull

    private fun JsonObject.double(key: String): Double? = primitive(key)?.contentOrNull?.toDoubleOrNull()

    private fun JsonObject.int(key: String): Int? {
        val content = primitive(key)?.contentOrNull ?: return null
        return content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt()
    }

    private fun JsonObject.long(key: String): Long? {
        val p = primitive(key) ?: return null
        if (p.booleanOrNull != null) return null
        return p.contentOrNull?.toLongOrNull()
    }
}
