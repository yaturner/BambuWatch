package com.das.bambuwatch.shared

/** Something that happened on the printer and is worth a notification. */
sealed interface PrintEvent {
    data class Finished(val jobName: String?) : PrintEvent
    /** Bambu reports FAILED both for real failures and for prints stopped by the user. */
    data class Stopped(val jobName: String?, val errorCode: Long?) : PrintEvent
    data class Paused(val jobName: String?, val errorCode: Long?, val needsAttention: Boolean) : PrintEvent
    data class ErrorRaised(val errorCode: Long) : PrintEvent
    data class HmsAlert(val count: Int) : PrintEvent
    data class FirstLayerDone(val jobName: String?) : PrintEvent
}

/**
 * Compares two consecutive [PrinterState]s and returns the events between them.
 *
 * The first real state after (re)starting is only a baseline: with no known
 * previous state, a printer already sitting in FINISH must not announce
 * "print finished" every time the phone reconnects.
 */
object PrintEventDetector {
    private val ACTIVE = setOf(GcodeState.PREPARE, GcodeState.RUNNING, GcodeState.PAUSE)

    fun detect(prev: PrinterState, curr: PrinterState): List<PrintEvent> {
        if (prev.updatedAt == 0L || prev.gcodeState == GcodeState.UNKNOWN) return emptyList()
        if (curr.gcodeState == GcodeState.UNKNOWN) return emptyList()

        val events = mutableListOf<PrintEvent>()
        val job = curr.jobName ?: prev.jobName
        val error = curr.printError?.takeIf { it != 0L }

        if (curr.gcodeState != prev.gcodeState) {
            when (curr.gcodeState) {
                GcodeState.FINISH ->
                    if (prev.gcodeState in ACTIVE) events += PrintEvent.Finished(job)
                GcodeState.FAILED ->
                    events += PrintEvent.Stopped(job, error)
                GcodeState.PAUSE ->
                    if (prev.gcodeState == GcodeState.RUNNING || prev.gcodeState == GcodeState.PREPARE) {
                        events += PrintEvent.Paused(job, error, needsAttention = error != null || curr.hmsCount > 0)
                    }
                else -> Unit
            }
        }

        // Errors and HMS alerts are already reported by a Paused/Stopped event in the same step.
        val coveredByStateEvent = events.any { it is PrintEvent.Paused || it is PrintEvent.Stopped }
        if (!coveredByStateEvent) {
            if (error != null && error != prev.printError) events += PrintEvent.ErrorRaised(error)
            else if (curr.hmsCount > prev.hmsCount) events += PrintEvent.HmsAlert(curr.hmsCount)
        }

        if (curr.gcodeState == GcodeState.RUNNING && prev.layer == 1 && (curr.layer ?: 0) >= 2) {
            events += PrintEvent.FirstLayerDone(job)
        }
        return events
    }
}

/** Title and body text for an event, shared by phone notifications and (later) the watch. */
data class EventText(val title: String, val text: String)

fun formatErrorCode(code: Long): String = "0x" + code.toString(16).uppercase().padStart(8, '0')

fun PrintEvent.describe(): EventText {
    fun name(job: String?) = job ?: "The print"
    fun errorSuffix(code: Long?) = code?.let { " (error ${formatErrorCode(it)})" }.orEmpty()
    return when (this) {
        is PrintEvent.Finished -> EventText("Print finished", "${name(jobName)} is done. Clear the plate.")
        is PrintEvent.Stopped -> EventText(
            if (errorCode != null) "Print failed" else "Print stopped",
            "${name(jobName)} stopped${errorSuffix(errorCode)}.",
        )
        is PrintEvent.Paused -> EventText(
            if (needsAttention) "Printer needs attention" else "Print paused",
            "${name(jobName)} is paused${errorSuffix(errorCode)}.",
        )
        is PrintEvent.ErrorRaised -> EventText("Printer error", "Error ${formatErrorCode(errorCode)}. Check the printer.")
        is PrintEvent.HmsAlert -> EventText(
            "Printer alert",
            if (count == 1) "1 active HMS alert. Check the printer." else "$count active HMS alerts. Check the printer.",
        )
        is PrintEvent.FirstLayerDone -> EventText("First layer done", "Check how ${name(jobName)}'s first layer went.")
    }
}
