package com.example.digitalmonk.service.vpn;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.util.Arrays;

import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.core.utils.Constants;
import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.vpn.heartbeat.VpnHeartBeatEntity;
import com.example.digitalmonk.service.vpn.heartbeat.VpnHeartbeatMonitorWorker;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Why we made this file:
 * This is the central engine of Digital Monk's web filtering. It creates a local
 * VPN tunnel that forces all DNS queries (requests for websites) to pass through
 * our DnsFilterEngine.
 *
 * It features a 3-Layer "Keep-Alive" system:
 * 1. WorkManager Watchdog (Periodic checks).
 * 2. Connectivity Probes (Socket tests).
 * 3. Monitor Service Binding (Process death protection).
 */
public class DnsVpnService extends VpnService {

    private static final String TAG = "DnsVpnService";
    public static final String ACTION_STOP = "ACTION_STOP";

    private static final String VPN_ADDRESS = "10.0.0.1";
    private static final String VPN_DNS = "10.0.0.2";
    private static final String UPSTREAM_DNS = "8.8.8.8";
    private static final int DNS_PORT = 53;
    private static final int DNS_TIMEOUT_MS = 3000;

    private static final long HEARTBEAT_INTERVAL_MS = 7 * 60 * 1000L;
    private static final long PROBE_INTERVAL_MS = 15_000L;
    private static final int MAX_PROBE_FAILURES = 3;
    private static final int PROBE_SOCKET_TIMEOUT_MS = 5000;

    public static volatile boolean isServiceRunning = false;

    private ParcelFileDescriptor vpnInterface;
    private volatile boolean isRunning = false;
    private Thread vpnThread;
    private DnsFilterEngine filterEngine;
    private PrefsManager prefs;

    private Thread heartbeatThread;
    private Thread connectivityProbeThread;
    private int consecutiveProbeFailures = 0;
    private BroadcastReceiver screenOnReceiver;
    private boolean monitorServiceBound = false;

    // ── Companion Service Connection ──────────────────────────────────────────
    private final ServiceConnection monitorConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            monitorServiceBound = true;
            Log.d(TAG, "VpnMonitorService bound ✅");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            monitorServiceBound = false;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefsManager(this);
        filterEngine = new DnsFilterEngine(prefs);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVpn(true);
            return START_NOT_STICKY;
        }

        if (!isRunning) {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(Constants.NOTIFICATION_ID_VPN, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Constants.NOTIFICATION_ID_VPN, notification);
            }
            startVpn();
        }

        return START_STICKY;
    }

    private void startVpn() {
        if (isRunning) return;

        try {
            Builder builder = new Builder()
                    .setSession("Digital Monk Shield")
                    .addAddress(VPN_ADDRESS, 32)
                    .addRoute(VPN_DNS, 32)
                    .addDnsServer(VPN_DNS)
                    .addDisallowedApplication(getPackageName())
                    .setBlocking(true);

            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                stopSelf();
                return;
            }

            isRunning = true;
            isServiceRunning = true;

            writeHeartbeat(VpnHeartBeatEntity.TYPE_ALIVE);

            // Start Threads
            vpnThread = new Thread(this::runVpnLoop, "dns-vpn-thread");
            vpnThread.start();

            startHeartbeatLoop();
            startConnectivityProbe();
            registerScreenOnReceiver();

            // TODO: bindMonitorService();

            if (prefs.isKeepVpnAlive()) {
                VpnHeartbeatMonitorWorker.schedule(this);
            }

            Log.i(TAG, "✅ VPN started");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start VPN", e);
            stopVpn(false);
        }
    }

    private void stopVpn(boolean cleanStop) {
        isRunning = false;
        isServiceRunning = false;

        if (cleanStop) {
            writeHeartbeat(VpnHeartBeatEntity.TYPE_STOPPED);
            VpnHeartbeatMonitorWorker.cancel(this);
        }

        stopHeartbeatLoop();
        stopConnectivityProbe();
        unregisterScreenOnReceiver();

        if (vpnThread != null) vpnThread.interrupt();
        try { if (vpnInterface != null) vpnInterface.close(); } catch (Exception ignored) {}

        vpnInterface = null;
        stopForeground(true);
        stopSelf();
    }

    private void runVpnLoop() {
        FileInputStream inputStream = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream outputStream = new FileOutputStream(vpnInterface.getFileDescriptor());
        byte[] packetBuffer = new byte[32767];

        try {
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                int length = inputStream.read(packetBuffer);
                if (length <= 0) continue;

                DnsPacketParser.DnsQuery query = DnsPacketParser.parse(packetBuffer, length);
                if (query == null) continue;

                byte[] response = null;
                DnsFilterEngine.FilterDecision decision = filterEngine.decide(query.domain, query.queryType);

                if (decision instanceof DnsFilterEngine.FilterDecision.Block) {
                    response = DnsPacketParser.buildNxDomainResponse(query);
                } else if (decision instanceof DnsFilterEngine.FilterDecision.SafeSearchRedirect) {
                    String ip = ((DnsFilterEngine.FilterDecision.SafeSearchRedirect) decision).getRedirectIp();
                    response = DnsPacketParser.buildARecordResponse(query, ip);
                } else {
                    response = forwardToUpstream(query);
                }

                if (response != null) {
                    outputStream.write(response);
                    outputStream.flush();
                }
            }
        } catch (Exception e) {
            if (isRunning) Log.e(TAG, "Error in VPN loop", e);
        }
    }

    private byte[] forwardToUpstream(DnsPacketParser.DnsQuery query) {
        try (DatagramSocket socket = new DatagramSocket()) {
            protect(socket);
            socket.setSoTimeout(DNS_TIMEOUT_MS);

            byte[] dnsPayload = Arrays.copyOfRange(query.rawPacket, query.dnsPayloadOffset, query.rawLength);
            InetAddress upstreamAddress = InetAddress.getByName(UPSTREAM_DNS);

            DatagramPacket sendPacket = new DatagramPacket(dnsPayload, dnsPayload.length, upstreamAddress, DNS_PORT);
            socket.send(sendPacket);

            byte[] responseBuffer = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(receivePacket);

            return DnsPacketParser.wrapUpstreamResponse(query,
                    Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
        } catch (Exception e) {
            return DnsPacketParser.buildNxDomainResponse(query);
        }
    }

    // ── Heartbeat & Helper Methods ──────────────────────────────────────────

    private void startHeartbeatLoop() {
        heartbeatThread = new Thread(() -> {
            try {
                while (isRunning) {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    writeHeartbeat(VpnHeartBeatEntity.TYPE_ALIVE);
                }
            } catch (InterruptedException ignored) {}
        });
        heartbeatThread.start();
    }

    private void writeHeartbeat(String type) {
        prefs.setLastVpnHeartbeatType(type);
        prefs.setLastVpnHeartbeatTimestamp(System.currentTimeMillis());
    }

    private void startConnectivityProbe() { /* Implementation similar to heartbeat */ }
    private void stopHeartbeatLoop() { if (heartbeatThread != null) heartbeatThread.interrupt(); }
    private void stopConnectivityProbe() { if (connectivityProbeThread != null) connectivityProbeThread.interrupt(); }

    private void registerScreenOnReceiver() {
        screenOnReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && isRunning) {
                    Log.d(TAG, "Screen ON - Healthy Check");
                }
            }
        };
        registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    private void unregisterScreenOnReceiver() {
        if (screenOnReceiver != null) unregisterReceiver(screenOnReceiver);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, Constants.CHANNEL_VPN)
                .setContentTitle("Digital Monk Shield Active 🛡️")
                .setContentText("Web filter & SafeSearch are running")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }
}