package com.das.bambuwatch.wear.complication

import com.das.bambuwatch.shared.WatchState
import com.das.bambuwatch.wear.Received
import com.das.bambuwatch.wear.tile.TileTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationContentTest {
    private val now = 10_000_000L
    private val running = WatchState(
        connected = true, gcodeState = "RUNNING", percent = 67, remainingMin = 72,
        layer = 142, totalLayers = 210, nozzleTemp = 220, nozzleTarget = 220,
        bedTemp = 60, bedTarget = 60, jobName = "Can_Organizer", updatedAt = now,
    )

    private fun content(s: WatchState) = ComplicationContent.from(Received(s, now - 1000), now)

    @Test
    fun `no data`() {
        val c = ComplicationContent.from(null, now)
        assertFalse(c.hasData)
        assertEquals("--", c.text)
    }

    @Test
    fun `printing shows percent, time left and a yellow partial ring`() {
        val c = content(running)
        assertEquals("67%", c.text)
        assertEquals("1h", c.title)
        assertEquals(67f, c.value, 0f)
        assertEquals(TileTone.PRINTING, c.tone)
        assertEquals("Printing · Can Organizer · 67% · 1h12m", c.longText)
    }

    @Test
    fun `idle and done show a full green ring`() {
        content(running.copy(gcodeState = "FINISH", percent = 100)).let {
            assertEquals("Done", it.text); assertNull(it.title); assertEquals(100f, it.value, 0f); assertEquals(TileTone.IDLE, it.tone)
        }
        content(running.copy(gcodeState = "IDLE", percent = 0)).let {
            assertEquals("Idle", it.text); assertEquals(100f, it.value, 0f); assertEquals(TileTone.IDLE, it.tone)
        }
    }

    @Test
    fun `errors are red and say so`() {
        content(running.copy(errorCode = 5)).let {
            assertEquals("67%", it.text); assertEquals("Error", it.title); assertEquals(TileTone.ERROR, it.tone)
            assertTrue(it.longText.endsWith("Error 0x00000005"))
        }
        content(running.copy(gcodeState = "FAILED", percent = 30)).let {
            assertEquals("Error", it.text); assertEquals(30f, it.value, 0f); assertEquals(TileTone.ERROR, it.tone)
        }
    }

    @Test
    fun `paused and preparing`() {
        assertEquals("Paused", content(running.copy(gcodeState = "PAUSE")).title)
        assertEquals("Prep", content(running.copy(gcodeState = "PREPARE", percent = 0)).text)
    }

    @Test
    fun `short time fits in 3 characters`() {
        assertEquals("4h", ComplicationContent.shortTime(253))
        assertEquals("45m", ComplicationContent.shortTime(45))
        assertEquals("9m", ComplicationContent.shortTime(9))
        for (m in listOf(0, 9, 59, 60, 599)) assert(ComplicationContent.shortTime(m).length <= 3)
    }

    @Test
    fun `compact time`() {
        assertEquals("45m", ComplicationContent.compactTime(45))
        assertEquals("2h05m", ComplicationContent.compactTime(125))
    }
}
