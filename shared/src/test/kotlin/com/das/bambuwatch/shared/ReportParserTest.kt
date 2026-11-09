package com.das.bambuwatch.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportParserTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResource(name)) { "missing fixture $name" }.readText()

    private val full get() = ReportParser.merge(fixture("full_report.json"), PrinterState(), nowMillis = 1000)

    @Test
    fun `full report populates every field`() {
        val s = full
        assertEquals(GcodeState.RUNNING, s.gcodeState)
        assertEquals(67, s.percent)
        assertEquals(72, s.remainingMinutes)
        assertEquals(142, s.layer)
        assertEquals(210, s.totalLayers)
        assertEquals("Benchy", s.jobName)
        assertEquals(2, s.speedLevel)
        assertEquals(219.8, s.nozzleTemp!!, 0.001)
        assertEquals(220.0, s.nozzleTarget!!, 0.001)
        assertEquals(59.9, s.bedTemp!!, 0.001)
        assertEquals(60.0, s.bedTarget!!, 0.001)
        assertEquals(31.0, s.chamberTemp!!, 0.001)
        assertEquals(true, s.lightOn)
        assertEquals(0L, s.printError)
        assertEquals(0, s.hmsCount)
        assertEquals(38, s.amsHumidityPercent)
        assertEquals(1000L, s.updatedAt)
        assertTrue(s.isPrinting)
        assertFalse(s.hasError)
    }

    @Test
    fun `ams trays are mapped with colors, remain and empty slots`() {
        val s = full
        assertEquals(4, s.amsTrays.size)
        assertEquals(AmsTray(0, "PLA", "#FFFFFF", 80, isEmpty = false), s.amsTrays[0])
        assertEquals(AmsTray(1, "PETG", "#0A2989", 45, isEmpty = false), s.amsTrays[1])
        assertTrue(s.amsTrays[2].isEmpty)
        assertNull("remain -1 means unknown", s.amsTrays[3].remainPercent)
        assertEquals(1, s.activeTray)
        assertEquals("#0A2989", s.activeAmsTray?.colorHex)
    }

    @Test
    fun `partial report keeps fields it does not mention`() {
        val s = ReportParser.merge(fixture("partial_report.json"), full, nowMillis = 2000)
        assertEquals(68, s.percent)
        assertEquals(143, s.layer)
        assertEquals(220.4, s.nozzleTemp!!, 0.001)
        // untouched
        assertEquals(GcodeState.RUNNING, s.gcodeState)
        assertEquals(59.9, s.bedTemp!!, 0.001)
        assertEquals("Benchy", s.jobName)
        assertEquals(4, s.amsTrays.size)
        assertEquals(1, s.activeTray)
        assertEquals(2000L, s.updatedAt)
    }

    @Test
    fun `tray_now delta without unit list keeps trays but updates active slot`() {
        val s = ReportParser.merge("""{"print":{"ams":{"tray_now":"255"}}}""", full, 3000)
        assertEquals(4, s.amsTrays.size)
        assertNull(s.activeTray)

        val ext = ReportParser.merge("""{"print":{"ams":{"tray_now":"254"}}}""", full, 3000)
        assertEquals(PrinterState.EXTERNAL_SPOOL, ext.activeTray)
        assertNull(ext.activeAmsTray)
    }

    @Test
    fun `numbers sent as strings are accepted`() {
        val s = ReportParser.merge(
            """{"print":{"mc_percent":"45","nozzle_temper":"215.5","layer_num":"12.0"}}""",
            PrinterState(), 1,
        )
        assertEquals(45, s.percent)
        assertEquals(215.5, s.nozzleTemp!!, 0.001)
        assertEquals(12, s.layer)
    }

    @Test
    fun `errors and hms alerts are detected`() {
        val s = ReportParser.merge(
            """{"print":{"gcode_state":"PAUSE","print_error":117473286,"hms":[{"attr":1,"code":2},{"attr":3,"code":4}]}}""",
            full, 1,
        )
        assertEquals(GcodeState.PAUSE, s.gcodeState)
        assertEquals(117473286L, s.printError)
        assertEquals(2, s.hmsCount)
        assertTrue(s.hasError)
    }

    @Test
    fun `invalid or irrelevant payloads return previous state unchanged`() {
        val prev = full
        assertSame(prev, ReportParser.merge("not json", prev, 9))
        assertSame(prev, ReportParser.merge("[1,2]", prev, 9))
        assertSame(prev, ReportParser.merge("""{"info":{"command":"get_version"}}""", prev, 9))
    }

    @Test
    fun `gcode states map including prepare and unknown`() {
        assertEquals(GcodeState.PREPARE, GcodeState.fromRaw("PREPARE"))
        assertEquals(GcodeState.FINISH, GcodeState.fromRaw("finish"))
        assertEquals(GcodeState.UNKNOWN, GcodeState.fromRaw("WAT"))
    }

    @Test
    fun `job names are prettified for display`() {
        assertEquals("Can Organizer - No Supports Needed", prettyJobName("Can_Organizer_-_No_Supports_Needed"))
        assertEquals("Benchy", prettyJobName("  Benchy "))
        assertNull(prettyJobName("__"))
        assertNull(prettyJobName(null))
    }

    @Test
    fun `color normalization`() {
        assertEquals("#0A2989", ReportParser.normalizeColor("0a2989ff"))
        assertEquals("#0A2989", ReportParser.normalizeColor("#0A2989"))
        assertNull(ReportParser.normalizeColor("abc"))
        assertNull(ReportParser.normalizeColor("ZZZZZZFF"))
        assertNull(ReportParser.normalizeColor(null))
    }
}
