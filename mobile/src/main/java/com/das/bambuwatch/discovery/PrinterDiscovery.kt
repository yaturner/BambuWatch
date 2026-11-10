package com.das.bambuwatch.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.das.bambuwatch.shared.DiscoveredPrinter
import com.das.bambuwatch.shared.PrinterAnnouncement
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Finds Bambu Lab printers on the local network by listening for the announcements they
 * broadcast every few seconds on UDP [PrinterAnnouncement.PORT]. Listen-only: nothing is sent.
 */
class PrinterDiscovery(context: Context) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    /** Emits each announcement heard (repeats included) until the collector cancels. */
    fun announcements(): Flow<DiscoveredPrinter> = flow {
        // Some phones drop broadcast/multicast packets to save power unless a lock is held.
        val lock = wifi?.createMulticastLock("printwatcher-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = RECEIVE_TIMEOUT_MS
        }
        try {
            socket.bind(InetSocketAddress(PrinterAnnouncement.PORT))
            val buf = ByteArray(BUFFER_SIZE)
            while (currentCoroutineContext().isActive) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue // lets cancellation be noticed
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                PrinterAnnouncement.parse(text, packet.address?.hostAddress)?.let { emit(it) }
            }
        } finally {
            socket.close()
            lock?.release()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val RECEIVE_TIMEOUT_MS = 1_000
        const val BUFFER_SIZE = 2_048
    }
}
