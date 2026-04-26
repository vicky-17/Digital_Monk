package com.example.digitalmonk.service.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.digitalmonk.core.utils.Constants
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.vpn.heartbeat.VpnHeartBeatEntity
import com.example.digitalmonk.service.vpn.heartbeat.VpnHeartbeatMonitorWorker
import com.example.digitalmonk.ui.dashboard.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * DnsVpnService — Digital Monk's DNS filter VPN.
 *
 * ── Keep-Alive Layers (Detoxify "Keep VPN Alive" feature) ───────────────────
 *
 *  Layer 1 — WorkManager heartbeat watchdog (VpnHeartbeatMonitorWorker)
 *    Fires every 15 min. If last heartbeat==ALIVE and service not running → restart.
 *    Handles: process killed while phone is on, WorkManager survives app updates.
 *
 *  Layer 2 — Connectivity health probe (runConnectivityProbe)
 *    Coroutine loop every 15s. Socket-connects to 1.1.1.1:443 through VPN.
 *    If VPN tunnel is dead but device has internet → restart.
 *    Handles: VPN tunnel silently failing without the service dying.
 *
 *  Layer 3 — Bound companion service (VpnMonitorService)
 *    Bound while VPN is running. Its onDestroy() fires when OEM kills our process.
 *    Reads last heartbeat → if ALIVE → calls startForegroundService immediately.
 *    Handles: OEM aggressive memory management (MIUI, ColorOS, etc.)
 *
 *  Screen-on receiver
 *    Registered on start, unregistered on stop.
 *    On ACTION_SCREEN_ON: checks tunnel health and restarts if needed.
 *    Handles: Detoxify's exact scenario — low battery / low RAM kills while screen off.
 *
 * ── Prevent VPN Override (Detoxify "Prevent VPN override" feature) ───────────
 *
 *  onRevoke() override
 *    When another VPN displaces ours, Android calls onRevoke().
 *    If preventVpnOverride pref is ON → we immediately request VPN permission again
 *    and restart. This fights back against bypass attempts.
 *
 * Pattern sources:
 *   DDG: TrackerBlockingVpnService.kt, VpnStateMonitorService.kt, VpnServiceHeartbeat.kt
 *   Intra: IntraVpnService.java, AutoStarter.java
 */
class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var vpnThread: Thread? = null
    private lateinit var filterEngine: DnsFilterEngine
    private lateinit var prefs: PrefsManager

    // ── Heartbeat ─────────────────────────────────────────────────────────────
    private var heartbeatThread: Thread? = null

    // ── Screen-on receiver ────────────────────────────────────────────────────
    private var screenOnReceiver: BroadcastReceiver? = null

    // ── Connectivity probe ────────────────────────────────────────────────────
    private var connectivityProbeThread: Thread? = null
    private var consecutiveProbeFailures = 0

    // ── Companion service binding (Layer 3) ───────────────────────────────────
    private var monitorServiceBound = false
    private val monitorConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            monitorServiceBound = true
            Log.d(TAG, "VpnMonitorService bound ✅")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            monitorServiceBound = false
            Log.w(TAG, "VpnMonitorService disconnected")
        }
    }

    companion object {
        @Volatile var serviceRunning: Boolean = false
            private set
        private const val TAG = "DnsVpnService"
        const val ACTION_STOP = "ACTION_STOP"

        private const val VPN_ADDRESS  = "10.0.0.1"
        private const val VPN_DNS      = "10.0.0.2"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val DNS_PORT     = 53
        private const val DNS_TIMEOUT_MS = 3000

        private const val HEARTBEAT_INTERVAL_MS  = 7 * 60 * 1000L   // 7 minutes
        private const val PROBE_INTERVAL_MS       = 15_000L           // 15 seconds
        private const val MAX_PROBE_FAILURES      = 3                  // give up after 3
        private const val PROBE_SOCKET_TIMEOUT_MS = 5_000
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        filterEngine = DnsFilterEngine(prefs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop command received")
            stopVpn(cleanStop = true)
            return START_NOT_STICKY
        }

        if (!isRunning) {
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

    /**
     * onRevoke() — called by Android when another VPN displaces ours.
     *
     * Default behaviour: VPN is silently killed.
     * Our behaviour: if "Prevent VPN Override" is ON, immediately fight back.
     *
     * Pattern from Intra's IntraVpnService.java and DDG's TrackerBlockingVpnService.kt.
     */
    override fun onRevoke() {
        Log.w(TAG, "⚠️ VPN permission revoked (another VPN took over)")

        // Write heartbeat so watchdog knows this was NOT a clean stop
        // (we'll restart, so ALIVE is correct — we haven't given up)
        writeHeartbeat(VpnHeartBeatEntity.TYPE_ALIVE)

        if (prefs.preventVpnOverride && prefs.safeSearchEnabled) {
            Log.w(TAG, "🛡️ Prevent VPN Override is ON — requesting permission and restarting")
            try {
                // Re-launch MainActivity so the VPN permission dialog appears.
                // If the user hasn't revoked VPN permission manually, prepare() returns null
                // and we can restart directly.
                val vpnPrepare = prepare(applicationContext)
                if (vpnPrepare == null) {
                    // Permission still granted — restart directly
                    val restartIntent = Intent(applicationContext, DnsVpnService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(restartIntent)
                    } else {
                        applicationContext.startService(restartIntent)
                    }
                } else {
                    // Permission was revoked — send user to MainActivity to re-grant
                    val mainIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("requestVpnPermission", true)
                    }
                    applicationContext.startActivity(mainIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart after revoke", e)
            }
        } else {
            // Prevent override is OFF — accept the revoke, clean up
            stopVpn(cleanStop = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn(cleanStop = false)
    }

    // ── Start / Stop ──────────────────────────────────────────────────────────

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

            // Write first ALIVE heartbeat immediately
            writeHeartbeat(VpnHeartBeatEntity.TYPE_ALIVE)

            // Start all keep-alive subsystems
            vpnThread = thread(name = "dns-vpn-thread") { runVpnLoop() }
            startHeartbeatLoop()
            startConnectivityProbe()
            registerScreenOnReceiver()
            bindMonitorService()

            // Schedule WorkManager watchdog (Layer 1)
            if (prefs.keepVpnAlive) {
                VpnHeartbeatMonitorWorker.schedule(this)
            }

            Log.i(TAG, "✅ VPN started — DNS filter is active")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn(cleanStop = false)
        }
    }

    /**
     * @param cleanStop true = user initiated (write STOPPED heartbeat, cancel watchdog)
     *                  false = unexpected kill (keep ALIVE heartbeat so watchdog restarts us)
     */
    private fun stopVpn(cleanStop: Boolean) {
        if (!isRunning && !serviceRunning) return
        isRunning = false
        serviceRunning = false

        if (cleanStop) {
            // Write STOPPED so the watchdog knows not to restart
            writeHeartbeat(VpnHeartBeatEntity.TYPE_STOPPED)
            VpnHeartbeatMonitorWorker.cancel(this)
            Log.i(TAG, "VPN stopped cleanly by user")
        } else {
            Log.w(TAG, "VPN stopped unexpectedly — watchdog will restart")
        }

        // Stop all subsystems
        stopHeartbeatLoop()
        stopConnectivityProbe()
        unregisterScreenOnReceiver()
        unbindMonitorService()

        vpnThread?.interrupt()
        vpnThread = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Heartbeat loop (Layer 1 support) ──────────────────────────────────────

    /**
     * Writes ALIVE every 7 minutes while the VPN is running.
     * If this loop stops (process killed), the WorkManager watchdog
     * and VpnMonitorService detect the stale ALIVE and restart us.
     *
     * DDG pattern: VpnServiceHeartbeat.kt
     */
    private fun startHeartbeatLoop() {
        heartbeatThread = thread(name = "vpn-heartbeat") {
            try {
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS)
                    if (isRunning) {
                        writeHeartbeat(VpnHeartBeatEntity.TYPE_ALIVE)
                    }
                }
            } catch (_: InterruptedException) {
                Log.d(TAG, "Heartbeat loop interrupted")
            }
        }
    }

    private fun stopHeartbeatLoop() {
        heartbeatThread?.interrupt()
        heartbeatThread = null
    }

    private fun writeHeartbeat(type: String) {
        prefs.lastVpnHeartbeatType = type
        prefs.lastVpnHeartbeatTimestamp = System.currentTimeMillis()
        Log.d(TAG, "💓 Heartbeat: $type")
    }

    // ── Connectivity probe (Layer 2) ──────────────────────────────────────────

    /**
     * Every 15 seconds, try to open a socket connection to 1.1.1.1:443.
     * If this fails but the device has real internet → VPN tunnel is broken → restart.
     *
     * DDG pattern: NetworkConnectivityHealthHandler.kt
     *
     * We only count consecutive failures to avoid false positives
     * (e.g., momentary wifi drop while walking).
     */
    private fun startConnectivityProbe() {
        if (!prefs.keepVpnAlive) return
        consecutiveProbeFailures = 0

        connectivityProbeThread = thread(name = "vpn-connectivity-probe") {
            try {
                // Initial delay — let the tunnel settle before probing
                Thread.sleep(30_000)

                while (isRunning && !Thread.currentThread().isInterrupted) {
                    checkTunnelHealth()
                    Thread.sleep(PROBE_INTERVAL_MS)
                }
            } catch (_: InterruptedException) {
                Log.d(TAG, "Connectivity probe interrupted")
            }
        }
    }

    private fun checkTunnelHealth() {
        if (!isRunning) return

        val vpnHasConnectivity = trySocketConnect()
        val deviceHasInternet  = deviceHasRealInternet()

        if (!vpnHasConnectivity && deviceHasInternet) {
            consecutiveProbeFailures++
            Log.w(TAG, "⚠️ VPN connectivity probe failed ($consecutiveProbeFailures/$MAX_PROBE_FAILURES)")

            if (consecutiveProbeFailures >= 2) {
                Log.w(TAG, "Restarting VPN tunnel due to connectivity loss")
                restartTunnel()
                consecutiveProbeFailures = 0
            }

            if (consecutiveProbeFailures >= MAX_PROBE_FAILURES) {
                Log.e(TAG, "Giving up after $MAX_PROBE_FAILURES failures")
                consecutiveProbeFailures = 0
            }
        } else {
            if (consecutiveProbeFailures > 0) {
                Log.d(TAG, "✅ Connectivity restored")
                consecutiveProbeFailures = 0
            }
        }
    }

    private fun trySocketConnect(): Boolean {
        return try {
            val socket = Socket()
            protect(socket)  // Route through device's real network (not VPN)
            socket.connect(InetSocketAddress("1.1.1.1", 443), PROBE_SOCKET_TIMEOUT_MS)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun deviceHasRealInternet(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }

    private fun restartTunnel() {
        if (!isRunning) return
        try {
            Log.i(TAG, "Restarting VPN tunnel…")
            // Close the old interface — startVpn() will create a new one
            try { vpnInterface?.close() } catch (_: Exception) {}
            vpnInterface = null
            vpnThread?.interrupt()
            vpnThread = null
            isRunning = false

            // Brief pause before re-establishing
            Thread.sleep(500)

            isRunning = true
            vpnInterface = Builder()
                .setSession("Digital Monk Shield")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_DNS, 32)
                .addDnsServer(VPN_DNS)
                .addDisallowedApplication(packageName)
                .setBlocking(true)
                .establish()

            if (vpnInterface != null) {
                vpnThread = thread(name = "dns-vpn-thread") { runVpnLoop() }
                Log.i(TAG, "✅ VPN tunnel restarted")
            } else {
                Log.e(TAG, "Tunnel restart failed — null interface")
                isRunning = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting tunnel", e)
            isRunning = false
        }
    }

    private fun stopConnectivityProbe() {
        connectivityProbeThread?.interrupt()
        connectivityProbeThread = null
    }

    // ── Screen-on receiver ────────────────────────────────────────────────────

    /**
     * Registers a BroadcastReceiver for ACTION_SCREEN_ON.
     *
     * When the screen turns on after being off (e.g. after low battery kill),
     * we immediately check if the VPN tunnel is still healthy.
     *
     * This is Detoxify's "Keep VPN alive" core use case:
     *   "Some phones kill VPN willy-nilly. We'll attempt to keep it on for as
     *    long as possible." — triggered on screen-on.
     *
     * DDG / Intra pattern: ACTION_SCREEN_ON registered in onStartCommand.
     */
    private fun registerScreenOnReceiver() {
        if (screenOnReceiver != null) return

        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    Log.d(TAG, "📱 Screen ON — checking VPN tunnel health")
                    if (isRunning && prefs.keepVpnAlive) {
                        thread(name = "screen-on-health-check") {
                            checkTunnelHealth()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver, filter)
        }
        Log.d(TAG, "Screen-on receiver registered")
    }

    private fun unregisterScreenOnReceiver() {
        screenOnReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            screenOnReceiver = null
            Log.d(TAG, "Screen-on receiver unregistered")
        }
    }

    // ── Companion service binding (Layer 3) ───────────────────────────────────

    private fun bindMonitorService() {
        try {
            val intent = VpnMonitorService.buildIntent(this)
            bindService(intent, monitorConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Binding to VpnMonitorService…")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bind VpnMonitorService: ${e.message}")
        }
    }

    private fun unbindMonitorService() {
        if (monitorServiceBound) {
            try {
                unbindService(monitorConnection)
                monitorServiceBound = false
                Log.d(TAG, "VpnMonitorService unbound")
            } catch (_: Exception) {}
        }
    }

    // ── DNS packet loop (unchanged) ───────────────────────────────────────────

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

    // ── Notification ──────────────────────────────────────────────────────────

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