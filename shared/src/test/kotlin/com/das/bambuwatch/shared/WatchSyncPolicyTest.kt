package com.das.bambuwatch.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSyncPolicyTest {
    private val base = WatchState(
        connected = true, gcodeState = "RUNNING", percent = 40, remainingMin = 60,
        layer = 100, totalLayers = 200, nozzleTemp = 220, nozzleTarget = 220,
        bedTemp = 60, bedTarget = 60, jobName = "Benchy", updatedAt = 1_000,
    )
    private val t0 = 1_000_000L

    private fun send(candidate: WatchState, elapsedMs: Long, last: WatchState? = base) =
        WatchSyncPolicy.shouldSend(last, t0, candidate, t0 + elapsedMs)

    @Test
    fun `first state is always sent`() {
        assertTrue(send(base, 0, last = null))
    }

    @Test
    fun `important changes are sent immediately`() {
        assertTrue(send(base.copy(gcodeState = "PAUSE"), 1))
        assertTrue(send(base.copy(connected = false), 1))
        assertTrue(send(base.copy(errorCode = 42), 1))
        assertTrue(send(base.copy(hmsCount = 1), 1))
        assertTrue(send(base.copy(jobName = "Other"), 1))
        assertTrue(send(base.copy(lightOn = true), 1))
        assertTrue(send(base.copy(activeAmsSlot = 2), 1))
    }

    @Test
    fun `progress changes are throttled to the progress interval`() {
        val progressed = base.copy(percent = 41, nozzleTemp = 219, updatedAt = 2_000)
        assertFalse(send(progressed, WatchSyncPolicy.PROGRESS_INTERVAL_MS - 1))
        assertTrue(send(progressed, WatchSyncPolicy.PROGRESS_INTERVAL_MS))
    }

    @Test
    fun `timestamp-only changes wait for the heartbeat`() {
        val same = base.copy(updatedAt = 9_999)
        assertFalse(send(same, WatchSyncPolicy.PROGRESS_INTERVAL_MS))
        assertFalse(send(same, WatchSyncPolicy.HEARTBEAT_INTERVAL_MS - 1))
        assertTrue(send(same, WatchSyncPolicy.HEARTBEAT_INTERVAL_MS))
    }
}
