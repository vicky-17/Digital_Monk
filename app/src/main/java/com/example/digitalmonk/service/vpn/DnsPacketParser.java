package com.example.digitalmonk.service.vpn;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Why we made this file:
 * The Android VpnService operates at Layer 3 (Network Layer). It doesn't give you
 * nice strings like "youtube.com"; it gives you raw arrays of bytes representing
 * IP packets.
 *
 * This utility class is the "Translator." It takes a raw `byte[]`, slices off the
 * IP and UDP headers, parses the complex DNS protocol format to find the domain
 * string, and can re-package a response (like an NXDOMAIN block) back into raw bytes.
 */
public class DnsPacketParser {

    private static final String TAG = "DnsPacketParser";

    // IP protocol numbers
    private static final int PROTOCOL_UDP = 17;
    // DNS port
    private static final int PORT_DNS = 53;

    // DNS response flags
    private static final int DNS_FLAG_QR_RESPONSE = 0x8000;
    private static final int DNS_FLAG_RD = 0x0100;
    private static final int DNS_FLAG_RA = 0x0080;
    private static final int DNS_RCODE_NXDOMAIN = 0x0003;
    private static final int DNS_RCODE_NOERROR = 0x0000;

    public static final int TYPE_A = 1;
    public static final int TYPE_AAAA = 28;

    /**
     * Private constructor for Utility Class.
     */
    private DnsPacketParser() {}

    /**
     * POJO for holding parsed query data. (Replacing Kotlin data class)
     */
    public static class DnsQuery {
        public final int transactionId;
        public final String domain;
        public final int queryType;
        public final int dnsPayloadOffset;
        public final byte[] srcIp;
        public final byte[] dstIp;
        public final int srcPort;
        public final int dstPort;
        public final byte[] rawPacket;
        public final int rawLength;

        public DnsQuery(int transactionId, String domain, int queryType, int dnsPayloadOffset,
                        byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] rawPacket, int rawLength) {
            this.transactionId = transactionId;
            this.domain = domain;
            this.queryType = queryType;
            this.dnsPayloadOffset = dnsPayloadOffset;
            this.srcIp = srcIp;
            this.dstIp = dstIp;
            this.srcPort = srcPort;
            this.dstPort = dstPort;
            this.rawPacket = rawPacket;
            this.rawLength = rawLength;
        }
    }

    /**
     * Parses a raw IP packet from the VPN tunnel.
     * Returns null if the packet is not a DNS query.
     */
    public static DnsQuery parse(byte[] raw, int length) {
        if (length < 28) return null; // Minimum: 20 IP + 8 UDP

        // ── IPv4 Header ───────────────────────────────────────────────────────
        int version = (raw[0] >>> 4) & 0xF;
        if (version != 4) return null;

        int ihl = (raw[0] & 0x0F) * 4;
        if (ihl < 20 || length < ihl + 8) return null;

        int protocol = raw[9] & 0xFF;
        if (protocol != PROTOCOL_UDP) return null;

        byte[] srcIp = Arrays.copyOfRange(raw, 12, 16);
        byte[] dstIp = Arrays.copyOfRange(raw, 16, 20);

        // ── UDP Header ────────────────────────────────────────────────────────
        int srcPort = ((raw[ihl] & 0xFF) << 8) | (raw[ihl + 1] & 0xFF);
        int dstPort = ((raw[ihl + 2] & 0xFF) << 8) | (raw[ihl + 3] & 0xFF);

        if (dstPort != PORT_DNS) return null;

        // ── DNS Payload ───────────────────────────────────────────────────────
        int dnsOffset = ihl + 8;
        if (length < dnsOffset + 12) return null;

        int transactionId = ((raw[dnsOffset] & 0xFF) << 8) | (raw[dnsOffset + 1] & 0xFF);

        int flags = ((raw[dnsOffset + 2] & 0xFF) << 8) | (raw[dnsOffset + 3] & 0xFF);
        if ((flags & 0x8000) != 0) return null; // It's a response, not a query

        int questionCount = ((raw[dnsOffset + 4] & 0xFF) << 8) | (raw[dnsOffset + 5] & 0xFF);
        if (questionCount == 0) return null;

        int questionStart = dnsOffset + 12;
        DomainParseResult parsedDomain = parseDomainName(raw, questionStart, length);
        if (parsedDomain == null) return null;

        int afterDomain = parsedDomain.offset;
        if (afterDomain + 4 > length) return null;

        int queryType = ((raw[afterDomain] & 0xFF) << 8) | (raw[afterDomain + 1] & 0xFF);

        return new DnsQuery(transactionId, parsedDomain.domain.toLowerCase(), queryType, dnsOffset,
                srcIp, dstIp, srcPort, dstPort, Arrays.copyOf(raw, length), length);
    }

    public static byte[] buildNxDomainResponse(DnsQuery query) {
        byte[] dnsResponse = buildDnsResponse(query, DNS_RCODE_NXDOMAIN, Collections.emptyList());
        return wrapInIpUdp(query.dstIp, query.srcIp, query.dstPort, query.srcPort, dnsResponse);
    }

    public static byte[] buildARecordResponse(DnsQuery query, String ipAddress) {
        try {
            byte[] ip = InetAddress.getByName(ipAddress).getAddress();
            byte[] answer = buildARecord(ip, 300);
            byte[] dnsResponse = buildDnsResponse(query, DNS_RCODE_NOERROR, Collections.singletonList(answer));
            return wrapInIpUdp(query.dstIp, query.srcIp, query.dstPort, query.srcPort, dnsResponse);
        } catch (Exception e) {
            return buildNxDomainResponse(query);
        }
    }

    public static byte[] wrapUpstreamResponse(DnsQuery originalQuery, byte[] dnsResponse) {
        return wrapInIpUdp(originalQuery.dstIp, originalQuery.srcIp, originalQuery.dstPort, originalQuery.srcPort, dnsResponse);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static class DomainParseResult {
        String domain;
        int offset;
        DomainParseResult(String d, int o) { domain = d; offset = o; }
    }

    private static DomainParseResult parseDomainName(byte[] packet, int startOffset, int length) {
        StringBuilder sb = new StringBuilder();
        int offset = startOffset;
        boolean jumped = false;
        int jumpCount = 0;
        int finalOffset = startOffset;

        while (offset < length) {
            int labelLen = packet[offset] & 0xFF;

            if ((labelLen & 0xC0) == 0xC0) {
                if (offset + 1 >= length) return null;
                if (!jumped) finalOffset = offset + 2;
                int pointer = ((labelLen & 0x3F) << 8) | (packet[offset + 1] & 0xFF);
                offset = pointer;
                jumped = true;
                jumpCount++;
                if (jumpCount > 10) return null;
                continue;
            }

            if (labelLen == 0) {
                if (!jumped) finalOffset = offset + 1;
                break;
            }

            offset++;
            if (offset + labelLen > length) return null;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(packet, offset, labelLen, StandardCharsets.UTF_8));
            offset += labelLen;
        }

        return sb.length() > 0 ? new DomainParseResult(sb.toString(), finalOffset) : null;
    }

    private static byte[] buildDnsResponse(DnsQuery query, int rcode, List<byte[]> answers) {
        ByteBuffer buf = ByteBuffer.allocate(512);

        buf.putShort((short) query.transactionId);
        short flags = (short) (DNS_FLAG_QR_RESPONSE | DNS_FLAG_RD | DNS_FLAG_RA | rcode);
        buf.putShort(flags);

        buf.putShort((short) 1); // QDCOUNT
        buf.putShort((short) answers.size()); // ANCOUNT
        buf.putShort((short) 0); // NSCOUNT
        buf.putShort((short) 0); // ARCOUNT

        byte[] rawDns = query.rawPacket;
        int questionStart = query.dnsPayloadOffset + 12;
        int pos = questionStart;

        while (pos < query.rawLength) {
            int len = rawDns[pos] & 0xFF;
            buf.put(rawDns[pos]);
            pos++;
            if (len == 0) break;
            if ((len & 0xC0) == 0xC0) { buf.put(rawDns[pos]); pos++; break; }
            for (int i = 0; i < len; i++) {
                buf.put(rawDns[pos++]);
            }
        }

        if (pos + 4 <= query.rawLength) {
            buf.put(rawDns, pos, 4);
        }

        for (byte[] answer : answers) {
            buf.put(answer);
        }

        return Arrays.copyOfRange(buf.array(), 0, buf.position());
    }

    private static byte[] buildARecord(byte[] ip, int ttl) {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put((byte) 0xC0);
        buf.put((byte) 0x0C);
        buf.putShort((short) TYPE_A);
        buf.putShort((short) 1);
        buf.putInt(ttl);
        buf.putShort((short) 4);
        buf.put(ip);
        return Arrays.copyOfRange(buf.array(), 0, buf.position());
    }

    private static byte[] wrapInIpUdp(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] dnsPayload) {
        int udpLen = 8 + dnsPayload.length;
        int ipLen = 20 + udpLen;
        ByteBuffer buf = ByteBuffer.allocate(ipLen);

        buf.put((byte) 0x45);
        buf.put((byte) 0);
        buf.putShort((short) ipLen);
        buf.putShort((short) 0);
        buf.putShort((short) 0x4000);
        buf.put((byte) 64);
        buf.put((byte) PROTOCOL_UDP);
        buf.putShort((short) 0);
        buf.put(srcIp);
        buf.put(dstIp);

        byte[] ipHeader = Arrays.copyOfRange(buf.array(), 0, 20);
        int checksum = ipChecksum(ipHeader);
        buf.array()[10] = (byte) (checksum >>> 8);
        buf.array()[11] = (byte) (checksum & 0xFF);

        buf.putShort((short) srcPort);
        buf.putShort((short) dstPort);
        buf.putShort((short) udpLen);
        buf.putShort((short) 0);

        buf.put(dnsPayload);

        return Arrays.copyOfRange(buf.array(), 0, ipLen);
    }

    private static int ipChecksum(byte[] header) {
        int sum = 0;
        for (int i = 0; i < header.length; i += 2) {
            int word = ((header[i] & 0xFF) << 8) | (header[i + 1] & 0xFF);
            sum += word;
        }
        while ((sum >>> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >>> 16);
        }
        return (~sum) & 0xFFFF;
    }
}