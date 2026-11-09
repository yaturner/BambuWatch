package com.das.bambuwatch.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The small snapshot the phone sends to the watch over the Wearable Data
 * Layer (milestone 4). Kept compact on purpose: every change wakes the watch.
 */
@Serializable
data class WatchState(
    val connected: Boolean,
    val gcodeState: String,
    val percent: Int,
    val remainingMin: Int,
    val layer: Int,
    val totalLayers: Int,
    val nozzleTemp: Int,
    val nozzleTarget: Int,
    val bedTemp: Int,
    val bedTarget: Int,
    val jobName: String,
    val activeAmsSlot: Int? = null,
    val filamentColor: String? = null,
    val errorCode: Long? = null,
    val hmsCount: Int = 0,
    val lightOn: Boolean? = null,
    val updatedAt: Long,
) {
    fun toBytes(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromBytes(bytes: ByteArray): WatchState = json.decodeFromString(serializer(), bytes.decodeToString())

        fun from(state: PrinterState): WatchState = WatchState(
            connected = state.connection == ConnectionState.CONNECTED,
            gcodeState = state.gcodeState.name,
            percent = state.percent ?: 0,
            remainingMin = state.remainingMinutes ?: 0,
            layer = state.layer ?: 0,
            totalLayers = state.totalLayers ?: 0,
            nozzleTemp = state.nozzleTemp?.toInt() ?: 0,
            nozzleTarget = state.nozzleTarget?.toInt() ?: 0,
            bedTemp = state.bedTemp?.toInt() ?: 0,
            bedTarget = state.bedTarget?.toInt() ?: 0,
            jobName = state.jobName.orEmpty(),
            activeAmsSlot = state.activeTray,
            filamentColor = state.activeAmsTray?.colorHex,
            errorCode = state.printError?.takeIf { it != 0L },
            hmsCount = state.hmsCount,
            lightOn = state.lightOn,
            updatedAt = state.updatedAt,
        )
    }
}
