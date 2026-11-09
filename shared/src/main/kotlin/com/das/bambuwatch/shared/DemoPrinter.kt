package com.das.bambuwatch.shared

import kotlin.math.roundToInt

/**
 * A simulated printer for demo mode (Play reviewers and anyone without a Bambu printer).
 *
 * [stateAt] is a pure function of the time since the demo started, so the app just samples it
 * on a timer and feeds the result through the same paths as real MQTT state: notifications,
 * watch sync, tile and complication. One cycle shows every visual state, then repeats:
 *
 * | Phase          | Length | Shows                                                  |
 * |----------------|--------|--------------------------------------------------------|
 * | Preparing      | 20 s   | heating, yellow ring at 0%                             |
 * | Printing       | 90 s   | 0 → 50%, "first layer done" alert                      |
 * | Paused (alert) | 20 s   | red ring, "printer needs attention" alert (HMS)        |
 * | Printing       | 90 s   | 50 → 100%                                              |
 * | Finished       | 40 s   | green ring, "print finished" alert                     |
 * | Idle           | 20 s   | green ring, no job                                     |
 */
object DemoPrinter {
    /** Stored in place of a real config while demo mode is on; never used to connect. */
    val CONFIG = PrinterConfig(ip = "demo", serial = "DEMO", accessCode = "demo")

    const val JOB_NAME = "Demo_-_Cable_Organizer"
    const val TOTAL_LAYERS = 120
    /** Pretend print length, so "time left" looks realistic though the demo runs in minutes. */
    private const val PRETEND_PRINT_MINUTES = 95

    private const val PREPARE_MS = 20_000L
    private const val PRINT_HALF_MS = 90_000L
    private const val PAUSE_MS = 20_000L
    private const val FINISH_MS = 40_000L
    private const val IDLE_MS = 20_000L
    const val CYCLE_MS = PREPARE_MS + PRINT_HALF_MS + PAUSE_MS + PRINT_HALF_MS + FINISH_MS + IDLE_MS

    /** Sampling interval used by the phone app. */
    const val TICK_MS = 2_000L

    private const val AMBIENT = 26.0
    private const val NOZZLE_TARGET = 220.0
    private const val BED_TARGET = 65.0
    private const val ACTIVE_TRAY = 1
    private const val FIRST_LAYER_FRACTION = 0.06

    private val TRAYS = listOf(
        AmsTray(slot = 0, filamentType = "PLA", colorHex = "#F2F2F2", remainPercent = 82, isEmpty = false),
        AmsTray(slot = 1, filamentType = "PLA", colorHex = "#1F6FEB", remainPercent = 64, isEmpty = false),
        AmsTray(slot = 2, filamentType = "PETG", colorHex = "#E5484D", remainPercent = 37, isEmpty = false),
        AmsTray(slot = 3, filamentType = "PLA", colorHex = "#161616", remainPercent = 91, isEmpty = false),
    )

    fun stateAt(elapsedMs: Long, nowMs: Long): PrinterState {
        val t = Math.floorMod(elapsedMs, CYCLE_MS)
        val base = PrinterState(
            connection = ConnectionState.CONNECTED,
            amsTrays = TRAYS,
            amsHumidityPercent = 22,
            chamberTemp = 31.0,
            speedLevel = 2,
            lightOn = true,
            printError = 0,
            updatedAt = nowMs,
        )
        val printStart = PREPARE_MS
        val pauseStart = printStart + PRINT_HALF_MS
        val resumeAt = pauseStart + PAUSE_MS
        val finishAt = resumeAt + PRINT_HALF_MS
        val idleAt = finishAt + FINISH_MS
        return when {
            t < printStart -> {
                val f = t.toDouble() / PREPARE_MS
                base.copy(
                    gcodeState = GcodeState.PREPARE,
                    jobName = JOB_NAME,
                    percent = 0,
                    remainingMinutes = PRETEND_PRINT_MINUTES,
                    layer = 0,
                    totalLayers = TOTAL_LAYERS,
                    nozzleTemp = lerp(AMBIENT, NOZZLE_TARGET, f),
                    nozzleTarget = NOZZLE_TARGET,
                    bedTemp = lerp(AMBIENT, BED_TARGET, f),
                    bedTarget = BED_TARGET,
                    activeTray = ACTIVE_TRAY,
                )
            }
            t < pauseStart -> printing(base, 0.5 * (t - printStart) / PRINT_HALF_MS)
            t < resumeAt -> printing(base, 0.5).copy(gcodeState = GcodeState.PAUSE, hmsCount = 1)
            t < finishAt -> printing(base, 0.5 + 0.5 * (t - resumeAt) / PRINT_HALF_MS)
            t < idleAt -> {
                val f = (t - finishAt).toDouble() / FINISH_MS
                base.copy(
                    gcodeState = GcodeState.FINISH,
                    jobName = JOB_NAME,
                    percent = 100,
                    remainingMinutes = 0,
                    layer = TOTAL_LAYERS,
                    totalLayers = TOTAL_LAYERS,
                    nozzleTemp = lerp(NOZZLE_TARGET, 60.0, f),
                    nozzleTarget = 0.0,
                    bedTemp = lerp(BED_TARGET, 45.0, f),
                    bedTarget = 0.0,
                )
            }
            else -> base.copy(
                gcodeState = GcodeState.IDLE,
                nozzleTemp = 40.0,
                nozzleTarget = 0.0,
                bedTemp = 38.0,
                bedTarget = 0.0,
            )
        }
    }

    private fun printing(base: PrinterState, fraction: Double): PrinterState {
        val f = fraction.coerceIn(0.0, 1.0)
        return base.copy(
            gcodeState = GcodeState.RUNNING,
            jobName = JOB_NAME,
            percent = (f * 100).toInt().coerceAtMost(99),
            remainingMinutes = ((1 - f) * PRETEND_PRINT_MINUTES).roundToInt().coerceAtLeast(1),
            // Hold layer 1 for a few ticks so the "first layer done" alert always fires.
            layer = if (f < FIRST_LAYER_FRACTION) 1
            else (2 + (f - FIRST_LAYER_FRACTION) / (1 - FIRST_LAYER_FRACTION) * (TOTAL_LAYERS - 2)).toInt(),
            totalLayers = TOTAL_LAYERS,
            nozzleTemp = NOZZLE_TARGET,
            nozzleTarget = NOZZLE_TARGET,
            bedTemp = BED_TARGET,
            bedTarget = BED_TARGET,
            activeTray = ACTIVE_TRAY,
        )
    }

    private fun lerp(a: Double, b: Double, f: Double) = a + (b - a) * f.coerceIn(0.0, 1.0)
}
