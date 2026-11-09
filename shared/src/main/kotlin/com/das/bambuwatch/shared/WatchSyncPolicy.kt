package com.das.bambuwatch.shared

/**
 * Decides when the phone pushes a new [WatchState] to the watch. Every Data
 * Layer write wakes the watch radio, so:
 *
 * - **Immediately** when something you'd want to see right away changes:
 *   connection, print state, job, errors, HMS alerts, light, active filament.
 * - **At most every [PROGRESS_INTERVAL_MS]** for progress, time left, layer and temperatures.
 * - **Every [HEARTBEAT_INTERVAL_MS]** even with no changes, so the watch can tell
 *   the phone is still alive (the payload's sentAt changes).
 */
object WatchSyncPolicy {
    const val PROGRESS_INTERVAL_MS = 30_000L
    const val HEARTBEAT_INTERVAL_MS = 5 * 60_000L

    fun shouldSend(last: WatchState?, lastSentAt: Long, candidate: WatchState, now: Long): Boolean {
        if (last == null) return true
        if (importantChanged(last, candidate)) return true
        val elapsed = now - lastSentAt
        return if (candidate.copy(updatedAt = 0) != last.copy(updatedAt = 0)) {
            elapsed >= PROGRESS_INTERVAL_MS
        } else {
            elapsed >= HEARTBEAT_INTERVAL_MS
        }
    }

    private fun importantChanged(a: WatchState, b: WatchState): Boolean =
        a.connected != b.connected ||
            a.gcodeState != b.gcodeState ||
            a.jobName != b.jobName ||
            a.errorCode != b.errorCode ||
            a.hmsCount != b.hmsCount ||
            a.lightOn != b.lightOn ||
            a.activeAmsSlot != b.activeAmsSlot ||
            a.filamentColor != b.filamentColor
}
