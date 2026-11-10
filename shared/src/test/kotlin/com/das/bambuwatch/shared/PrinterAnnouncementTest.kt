package com.das.bambuwatch.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrinterAnnouncementTest {
    // Captured from a P1S (serial changed).
    private val p1s = """
        NOTIFY * HTTP/1.1
        HOST: 239.255.255.250:1900
        Server: UPnP/1.0
        Location: 192.168.68.54
        NT: urn:bambulab-com:device:3dprinter:1
        USN: 01P00C000000001
        Cache-Control: max-age=1800
        DevModel.bambu.com: C12
        DevName.bambu.com: p1s
        DevSignal.bambu.com: -37
        DevConnect.bambu.com: lan
        DevBind.bambu.com: free
        Devseclink.bambu.com: secure
        DevVersion.bambu.com: 01.10.00.00
        DevCap.bambu.com: 1
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun parsesP1sAnnouncement() {
        val p = PrinterAnnouncement.parse(p1s, "192.168.68.54")!!
        assertEquals("192.168.68.54", p.ip)
        assertEquals("01P00C000000001", p.serial)
        assertEquals("C12", p.modelCode)
        assertEquals("P1S", p.modelName)
        assertEquals("p1s", p.name)
    }

    @Test
    fun fallsBackToSenderIpWhenLocationMissing() {
        val noLocation = p1s.lineSequence().filterNot { it.startsWith("Location") }.joinToString("\r\n")
        assertEquals("10.0.0.7", PrinterAnnouncement.parse(noLocation, "10.0.0.7")!!.ip)
    }

    @Test
    fun ignoresOtherSsdpDevices() {
        val router = """
            NOTIFY * HTTP/1.1
            Location: http://192.168.68.1:5000/rootDesc.xml
            NT: urn:schemas-upnp-org:device:InternetGatewayDevice:1
            USN: uuid:1234::upnp:rootdevice
        """.trimIndent()
        assertNull(PrinterAnnouncement.parse(router, "192.168.68.1"))
        assertNull(PrinterAnnouncement.parse("garbage", "192.168.68.1"))
        assertNull(PrinterAnnouncement.parse(p1s.replace("USN: 01P00C000000001", "USN: "), "192.168.68.54"))
    }

    @Test
    fun unknownModelCodeShownAsIs() {
        assertEquals("Z9", BambuModels.nameFor("Z9"))
        assertEquals("A1 mini", BambuModels.nameFor("n1"))
    }
}
