package com.das.bambuwatch.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterConfigTest {
    private val cfg = PrinterConfig("192.168.1.50", "01P00A123456789", "12345678")

    @Test
    fun `round trips through the file format`() {
        assertEquals(cfg, PrinterConfigCodec.decode(PrinterConfigCodec.encode(cfg)))
    }

    @Test
    fun `encoded file is human readable with expected keys`() {
        val text = PrinterConfigCodec.encode(cfg)
        assertTrue(text.contains("\"ip\": \"192.168.1.50\""))
        assertTrue(text.contains("\"serial\": \"01P00A123456789\""))
        assertTrue(text.contains("\"accessCode\": \"12345678\""))
    }

    @Test
    fun `decode normalizes whitespace and serial case and ignores extra keys`() {
        val decoded = PrinterConfigCodec.decode(
            """{"ip":" 192.168.1.50 ","serial":"01p00a123456789","accessCode":" 12345678","comment":"garage P1S"}""",
        )
        assertEquals(cfg, decoded)
    }

    @Test
    fun `invalid files are rejected with a reason`() {
        assertThrows(InvalidConfigException::class.java) { PrinterConfigCodec.decode("not json") }
        val missing = assertThrows(InvalidConfigException::class.java) { PrinterConfigCodec.decode("""{"ip":"1.2.3.4"}""") }
        assertEquals("missing serial, accessCode", missing.message)
        val blank = assertThrows(InvalidConfigException::class.java) {
            PrinterConfigCodec.decode("""{"ip":"1.2.3.4","serial":"","accessCode":"x"}""")
        }
        assertTrue(blank.message!!.contains("non-empty"))
    }

    @Test
    fun `toString hides the access code`() {
        val secret = cfg.copy(accessCode = "3d75zz99")
        assertFalse(secret.toString().contains("3d75zz99"))
    }
}
