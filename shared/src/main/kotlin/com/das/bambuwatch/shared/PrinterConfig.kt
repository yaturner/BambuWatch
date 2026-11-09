package com.das.bambuwatch.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** How to reach the printer on the LAN. */
@Serializable
data class PrinterConfig(
    val ip: String,
    val serial: String,
    @SerialName("accessCode") val accessCode: String,
) {
    val isComplete: Boolean get() = ip.isNotBlank() && serial.isNotBlank() && accessCode.isNotBlank()

    fun normalized(): PrinterConfig = PrinterConfig(ip.trim(), serial.trim().uppercase(), accessCode.trim())

    override fun toString(): String = "PrinterConfig(ip=$ip, serial=$serial, accessCode=***)"
}

/**
 * The printer.json file format:
 * ```
 * { "ip": "192.168.1.50", "serial": "01P00A123456789", "accessCode": "12345678" }
 * ```
 */
object PrinterConfigCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun encode(config: PrinterConfig): String = json.encodeToString(PrinterConfig.serializer(), config.normalized()) + "\n"

    @OptIn(ExperimentalSerializationApi::class)
    /** Returns the config, or throws [InvalidConfigException] explaining what's wrong. */
    fun decode(text: String): PrinterConfig {
        val config = try {
            json.decodeFromString(PrinterConfig.serializer(), text)
        } catch (e: MissingFieldException) {
            throw InvalidConfigException("missing ${e.missingFields.joinToString()}", e)
        } catch (e: SerializationException) {
            throw InvalidConfigException(e.message?.lineSequence()?.firstOrNull() ?: "not valid JSON", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidConfigException(e.message ?: "not valid JSON", e)
        }.normalized()
        if (!config.isComplete) throw InvalidConfigException("ip, serial and accessCode must all be non-empty")
        return config
    }
}

class InvalidConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)
