package com.curbme.app.service.vpn

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Locale

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
object DnsPacketParser {
    private const val TAG = "DnsPacketParser"

    // IP protocol numbers
    private const val PROTOCOL_UDP = 17

    // DNS port
    private const val PORT_DNS = 53

    // DNS response flags
    private const val DNS_FLAG_QR_RESPONSE = 0x8000
    private const val DNS_FLAG_RD = 0x0100
    private const val DNS_FLAG_RA = 0x0080
    private const val DNS_RCODE_NXDOMAIN = 0x0003
    private const val DNS_RCODE_NOERROR = 0x0000

    const val TYPE_A: Int = 1
    const val TYPE_AAAA: Int = 28

    /**
     * Parses a raw IP packet from the VPN tunnel.
     * Returns null if the packet is not a DNS query.
     */
    @JvmStatic
    fun parse(raw: ByteArray, length: Int): DnsQuery? {
        if (length < 28) return null // Minimum: 20 IP + 8 UDP


        // ── IPv4 Header ───────────────────────────────────────────────────────
        val version = (raw[0].toInt() ushr 4) and 0xF
        if (version != 4) return null

        val ihl = (raw[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = raw[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null

        val srcIp = Arrays.copyOfRange(raw, 12, 16)
        val dstIp = Arrays.copyOfRange(raw, 16, 20)

        // ── UDP Header ────────────────────────────────────────────────────────
        val srcPort = ((raw[ihl].toInt() and 0xFF) shl 8) or (raw[ihl + 1].toInt() and 0xFF)
        val dstPort = ((raw[ihl + 2].toInt() and 0xFF) shl 8) or (raw[ihl + 3].toInt() and 0xFF)

        if (dstPort != PORT_DNS) return null

        // ── DNS Payload ───────────────────────────────────────────────────────
        val dnsOffset = ihl + 8
        if (length < dnsOffset + 12) return null

        val transactionId =
            ((raw[dnsOffset].toInt() and 0xFF) shl 8) or (raw[dnsOffset + 1].toInt() and 0xFF)

        val flags =
            ((raw[dnsOffset + 2].toInt() and 0xFF) shl 8) or (raw[dnsOffset + 3].toInt() and 0xFF)
        if ((flags and 0x8000) != 0) return null // It's a response, not a query


        val questionCount =
            ((raw[dnsOffset + 4].toInt() and 0xFF) shl 8) or (raw[dnsOffset + 5].toInt() and 0xFF)
        if (questionCount == 0) return null

        val questionStart = dnsOffset + 12
        val parsedDomain = parseDomainName(raw, questionStart, length)
        if (parsedDomain == null) return null

        val afterDomain = parsedDomain.offset
        if (afterDomain + 4 > length) return null

        val queryType =
            ((raw[afterDomain].toInt() and 0xFF) shl 8) or (raw[afterDomain + 1].toInt() and 0xFF)

        return DnsQuery(
            transactionId, parsedDomain.domain.lowercase(Locale.getDefault()), queryType, dnsOffset,
            srcIp, dstIp, srcPort, dstPort, raw.copyOf(length), length
        )
    }

    @JvmStatic
    fun buildNxDomainResponse(query: DnsQuery): ByteArray {
        val dnsResponse =
            buildDnsResponse(query, DNS_RCODE_NXDOMAIN, mutableListOf())
        return wrapInIpUdp(query.dstIp, query.srcIp, query.dstPort, query.srcPort, dnsResponse)
    }

    @JvmStatic
    fun buildARecordResponse(query: DnsQuery, ipAddress: String?): ByteArray {
        try {
            val ip = InetAddress.getByName(ipAddress).getAddress()
            val answer = buildARecord(ip, 300)
            val dnsResponse = buildDnsResponse(
                query,
                DNS_RCODE_NOERROR,
                mutableListOf(answer)
            )
            return wrapInIpUdp(query.dstIp, query.srcIp, query.dstPort, query.srcPort, dnsResponse)
        } catch (e: Exception) {
            return buildNxDomainResponse(query)
        }
    }

    @JvmStatic
    fun wrapUpstreamResponse(originalQuery: DnsQuery, dnsResponse: ByteArray): ByteArray {
        return wrapInIpUdp(
            originalQuery.dstIp,
            originalQuery.srcIp,
            originalQuery.dstPort,
            originalQuery.srcPort,
            dnsResponse
        )
    }

    private fun parseDomainName(
        packet: ByteArray,
        startOffset: Int,
        length: Int
    ): DomainParseResult? {
        val sb = StringBuilder()
        var offset = startOffset
        var jumped = false
        var jumpCount = 0
        var finalOffset = startOffset

        while (offset < length) {
            val labelLen = packet[offset].toInt() and 0xFF

            if ((labelLen and 0xC0) == 0xC0) {
                if (offset + 1 >= length) return null
                if (!jumped) finalOffset = offset + 2
                val pointer = ((labelLen and 0x3F) shl 8) or (packet[offset + 1].toInt() and 0xFF)
                offset = pointer
                jumped = true
                jumpCount++
                if (jumpCount > 10) return null
                continue
            }

            if (labelLen == 0) {
                if (!jumped) finalOffset = offset + 1
                break
            }

            offset++
            if (offset + labelLen > length) return null
            if (sb.length > 0) sb.append('.')
            sb.append(String(packet, offset, labelLen, StandardCharsets.UTF_8))
            offset += labelLen
        }

        return if (sb.length > 0) DomainParseResult(sb.toString(), finalOffset) else null
    }

    private fun buildDnsResponse(
        query: DnsQuery,
        rcode: Int,
        answers: MutableList<ByteArray>
    ): ByteArray {
        val buf = ByteBuffer.allocate(512)

        buf.putShort(query.transactionId.toShort())
        val flags = (DNS_FLAG_QR_RESPONSE or DNS_FLAG_RD or DNS_FLAG_RA or rcode).toShort()
        buf.putShort(flags)

        buf.putShort(1.toShort()) // QDCOUNT
        buf.putShort(answers.size.toShort()) // ANCOUNT
        buf.putShort(0.toShort()) // NSCOUNT
        buf.putShort(0.toShort()) // ARCOUNT

        val rawDns = query.rawPacket
        val questionStart = query.dnsPayloadOffset + 12
        var pos = questionStart

        while (pos < query.rawLength) {
            val len = rawDns[pos].toInt() and 0xFF
            buf.put(rawDns[pos])
            pos++
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                buf.put(rawDns[pos])
                pos++
                break
            }
            for (i in 0..<len) {
                buf.put(rawDns[pos++])
            }
        }

        if (pos + 4 <= query.rawLength) {
            buf.put(rawDns, pos, 4)
        }

        for (answer in answers) {
            buf.put(answer)
        }

        return Arrays.copyOfRange(buf.array(), 0, buf.position())
    }

    private fun buildARecord(ip: ByteArray, ttl: Int): ByteArray {
        val buf = ByteBuffer.allocate(256)
        buf.put(0xC0.toByte())
        buf.put(0x0C.toByte())
        buf.putShort(TYPE_A.toShort())
        buf.putShort(1.toShort())
        buf.putInt(ttl)
        buf.putShort(4.toShort())
        buf.put(ip)
        return Arrays.copyOfRange(buf.array(), 0, buf.position())
    }

    private fun wrapInIpUdp(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val udpLen = 8 + dnsPayload.size
        val ipLen = 20 + udpLen
        val buf = ByteBuffer.allocate(ipLen)

        buf.put(0x45.toByte())
        buf.put(0.toByte())
        buf.putShort(ipLen.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(PROTOCOL_UDP.toByte())
        buf.putShort(0.toShort())
        buf.put(srcIp)
        buf.put(dstIp)

        val ipHeader = Arrays.copyOfRange(buf.array(), 0, 20)
        val checksum = ipChecksum(ipHeader)
        buf.array()[10] = (checksum ushr 8).toByte()
        buf.array()[11] = (checksum and 0xFF).toByte()

        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0.toShort())

        buf.put(dnsPayload)

        return Arrays.copyOfRange(buf.array(), 0, ipLen)
    }

    private fun ipChecksum(header: ByteArray): Int {
        var sum = 0
        var i = 0
        while (i < header.size) {
            val word = ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    /**
     * POJO for holding parsed query data. (Replacing Kotlin data class)
     */
    class DnsQuery(
        val transactionId: Int,
        @JvmField val domain: String?,
        @JvmField val queryType: Int,
        @JvmField val dnsPayloadOffset: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        @JvmField val rawPacket: ByteArray,
        @JvmField val rawLength: Int
    )

    // ── Private helpers ───────────────────────────────────────────────────────
    private class DomainParseResult(var domain: String, var offset: Int)
}