package com.das.bambuwatch.wear.tile

import com.das.bambuwatch.shared.WatchState
import com.das.bambuwatch.wear.Received
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileModelTest {
    private val now = 10_000_000L
    private val running = WatchState(
        connected = true, gcodeState = "RUNNING", percent = 67, remainingMin = 72,
        layer = 142, totalLayers = 210, nozzleTemp = 220, nozzleTarget = 220,
        bedTemp = 60, bedTarget = 60, jobName = "Benchy", updatedAt = now,
    )

    private fun model(s: WatchState, sentAgoMs: Long = 1_000) = TileModel.from(Received(s, now - sentAgoMs), now)

    @Test
    fun `no data asks to open the phone app`() {
        val m = TileModel.from(null, now)
        assertEquals("No data", m.headline)
        assertNull(m.progress)
    }

    @Test
    fun `printing shows percent, job, time and layer with a ring`() {
        val m = model(running)
        assertEquals("Printing", m.label)
        assertEquals("67%", m.headline)
        assertEquals("Benchy", m.detail)
        assertEquals("1h 12m · L142/210", m.secondary)
        assertEquals(0.67f, m.progress!!, 0.001f)
        assertEquals(TileTone.PRINTING, m.tone)
    }

    @Test
    fun `short remaining time and missing layers`() {
        assertEquals("5m left", model(running.copy(remainingMin = 5, totalLayers = 0)).secondary)
    }

    @Test
    fun `ring is green when idle, yellow while printing, red on error`() {
        assertEquals(TileTone.PRINTING, model(running.copy(gcodeState = "PREPARE")).tone)
        assertEquals(TileTone.PRINTING, model(running.copy(gcodeState = "PAUSE")).tone)
        model(running.copy(gcodeState = "FINISH", percent = 100)).let {
            assertEquals("Done", it.label); assertEquals(TileTone.IDLE, it.tone); assertEquals(1f, it.progress!!, 0f)
        }
        assertEquals(TileTone.ERROR, model(running.copy(gcodeState = "FAILED")).tone)
        model(running.copy(gcodeState = "IDLE")).let {
            assertEquals("Ready", it.headline); assertEquals(TileTone.IDLE, it.tone)
            assertEquals("idle shows a full green ring", 1f, it.progress!!, 0f)
        }
        assertEquals(TileTone.ERROR, model(running.copy(gcodeState = "PAUSE", errorCode = 7)).tone)
        assertEquals(TileTone.ERROR, model(running.copy(gcodeState = "IDLE", hmsCount = 1)).tone)
    }

    @Test
    fun `warnings replace the bottom line by priority`() {
        assertEquals("Error 0x0500400E", model(running.copy(errorCode = 0x0500400EL)).secondary)
        assertEquals(TileTone.ERROR, model(running.copy(errorCode = 1)).tone)
        assertEquals("2 alerts", model(running.copy(hmsCount = 2)).secondary)
        assertEquals("Printer offline", model(running.copy(connected = false, errorCode = 1)).secondary)
        model(running, sentAgoMs = TileModel.STALE_AFTER_MS + 1).let {
            assertEquals("Phone not updating", it.secondary)
            assertEquals("connection problems keep the state color", TileTone.PRINTING, it.tone)
        }
    }

    @Test
    fun `hms alerts are not shown once the print is finished`() {
        assertEquals("Clear the plate", model(running.copy(gcodeState = "FINISH", hmsCount = 1)).secondary)
    }
}
