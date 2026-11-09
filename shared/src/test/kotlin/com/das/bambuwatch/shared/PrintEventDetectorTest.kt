package com.das.bambuwatch.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintEventDetectorTest {
    private val running = PrinterState(
        gcodeState = GcodeState.RUNNING, jobName = "Benchy", layer = 50,
        printError = 0, hmsCount = 0, updatedAt = 1,
    )

    private fun detect(prev: PrinterState, curr: PrinterState) = PrintEventDetector.detect(prev, curr)

    @Test
    fun `running to finish fires Finished`() {
        assertEquals(listOf(PrintEvent.Finished("Benchy")), detect(running, running.copy(gcodeState = GcodeState.FINISH)))
    }

    @Test
    fun `first state after connect is a baseline, not an event`() {
        val finished = running.copy(gcodeState = GcodeState.FINISH, printError = 123)
        assertTrue(detect(PrinterState(), finished).isEmpty())
        assertTrue(detect(PrinterState(gcodeState = GcodeState.UNKNOWN, updatedAt = 5), finished).isEmpty())
    }

    @Test
    fun `idle to finish does not fire`() {
        val idle = running.copy(gcodeState = GcodeState.IDLE)
        assertTrue(detect(idle, idle.copy(gcodeState = GcodeState.FINISH)).isEmpty())
    }

    @Test
    fun `failed fires Stopped with error code when present`() {
        assertEquals(
            listOf(PrintEvent.Stopped("Benchy", null)),
            detect(running, running.copy(gcodeState = GcodeState.FAILED)),
        )
        assertEquals(
            listOf(PrintEvent.Stopped("Benchy", 0x0500400EL)),
            detect(running, running.copy(gcodeState = GcodeState.FAILED, printError = 0x0500400EL)),
        )
    }

    @Test
    fun `pause with error needs attention and does not double-report the error`() {
        val events = detect(running, running.copy(gcodeState = GcodeState.PAUSE, printError = 0x07008011L))
        assertEquals(listOf(PrintEvent.Paused("Benchy", 0x07008011L, needsAttention = true)), events)
    }

    @Test
    fun `plain pause does not need attention`() {
        assertEquals(
            listOf(PrintEvent.Paused("Benchy", null, needsAttention = false)),
            detect(running, running.copy(gcodeState = GcodeState.PAUSE)),
        )
    }

    @Test
    fun `new error while running fires once`() {
        val err = running.copy(printError = 42)
        assertEquals(listOf(PrintEvent.ErrorRaised(42)), detect(running, err))
        assertTrue("same error again is not new", detect(err, err.copy(layer = 51)).isEmpty())
    }

    @Test
    fun `hms count increase fires, decrease does not`() {
        val two = running.copy(hmsCount = 2)
        assertEquals(listOf(PrintEvent.HmsAlert(2)), detect(running, two))
        assertTrue(detect(two, running).isEmpty())
    }

    @Test
    fun `first layer done fires on layer 1 to 2 or more`() {
        val l1 = running.copy(layer = 1)
        assertEquals(listOf(PrintEvent.FirstLayerDone("Benchy")), detect(l1, l1.copy(layer = 3)))
        assertTrue(detect(running.copy(layer = 2), running.copy(layer = 3)).isEmpty())
    }

    @Test
    fun `descriptions and error formatting`() {
        assertEquals("0x0500400E", formatErrorCode(0x0500400EL))
        assertEquals("Print failed", PrintEvent.Stopped("Benchy", 5).describe().title)
        assertEquals("Print stopped", PrintEvent.Stopped("Benchy", null).describe().title)
        assertEquals("1 active HMS alert. Check the printer.", PrintEvent.HmsAlert(1).describe().text)
        assertEquals("The print is done. Clear the plate.", PrintEvent.Finished(null).describe().text)
    }
}
