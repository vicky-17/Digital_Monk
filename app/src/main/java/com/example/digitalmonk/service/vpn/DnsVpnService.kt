package com.example.digitalmonk.service.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.digitalmonk.core.utils.Constants
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.ui.dashboard.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var vpnThread: Thread? = null
    private lateinit var filterEngine: DnsFilterEngine

    companion object {
        @Volatile var serviceRunning: Boolean = false
            private set
        private const val TAG = "DnsVpnService"
        const val ACTION_STOP = "ACTION_STOP"

        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_DNS     = "10.0.0.2"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 3000
    }

    override fun onCreate() {
        super.onCreate()
        filterEngine = DnsFilterEngine(PrefsManager(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop command received")
            stopVpn()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            // ✅ FIX: Android 14+ (API 34+) REQUIRES the foreground service type to be passed
            // to startForeground(). Without this, it throws MissingForegroundServiceTypeException.
            // The type here must match android:foregroundServiceType in AndroidManifest.xml.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    Constants.NOTIFICATION_ID_VPN,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(Constants.NOTIFICATION_ID_VPN, buildNotification())
            }
            startVpn()
        }

        return START_STICKY
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked by user")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    private fun startVpn() {
        if (isRunning) return

        try {
            vpnInterface = Builder()
                .setSession("Digital Monk Shield")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_DNS, 32)
                .addDnsServer(VPN_DNS)
                .addDisallowedApplication(packageName)
                .setBlocking(true)
                .establish()

            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface is null — permission not granted?")
                stopSelf()
                return
            }

            isRunning = true
            serviceRunning = true
            vpnThread = thread(name = "dns-vpn-thread") { runVpnLoop() }
            Log.i(TAG, "✅ VPN started — DNS filter is active")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning = false
        serviceRunning = false
        vpnThread?.interrupt()
        vpnThread = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    private fun runVpnLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream  = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val packetBuffer = ByteArray(32767)

        Log.i(TAG, "DNS packet loop started")

        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val length = inputStream.read(packetBuffer)
                if (length <= 0) continue

                val query = DnsPacketParser.parse(packetBuffer, length) ?: continue

                Log.v(TAG, "DNS query: ${query.domain} (type=${query.queryType})")

                val response: ByteArray? = when (val decision = filterEngine.decide(query.domain, query.queryType)) {
                    is DnsFilterEngine.FilterDecision.Block -> {
                        Log.d(TAG, "🚫 Blocked: ${query.domain}")
                        DnsPacketParser.buildNxDomainResponse(query)
                    }
                    is DnsFilterEngine.FilterDecision.SafeSearchRedirect -> {
                        Log.d(TAG, "🔍 SafeSearch: ${query.domain} → ${decision.redirectIp}")
                        DnsPacketParser.buildARecordResponse(query, decision.redirectIp)
                    }
                    is DnsFilterEngine.FilterDecision.Allow -> {
                        forwardToUpstream(query)
                    }
                }

                if (response != null) {
                    outputStream.write(response)
                    outputStream.flush()
                }
            }
        } catch (e: InterruptedException) {
            Log.i(TAG, "VPN loop interrupted (shutdown)")
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "Error in VPN loop", e)
        }

        Log.i(TAG, "DNS packet loop ended")
    }

    private fun forwardToUpstream(query: DnsPacketParser.DnsQuery): ByteArray? {
        val socket = DatagramSocket()
        return try {
            protect(socket)
            socket.soTimeout = DNS_TIMEOUT_MS

            val dnsPayload = query.rawPacket.copyOfRange(query.dnsPayloadOffset, query.rawLength)
            val upstreamAddress = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(dnsPayload, dnsPayload.size, upstreamAddress, DNS_PORT))

            val responseBuffer = ByteArray(4096)
            val receivePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(receivePacket)

            DnsPacketParser.wrapUpstreamResponse(query, receivePacket.data.copyOf(receivePacket.length))

        } catch (e: Exception) {
            Log.w(TAG, "Upstream DNS failed for ${query.domain}: ${e.message}")
            DnsPacketParser.buildNxDomainResponse(query)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }



    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, DnsVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.CHANNEL_VPN)
            .setContentTitle("Digital Monk Shield Active 🛡️")
            .setContentText("Web filter & SafeSearch are running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}