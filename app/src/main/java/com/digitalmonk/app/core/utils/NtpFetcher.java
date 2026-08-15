// Fetches time from time.google.com via NTP (UDP port 123)
// Returns NTP epoch ms, or -1 on failure
// Has 2 fallback servers: time.cloudflare.com, pool.ntp.org
// Static method: NtpFetcher.fetchNtpTime()
// Must be called off main thread (already handled by callers)


package com.digitalmonk.app.core.utils;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class NtpFetcher {

    private static final String TAG = "NtpFetcher";
    private static final int    NTP_PORT        = 123;
    private static final int    NTP_PACKET_SIZE = 48;
    private static final int    TIMEOUT_MS      = 4000;

    // Difference between NTP epoch (1900) and Unix epoch (1970) in seconds
    private static final long NTP_UNIX_OFFSET = 2208988800L;

    private static final String[] SERVERS = {
            "time.google.com",
            "time.cloudflare.com",
            "pool.ntp.org"
    };

    /**
     * Fetches current time from NTP servers.
     * Tries each server in order, returns first success.
     *
     * @return Unix epoch milliseconds, or -1 if all servers failed.
     * MUST be called off the main thread.
     */
    public static long fetchNtpTime() {
        for (String server : SERVERS) {
            long result = queryServer(server);
            if (result > 0) {
                Log.i(TAG, "NTP time fetched from " + server + " → " + result);
                return result;
            }
        }
        Log.w(TAG, "All NTP servers failed");
        return -1;
    }

    private static long queryServer(String server) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);

            // Build NTP request packet
            byte[] buffer = new byte[NTP_PACKET_SIZE];
            // LI=0, VN=3, Mode=3 (client)
            buffer[0] = 0x1B;

            InetAddress address = InetAddress.getByName(server);
            DatagramPacket request = new DatagramPacket(
                    buffer, buffer.length, address, NTP_PORT
            );

            // Record time just before sending (T1)
            long requestTime = System.currentTimeMillis();
            socket.send(request);

            // Receive response
            DatagramPacket response = new DatagramPacket(
                    new byte[NTP_PACKET_SIZE], NTP_PACKET_SIZE
            );
            socket.receive(response);

            // Record time just after receiving (T4)
            long responseTime = System.currentTimeMillis();

            byte[] data = response.getData();

            // Transmit Timestamp is at bytes 40–47
            // Extract seconds (bytes 40–43) and fraction (bytes 44–47)
            long seconds  = extractUnsignedInt(data, 40);
            long fraction = extractUnsignedInt(data, 44);

            // Convert NTP timestamp to Unix epoch ms
            long ntpMs = ((seconds - NTP_UNIX_OFFSET) * 1000L)
                    + (fraction * 1000L / 0x100000000L);

            // Compensate for network round-trip (add half the RTT)
            long rtt    = responseTime - requestTime;
            long result = ntpMs + (rtt / 2);

            Log.d(TAG, server + " → ntpMs=" + ntpMs + " rtt=" + rtt + "ms");
            return result;

        } catch (Exception e) {
            Log.w(TAG, "NTP query failed for " + server + ": " + e.getMessage());
            return -1;
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * Reads 4 bytes from data[offset] as an unsigned 32-bit integer.
     */
    private static long extractUnsignedInt(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }
}