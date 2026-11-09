package com.das.bambuwatch.wear

import android.content.Context
import android.util.Base64
import android.util.Log
import com.das.bambuwatch.shared.WatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The latest snapshot from the phone and when the phone sent it. */
data class Received(val state: WatchState, val sentAt: Long)

/**
 * Holds the most recent [WatchState] in memory and in SharedPreferences, so
 * the UI (and the tile in milestone 5) can show something instantly after
 * the process restarts.
 */
class WatchStateCache(context: Context) {
    private val prefs = context.getSharedPreferences("watch_state", Context.MODE_PRIVATE)

    private val _latest = MutableStateFlow(load())
    val latest: StateFlow<Received?> = _latest.asStateFlow()

    /** Ignores anything older than what we already have (items can arrive out of order). */
    fun update(bytes: ByteArray, sentAt: Long) {
        val state = try {
            WatchState.fromBytes(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "Bad state payload", e)
            return
        }
        val current = _latest.value
        if (current != null && sentAt < current.sentAt) return
        _latest.value = Received(state, sentAt)
        prefs.edit()
            .putString(KEY_BYTES, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .putLong(KEY_SENT_AT, sentAt)
            .apply()
        Log.i(TAG, "State from phone: ${state.gcodeState} ${state.percent}% '${state.jobName}' connected=${state.connected}")
    }

    private fun load(): Received? {
        val encoded = prefs.getString(KEY_BYTES, null) ?: return null
        return runCatching {
            Received(WatchState.fromBytes(Base64.decode(encoded, Base64.NO_WRAP)), prefs.getLong(KEY_SENT_AT, 0))
        }.getOrNull()
    }

    private companion object {
        const val TAG = "WatchStateCache"
        const val KEY_BYTES = "state_bytes"
        const val KEY_SENT_AT = "sent_at"
    }
}
