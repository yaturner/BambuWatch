package com.das.bambuwatch.shared

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DemoPrinterTest {
    private fun cycleStates(offsetMs: Long = 0): List<PrinterState> =
        (0 until DemoPrinter.CYCLE_MS step DemoPrinter.TICK_MS).map { DemoPrinter.stateAt(it + offsetMs, 1_000 + it) }

    @Test
    fun oneCycleRaisesFirstLayerPauseAndFinishAlertsInOrder() {
        // Any tick phase must produce the same alerts.
        for (offset in listOf(0L, 700L, 1_300L, 1_999L)) {
            val events = cycleStates(offset).zipWithNext { a, b -> PrintEventDetector.detect(a, b) }.flatten()
            assertEquals("offset $offset: $events", 3, events.size)
            assertTrue(events[0] is PrintEvent.FirstLayerDone)
            val paused = events[1] as PrintEvent.Paused
            assertTrue(paused.needsAttention)
            assertTrue(events[2] is PrintEvent.Finished)
        }
    }

    @Test
    fun showsEveryRingColorState() {
        val states = cycleStates()
        assertTrue(states.any { it.gcodeState == GcodeState.PREPARE })
        assertTrue(states.any { it.gcodeState == GcodeState.RUNNING && !it.hasError })
        assertTrue(states.any { it.gcodeState == GcodeState.PAUSE && it.hasError })
        assertTrue(states.any { it.gcodeState == GcodeState.FINISH && it.percent == 100 })
        assertTrue(states.any { it.gcodeState == GcodeState.IDLE })
        assertTrue(states.all { it.connection == ConnectionState.CONNECTED && it.updatedAt > 0 })
    }

    @Test
    fun progressNeverGoesBackwardsWithinAPrint() {
        val printing = cycleStates().filter { it.isPrinting }
        printing.zipWithNext { a, b ->
            assertTrue((b.percent ?: 0) >= (a.percent ?: 0))
            assertTrue((b.layer ?: 0) >= (a.layer ?: 0))
            assertTrue((b.remainingMinutes ?: 0) <= (a.remainingMinutes ?: 0))
        }
        assertTrue(printing.all { (it.percent ?: 0) in 0..99 })
    }

    @Test
    fun repeatsEveryCycle() {
        val a = DemoPrinter.stateAt(33_000, 5)
        val b = DemoPrinter.stateAt(33_000 + 3 * DemoPrinter.CYCLE_MS, 5)
        assertEquals(a, b)
    }
}
