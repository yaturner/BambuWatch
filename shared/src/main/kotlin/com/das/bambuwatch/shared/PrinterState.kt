package com.das.bambuwatch.shared

/** Connection to the printer's MQTT broker, as seen by the phone. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

/** The printer's `gcode_state`. PREPARE is kept separate from RUNNING so the
 * watch can show "Preparing…" (heating, bed leveling) before layer 1. */
enum class GcodeState {
    IDLE, PREPARE, RUNNING, PAUSE, FINISH, FAILED, UNKNOWN;

    companion object {
        fun fromRaw(raw: String): GcodeState = when (raw.uppercase()) {
            "IDLE" -> IDLE
            "PREPARE", "SLICING" -> PREPARE
            "RUNNING" -> RUNNING
            "PAUSE" -> PAUSE
            "FINISH" -> FINISH
            "FAILED" -> FAILED
            else -> UNKNOWN
        }
    }
}

data class AmsTray(
    /** Global slot index: unit * 4 + tray (0..15). */
    val slot: Int,
    val filamentType: String? = null,
    /** "#RRGGBB", alpha stripped. */
    val colorHex: String? = null,
    /** 0-100, or null when the AMS can't tell (non-RFID spool). */
    val remainPercent: Int? = null,
    val isEmpty: Boolean = true,
)

/**
 * The phone's merged view of the printer. MQTT reports are mostly partial
 * deltas, so this is only ever produced by [ReportParser.merge] folding a
 * payload into the previous state.
 */
data class PrinterState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val lastError: String? = null,

    val gcodeState: GcodeState = GcodeState.UNKNOWN,
    val percent: Int? = null,
    val remainingMinutes: Int? = null,
    val layer: Int? = null,
    val totalLayers: Int? = null,
    val jobName: String? = null,
    /** 1=Silent, 2=Standard, 3=Sport, 4=Ludicrous. */
    val speedLevel: Int? = null,

    val nozzleTemp: Double? = null,
    val nozzleTarget: Double? = null,
    val bedTemp: Double? = null,
    val bedTarget: Double? = null,
    val chamberTemp: Double? = null,

    val lightOn: Boolean? = null,

    val amsTrays: List<AmsTray> = emptyList(),
    /** Global slot index of the loaded tray, [EXTERNAL_SPOOL], or null if none. */
    val activeTray: Int? = null,
    val amsHumidityPercent: Int? = null,

    /** `print_error`; 0 means no error. */
    val printError: Long? = null,
    /** Number of active HMS (health management) alerts. */
    val hmsCount: Int = 0,

    /** Epoch millis of the last report merged in; 0 if none yet. */
    val updatedAt: Long = 0,
) {
    val isPrinting: Boolean
        get() = gcodeState == GcodeState.PREPARE ||
            gcodeState == GcodeState.RUNNING ||
            gcodeState == GcodeState.PAUSE

    val hasError: Boolean
        get() = gcodeState == GcodeState.FAILED || (printError ?: 0L) != 0L || hmsCount > 0

    val activeAmsTray: AmsTray?
        get() = activeTray?.let { slot -> amsTrays.firstOrNull { it.slot == slot } }

    companion object {
        const val EXTERNAL_SPOOL = 254
    }
}

/** Bambu job names use underscores for spaces ("Can_Organizer_-_No_Supports"); show them readably. */
fun prettyJobName(raw: String?): String? =
    raw?.replace('_', ' ')?.replace(Regex("\\s+"), " ")?.trim()?.ifBlank { null }
