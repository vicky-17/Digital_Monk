package com.example.digitalmonk.service.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var vpnThread: Thread? = null

    companion object {
        private const val TAG = "DnsVpnService"

        // The fake IP address for the Android device inside the VPN
        private const val VPN_ADDRESS = "10.0.0.1"

        // The fake IP address for our intercepting DNS Server
        private const val VPN_DNS = "10.0.0.2"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        // START_STICKY tells Android to restart this service if it gets killed
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return

        try {
            val builder = Builder()
                .addAddress(VPN_ADDRESS, 32)

                // CRITICAL FOR BATTERY: We ONLY route traffic destined for 10.0.0.2 (Port 53).
                // All other apps (YouTube videos, games) bypass the VPN and use normal Wi-Fi.
                .addRoute(VPN_DNS, 32)

                // Tell Android: "Send all DNS queries to 10.0.0.2"
                .addDnsServer(VPN_DNS)

                .setSession("Digital Monk Shield")
                .setBlocking(true)

            // Build the tunnel
            vpnInterface = builder.establish()
            isRunning = true

            // Start reading the captured traffic on a background thread
            vpnThread = thread {
                runVpnLoop()
            }
            Log.d(TAG, "VPN Started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun runVpnLoop() {
        val fileDescriptor = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fileDescriptor)
        val outputStream = FileOutputStream(fileDescriptor)

        // Buffer to hold the raw network packet (Max UDP size is 65535, but 32767 is safe for DNS)
        val packet = ByteBuffer.allocate(32767)

        try {
            while (isRunning && !Thread.interrupted()) {
                packet.clear()

                // This blocks until Android sends a DNS query to our fake server
                val length = inputStream.read(packet.array())
                if (length > 0) {
                    packet.limit(length)

                    // PHASE 1: We successfully caught a packet!
                    Log.d(TAG, "Captured a raw packet! Size: $length bytes")

                    // TODO (Phase 2): Pass this raw packet to our DNS parser
                    // 1. Read the domain name
                    // 2. Check Blocklist / SafeSearch
                    // 3. Send back a fake response using outputStream
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error reading from VPN tunnel", e)
            }
        } finally {
            stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning = false
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopSelf()
        Log.d(TAG, "VPN Stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}