package com.das.bambuwatch.mqtt

import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bambu local MQTT: TLS on 8883, user "bblp", password = LAN access code.
 * Adapted from p1sTouchScreen-Android's MqttPrinterClient.
 *
 * The printer's certificate is self-signed and LAN-only, so trust-all is the
 * same accepted trade-off as the P1S touchscreen app. TODO: pin the cert
 * fingerprint on first connect instead.
 *
 * One instance = one connection attempt; create a new one to reconnect.
 */
class MqttPrinterClient(
    private val ip: String,
    private val accessCode: String,
    private val serial: String,
) {
    private val disconnected = CompletableDeferred<Throwable>()

    private val client: Mqtt3AsyncClient = Mqtt3Client.builder()
        .identifier("bambuwatch-${System.currentTimeMillis()}")
        .serverHost(ip)
        .serverPort(8883)
        .sslConfig(
            MqttClientSslConfig.builder()
                .trustManagerFactory(InsecureTrustManagerFactory.INSTANCE)
                .build(),
        )
        .simpleAuth()
        .username("bblp")
        .password(accessCode.toByteArray(Charsets.UTF_8))
        .applySimpleAuth()
        .addDisconnectedListener { ctx -> disconnected.complete(ctx.cause) }
        .buildAsync()

    private val reportTopic = "device/$serial/report"
    private val requestTopic = "device/$serial/request"

    suspend fun connect() {
        withTimeout(CONNECT_TIMEOUT_MS) {
            client.connectWith().keepAlive(30).send().await()
        }
    }

    /** Subscribes to reports; [onMessage] runs on a HiveMQ/Netty thread. */
    suspend fun subscribe(onMessage: (String) -> Unit) {
        withTimeout(CONNECT_TIMEOUT_MS) {
            client.subscribeWith()
                .topicFilter(reportTopic)
                .qos(MqttQos.AT_MOST_ONCE)
                .callback { publish -> onMessage(String(publish.payloadAsBytes, Charsets.UTF_8)) }
                .send()
                .await()
        }
    }

    /** QoS 0: the printer's broker never PUBACKs QoS 1 publishes (found in the P1S app). */
    suspend fun publish(json: String) {
        withTimeout(PUBLISH_TIMEOUT_MS) {
            client.publishWith()
                .topic(requestTopic)
                .qos(MqttQos.AT_MOST_ONCE)
                .payload(json.toByteArray(Charsets.UTF_8))
                .send()
                .await()
        }
    }

    /** Suspends until the connection drops, returning the cause. */
    suspend fun awaitDisconnect(): Throwable = disconnected.await()

    fun disconnect() {
        if (client.state.isConnectedOrReconnect) client.disconnect()
    }

    private suspend fun <T> CompletableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        whenComplete { value, error ->
            if (error != null) cont.resumeWithException(error) else cont.resume(value)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val PUBLISH_TIMEOUT_MS = 10_000L
    }
}
