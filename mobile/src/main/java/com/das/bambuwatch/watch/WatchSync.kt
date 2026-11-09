package com.das.bambuwatch.watch

import android.content.Context
import android.util.Log
import com.das.bambuwatch.shared.DataLayerPaths
import com.das.bambuwatch.shared.PrinterState
import com.das.bambuwatch.shared.WatchState
import com.das.bambuwatch.shared.WatchSyncPolicy
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** What the phone knows about its link to the watch, for the status screen. */
data class WatchLinkStatus(
    /** False when the Wearable API isn't available (no Wear OS / Galaxy Wearable pairing). */
    val apiAvailable: Boolean = true,
    /** Display names of connected watches. */
    val connectedWatches: List<String> = emptyList(),
    /** Display names of connected watches that have the BambuWatch watch app installed. */
    val appInstalledOn: List<String> = emptyList(),
    val lastSentAt: Long = 0,
    val lastError: String? = null,
)

/**
 * Pushes [WatchState] snapshots to the watch as a Data Layer item at
 * [DataLayerPaths.STATE], paced by [WatchSyncPolicy]. The Data Layer delivers
 * the latest item whenever the watch reconnects, so nothing is queued here.
 */
class WatchSync(context: Context, private val state: StateFlow<PrinterState>) {
    private val dataClient = Wearable.getDataClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)

    private val lock = Mutex()
    private var lastSent: WatchState? = null
    private var lastSentAt = 0L
    private var noDataSince = 0L

    private val _link = MutableStateFlow(WatchLinkStatus())
    val link: StateFlow<WatchLinkStatus> = _link.asStateFlow()

    /** Runs until cancelled (by the monitor service). */
    suspend fun run() = coroutineScope {
        launch { state.collect { evaluate(it) } }
        // Flushes throttled progress and sends heartbeats when the printer goes quiet.
        launch {
            while (isActive) {
                delay(TICK_MS)
                evaluate(state.value)
            }
        }
        launch {
            while (isActive) {
                refreshLinkStatus()
                delay(LINK_REFRESH_MS)
            }
        }
    }

    /** Sends [printerState] now, bypassing the policy (e.g. a final "disconnected" when monitoring stops). */
    suspend fun sendNow(printerState: PrinterState) = lock.withLock {
        val ws = WatchState.from(printerState)
        val now = System.currentTimeMillis()
        if (put(ws, now)) { lastSent = ws; lastSentAt = now }
    }

    suspend fun refreshLinkStatus() {
        if (!_link.value.apiAvailable) return
        try {
            val nodes = nodeClient.connectedNodes.await()
            val withApp = capabilityClient
                .getCapability(DataLayerPaths.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await().nodes
            _link.update {
                it.copy(
                    connectedWatches = nodes.map { n -> n.displayName },
                    appInstalledOn = withApp.map { n -> n.displayName },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFailure(e)
        }
    }

    private suspend fun evaluate(printerState: PrinterState) = lock.withLock {
        if (!_link.value.apiAvailable) return@withLock
        val now = System.currentTimeMillis()
        // Right after (re)connecting there's no printer data yet. Don't overwrite the watch's
        // last good snapshot with a blank one unless the printer stays silent for a while.
        if (printerState.updatedAt == 0L) {
            if (noDataSince == 0L) noDataSince = now
            if (now - noDataSince < NO_DATA_GRACE_MS) return@withLock
        } else {
            noDataSince = 0L
        }
        val candidate = WatchState.from(printerState)
        if (WatchSyncPolicy.shouldSend(lastSent, lastSentAt, candidate, now) && put(candidate, now)) {
            lastSent = candidate
            lastSentAt = now
        }
    }

    private suspend fun put(ws: WatchState, now: Long): Boolean {
        if (!_link.value.apiAvailable) return false
        return try {
            val request = PutDataMapRequest.create(DataLayerPaths.STATE).apply {
                dataMap.putByteArray(DataLayerPaths.STATE_KEY, ws.toBytes())
                dataMap.putLong(DataLayerPaths.SENT_AT_KEY, now)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            Log.d(TAG, "Sent ${ws.gcodeState} ${ws.percent}% to watch")
            _link.update { it.copy(lastSentAt = now, lastError = null) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFailure(e)
            false
        }
    }

    private fun handleFailure(e: Exception) {
        if (e is ApiException && e.statusCode == ConnectionResult.API_UNAVAILABLE) {
            Log.i(TAG, "Wearable API unavailable on this phone; watch sync disabled")
            _link.update { it.copy(apiAvailable = false, lastError = "Wear OS not available on this phone") }
        } else {
            Log.w(TAG, "Watch sync failed", e)
            _link.update { it.copy(lastError = e.message ?: e.javaClass.simpleName) }
        }
    }

    private companion object {
        const val TAG = "WatchSync"
        const val TICK_MS = 10_000L
        const val LINK_REFRESH_MS = 60_000L
        const val NO_DATA_GRACE_MS = 60_000L
    }
}
