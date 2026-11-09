package com.das.bambuwatch.shared

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * JSON payloads published to `device/<serial>/request`.
 *
 * Note: on firmware with Bambu's authorization layer, control commands
 * (everything except [pushAll]) only take effect in LAN-only + Developer Mode.
 */
object PrinterCommands {
    fun pushAll(seq: Long): String = buildJsonObject {
        putJsonObject("pushing") {
            put("sequence_id", seq.toString())
            put("command", "pushall")
        }
    }.toString()

    fun pause(seq: Long): String = printCommand("pause", seq)
    fun resume(seq: Long): String = printCommand("resume", seq)
    fun stop(seq: Long): String = printCommand("stop", seq)

    fun chamberLight(on: Boolean, seq: Long): String = buildJsonObject {
        putJsonObject("system") {
            put("sequence_id", seq.toString())
            put("command", "ledctrl")
            put("led_node", "chamber_light")
            put("led_mode", if (on) "on" else "off")
            put("led_on_time", 500)
            put("led_off_time", 500)
            put("loop_times", 0)
            put("interval_time", 0)
        }
    }.toString()

    private fun printCommand(command: String, seq: Long): String = buildJsonObject {
        putJsonObject("print") {
            put("sequence_id", seq.toString())
            put("command", command)
        }
    }.toString()
}

/** Shared phone/watch Data Layer contract (used from milestone 4). */
object DataLayerPaths {
    const val STATE = "/printer/state"
    const val STATE_KEY = "state"
    const val COMMAND = "/printer/cmd"
}

/** Command names the watch sends on [DataLayerPaths.COMMAND]. */
enum class WatchCommand { PAUSE, RESUME, STOP, LIGHT_ON, LIGHT_OFF }
