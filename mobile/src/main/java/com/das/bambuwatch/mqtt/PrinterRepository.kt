package com.das.bambuwatch.mqtt

import android.util.Log
import com.das.bambuwatch.shared.PrinterConfig
import com.das.bambuwatch.shared.ConnectionState
import com.das.bambuwatch.shared.PrinterCommands
import com.das.bambuwatch.shared.PrinterState
import com.das.bambuwatch.shared.ReportParser
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the single MQTT connection to the printer and exposes the merged
 * [PrinterState]. Reconnects with exponential backoff (1 s .. 30 s) and
 * re-requests a full snapshot (`pushall`) every minute so a missed delta
 * can't leave the state stale for long.
 */
class PrinterRepository(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PrinterState())
    val state: StateFlow<PrinterState> = _state.asStateFlow()

    private val sequence = AtomicLong(0)
    private var connectionJob: Job? = null
    @Volatile private var client: MqttPrinterClient? = null

    @Synchronized
    fun start(config: PrinterConfig) {
        connectionJob?.cancel()
        _state.value = PrinterState(connection = ConnectionState.CONNECTING)
        connectionJob = scope.launch { connectionLoop(config) }
    }

    @Synchronized
    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        _state.update { it.copy(connection = ConnectionState.DISCONNECTED) }
    }

    /** Sends a raw command if connected. Returns false if there is no connection. */
    suspend fun send(buildPayload: (seq: Long) -> String): Boolean {
        val c = client ?: return false
        return runCatching { c.publish(buildPayload(sequence.incrementAndGet())) }
            .onFailure { Log.w(TAG, "publish failed", it) }
            .isSuccess
    }

    private suspend fun connectionLoop(config: PrinterConfig) = coroutineScope {
        var backoffMs = INITIAL_BACKOFF_MS
        var firstAttempt = true
        while (isActive) {
            _state.update {
                it.copy(connection = if (firstAttempt) ConnectionState.CONNECTING else ConnectionState.RECONNECTING)
            }
            val c = MqttPrinterClient(config.ip, config.accessCode, config.serial)
            try {
                c.connect()
                c.subscribe { payload ->
                    _state.update { ReportParser.merge(payload, it, System.currentTimeMillis()) }
                }
                client = c
                _state.update { it.copy(connection = ConnectionState.CONNECTED, lastError = null) }
                backoffMs = INITIAL_BACKOFF_MS

                coroutineScope {
                    val refresher = launch {
                        while (isActive) {
                            c.publish(PrinterCommands.pushAll(sequence.incrementAndGet()))
                            delay(PUSHALL_INTERVAL_MS)
                        }
                    }
                    val cause = c.awaitDisconnect()
                    refresher.cancel()
                    throw IOException("Connection lost: ${cause.message}", cause)
                }
            } catch (e: TimeoutCancellationException) {
                // Must come before CancellationException (its supertype): a timeout is a
                // retryable failure, not cancellation of this loop.
                Log.w(TAG, "MQTT timeout", e)
                _state.update { it.copy(lastError = "Timed out reaching printer at ${config.ip}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "MQTT connection failed", e)
                _state.update { it.copy(lastError = describe(e)) }
            } finally {
                client = null
                c.disconnect()
            }
            firstAttempt = false
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun describe(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.last()
        val msg = root.message ?: root.javaClass.simpleName
        return when {
            msg.contains("NOT_AUTHORIZED", true) || msg.contains("BAD_USER_NAME_OR_PASSWORD", true) ->
                "Access code rejected"
            else -> msg
        }
    }

    private companion object {
        const val TAG = "PrinterRepository"
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val PUSHALL_INTERVAL_MS = 60_000L
    }
}
