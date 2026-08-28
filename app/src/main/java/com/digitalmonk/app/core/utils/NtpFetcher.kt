// Fetches time from time.google.com via NTP (UDP port 123)
// Returns NTP epoch ms, or -1 on failure
// Has 2 fallback servers: time.cloudflare.com, pool.ntp.org
// Static method: NtpFetcher.fetchNtpTime()
// Must be called off main thread (already handled by callers)
package com.digitalmonk.app.core.utils

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object NtpFetcher {
    private const val TAG = "NtpFetcher"
    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val TIMEOUT_MS = 4000

    // Difference between NTP epoch (1900) and Unix epoch (1970) in seconds
    private const val NTP_UNIX_OFFSET = 2208988800L

    private val SERVERS = arrayOf<String?>(
        "time.google.com",
        "time.cloudflare.com",
        "pool.ntp.org"
    )

    /**
     * Fetches current time from NTP servers.
     * Tries each server in order, returns first success.
     * 
     * @return Unix epoch milliseconds, or -1 if all servers failed.
     * MUST be called off the main thread.
     */
    @JvmStatic
    fun fetchNtpTime(): Long {
        for (server in SERVERS) {
            val result = queryServer(server)
            if (result > 0) {
                Log.i(TAG, "NTP time fetched from " + server + " → " + result)
                return result
            }
        }
        Log.w(TAG, "All NTP servers failed")
        return -1
    }

    private fun queryServer(server: String?): Long {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.setSoTimeout(TIMEOUT_MS)

            // Build NTP request packet
            val buffer = ByteArray(NTP_PACKET_SIZE)
            // LI=0, VN=3, Mode=3 (client)
            buffer[0] = 0x1B

            val address = InetAddress.getByName(server)
            val request = DatagramPacket(
                buffer, buffer.size, address, NTP_PORT
            )

            // Record time just before sending (T1)
            val requestTime = System.currentTimeMillis()
            socket.send(request)

            // Receive response
            val response = DatagramPacket(
                ByteArray(NTP_PACKET_SIZE), NTP_PACKET_SIZE
            )
            socket.receive(response)

            // Record time just after receiving (T4)
            val responseTime = System.currentTimeMillis()

            val data = response.getData()

            // Transmit Timestamp is at bytes 40–47
            // Extract seconds (bytes 40–43) and fraction (bytes 44–47)
            val seconds = extractUnsignedInt(data, 40)
            val fraction = extractUnsignedInt(data, 44)

            // Convert NTP timestamp to Unix epoch ms
            val ntpMs = (((seconds - NTP_UNIX_OFFSET) * 1000L)
                    + (fraction * 1000L / 0x100000000L))

            // Compensate for network round-trip (add half the RTT)
            val rtt = responseTime - requestTime
            val result = ntpMs + (rtt / 2)

            Log.d(TAG, server + " → ntpMs=" + ntpMs + " rtt=" + rtt + "ms")
            return result
        } catch (e: Exception) {
            Log.w(TAG, "NTP query failed for " + server + ": " + e.message)
            return -1
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close()
            }
        }
    }

    /**
     * Reads 4 bytes from data[offset] as an unsigned 32-bit integer.
     */
    private fun extractUnsignedInt(data: ByteArray, offset: Int): Long {
        var value: Long = 0
        for (i in 0..3) {
            value = (value shl 8) or (data[offset + i].toInt() and 0xFF).toLong()
        }
        return value
    }
}