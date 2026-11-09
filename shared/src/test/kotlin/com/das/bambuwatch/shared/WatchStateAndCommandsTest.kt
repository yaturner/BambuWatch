package com.das.bambuwatch.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchStateAndCommandsTest {
    private val printing = PrinterState(
        connection = ConnectionState.CONNECTED,
        gcodeState = GcodeState.RUNNING,
        percent = 67, remainingMinutes = 72, layer = 142, totalLayers = 210,
        nozzleTemp = 219.8, nozzleTarget = 220.0, bedTemp = 59.9, bedTarget = 60.0,
        jobName = "Benchy",
        amsTrays = listOf(AmsTray(1, "PETG", "#0A2989", 45, false)),
        activeTray = 1, printError = 0, updatedAt = 42,
    )

    @Test
    fun `watch state is derived from printer state`() {
        val w = WatchState.from(printing)
        assertEquals(true, w.connected)
        assertEquals("RUNNING", w.gcodeState)
        assertEquals(67, w.percent)
        assertEquals(219, w.nozzleTemp)
        assertEquals("#0A2989", w.filamentColor)
        assertNull("print_error 0 is not an error", w.errorCode)
    }

    @Test
    fun `watch state round trips through bytes`() {
        val w = WatchState.from(printing)
        assertEquals(w, WatchState.fromBytes(w.toBytes()))
    }

    @Test
    fun `watch state payload stays small`() {
        val size = WatchState.from(printing).toBytes().size
        assert(size < 512) { "WatchState is $size bytes" }
    }

    @Test
    fun `command payloads have the expected shape`() {
        val push = Json.parseToJsonElement(PrinterCommands.pushAll(7)).jsonObject["pushing"]!!.jsonObject
        assertEquals("pushall", push["command"]!!.jsonPrimitive.content)
        assertEquals("7", push["sequence_id"]!!.jsonPrimitive.content)

        val pause = Json.parseToJsonElement(PrinterCommands.pause(8)).jsonObject["print"]!!.jsonObject
        assertEquals("pause", pause["command"]!!.jsonPrimitive.content)

        val light = Json.parseToJsonElement(PrinterCommands.chamberLight(false, 9)).jsonObject["system"]!!.jsonObject
        assertEquals("ledctrl", light["command"]!!.jsonPrimitive.content)
        assertEquals("off", light["led_mode"]!!.jsonPrimitive.content)
    }
}
