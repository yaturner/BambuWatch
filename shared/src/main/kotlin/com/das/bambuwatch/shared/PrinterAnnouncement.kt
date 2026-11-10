package com.das.bambuwatch.shared

/** A Bambu Lab printer heard announcing itself on the local network. */
data class DiscoveredPrinter(
    val ip: String,
    val serial: String,
    /** Model code from the announcement, e.g. "C12". */
    val modelCode: String?,
    /** The name the user gave the printer in Bambu Studio / Handy, e.g. "p1s". */
    val name: String?,
) {
    val modelName: String? get() = modelCode?.let { BambuModels.nameFor(it) }
}

/**
 * Parses the SSDP NOTIFY that Bambu printers broadcast every few seconds on UDP port 2021:
 * ```
 * NOTIFY * HTTP/1.1
 * Location: 192.168.68.54
 * NT: urn:bambulab-com:device:3dprinter:1
 * USN: 01P00C570301173
 * DevModel.bambu.com: C12
 * DevName.bambu.com: p1s
 * ```
 * Only reads the announcement; nothing is ever sent to the printer.
 */
object PrinterAnnouncement {
    const val PORT = 2021
    private const val PRINTER_NT = "urn:bambulab-com:device:3dprinter"
    private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
    private val SERIAL = Regex("^[A-Z0-9]{8,20}$")

    /** Returns the printer, or null if [packet] isn't a Bambu printer announcement. */
    fun parse(packet: String, senderIp: String?): DiscoveredPrinter? {
        val lines = packet.lineSequence().map { it.trim() }.toList()
        if (lines.firstOrNull()?.startsWith("NOTIFY", ignoreCase = true) != true &&
            lines.firstOrNull()?.startsWith("HTTP/1.1 200", ignoreCase = true) != true
        ) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val i = line.indexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
            line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()

        val nt = headers["nt"] ?: headers["st"] ?: return null
        if (!nt.startsWith(PRINTER_NT, ignoreCase = true)) return null

        val serial = headers["usn"]?.uppercase()?.takeIf { SERIAL.matches(it) } ?: return null
        val ip = headers["location"]?.takeIf { IPV4.matches(it) }
            ?: senderIp?.takeIf { IPV4.matches(it) }
            ?: return null
        return DiscoveredPrinter(
            ip = ip,
            serial = serial,
            modelCode = headers["devmodel.bambu.com"]?.ifBlank { null },
            name = headers["devname.bambu.com"]?.ifBlank { null },
        )
    }
}

/** Bambu's internal model codes, as sent in announcements. */
object BambuModels {
    private val NAMES = mapOf(
        "BL-P001" to "X1 Carbon",
        "BL-P002" to "X1",
        "C13" to "X1E",
        "C11" to "P1P",
        "C12" to "P1S",
        "N1" to "A1 mini",
        "N2S" to "A1",
        "O1D" to "H2D",
    )

    fun nameFor(code: String): String = NAMES[code.uppercase()] ?: code
}
