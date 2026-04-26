package com.example.digitalmonk.service.vpn

import android.util.Log
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Parses raw IPv4 packets from the VPN tunnel and extracts DNS query information.
 *
 * Packet layout we handle:
 *   [IPv4 Header (20+ bytes)] [UDP Header (8 bytes)] [DNS Payload]
 *
 * All packets in our tunnel are destined for 10.0.0.2:53 (our fake DNS server),
 * so we only need to handle DNS-over-UDP queries.
 */
object DnsPacketParser {

    private const val TAG = "DnsPacketParser"

    // IP protocol numbers
    private const val PROTOCOL_UDP = 17

    // DNS port
    private const val PORT_DNS = 53

    // DNS response flags
    private const val DNS_FLAG_QR_RESPONSE = 0x8000  // QR bit = response
    private const val DNS_FLAG_AA          = 0x0400  // Authoritative Answer
    private const val DNS_FLAG_RD          = 0x0100  // Recursion Desired
    private const val DNS_FLAG_RA          = 0x0080  // Recursion Available
    private const val DNS_RCODE_NXDOMAIN   = 0x0003  // Name does not exist
    private const val DNS_RCODE_NOERROR    = 0x0000

    // DNS record types
    const val TYPE_A    = 1   // IPv4 address
    const val TYPE_AAAA = 28  // IPv6 address

    /**
     * Result of parsing a DNS packet.
     */
    data class DnsQuery(
        val transactionId: Int,
        val domain: String,
        val queryType: Int,          // TYPE_A, TYPE_AAAA, etc.
        val dnsPayloadOffset: Int,   // Where DNS data starts in the raw packet
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val rawPacket: ByteArray,
        val rawLength: Int
    )

    /**
     * Parses a raw IP packet from the VPN tunnel.
     * Returns null if the packet is not a DNS query.
     */
    fun parse(raw: ByteArray, length: Int): DnsQuery? {
        if (length < 28) return null  // Minimum: 20 IP + 8 UDP

        // ── IPv4 Header ───────────────────────────────────────────────────────
        val version = (raw[0].toInt() ushr 4) and 0xF
        if (version != 4) return null  // We only handle IPv4

        val ihl = (raw[0].toInt() and 0x0F) * 4  // Header length in bytes
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = raw[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null  // Only UDP

        val srcIp = raw.copyOfRange(12, 16)
        val dstIp = raw.copyOfRange(16, 20)

        // ── UDP Header ────────────────────────────────────────────────────────
        val srcPort = ((raw[ihl].toInt() and 0xFF) shl 8) or (raw[ihl + 1].toInt() and 0xFF)
        val dstPort = ((raw[ihl + 2].toInt() and 0xFF) shl 8) or (raw[ihl + 3].toInt() and 0xFF)

        if (dstPort != PORT_DNS) return null  // Only DNS queries

        // ── DNS Payload ───────────────────────────────────────────────────────
        val dnsOffset = ihl + 8
        if (length < dnsOffset + 12) return null  // DNS header is 12 bytes minimum

        val transactionId = ((raw[dnsOffset].toInt() and 0xFF) shl 8) or
                (raw[dnsOffset + 1].toInt() and 0xFF)

        // Check it's a query (QR bit = 0)
        val flags = ((raw[dnsOffset + 2].toInt() and 0xFF) shl 8) or
                (raw[dnsOffset + 3].toInt() and 0xFF)
        if ((flags and 0x8000) != 0) return null  // It's a response, not a query

        val questionCount = ((raw[dnsOffset + 4].toInt() and 0xFF) shl 8) or
                (raw[dnsOffset + 5].toInt() and 0xFF)
        if (questionCount == 0) return null

        // Parse the first question's domain name
        val questionStart = dnsOffset + 12
        val (domain, afterDomain) = parseDomainName(raw, questionStart, length) ?: return null

        if (afterDomain + 4 > length) return null
        val queryType = ((raw[afterDomain].toInt() and 0xFF) shl 8) or
                (raw[afterDomain + 1].toInt() and 0xFF)

        return DnsQuery(
            transactionId = transactionId,
            domain = domain.lowercase(),
            queryType = queryType,
            dnsPayloadOffset = dnsOffset,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            rawPacket = raw.copyOf(length),
            rawLength = length
        )
    }

    /**
     * Builds a DNS NXDOMAIN response packet (blocked domain).
     * The response is a complete IPv4/UDP/DNS packet ready to write back to the tunnel.
     */
    fun buildNxDomainResponse(query: DnsQuery): ByteArray {
        val dnsResponse = buildDnsResponse(query, rcode = DNS_RCODE_NXDOMAIN, answers = emptyList())
        return wrapInIpUdp(
            srcIp = query.dstIp,   // Swap src/dst for the response
            dstIp = query.srcIp,
            srcPort = query.dstPort,
            dstPort = query.srcPort,
            dnsPayload = dnsResponse
        )
    }

    /**
     * Builds a DNS A-record response pointing to [ipAddress].
     * Used for SafeSearch enforcement (redirect google.com to forcesafesearch.google.com IP).
     */
    fun buildARecordResponse(query: DnsQuery, ipAddress: String): ByteArray {
        val ip = InetAddress.getByName(ipAddress).address
        val answer = buildARecord(query.domain, ip, ttl = 300)
        val dnsResponse = buildDnsResponse(query, rcode = DNS_RCODE_NOERROR, answers = listOf(answer))
        return wrapInIpUdp(
            srcIp = query.dstIp,
            dstIp = query.srcIp,
            srcPort = query.dstPort,
            dstPort = query.srcPort,
            dnsPayload = dnsResponse
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parses a DNS domain name from a packet.
     * Returns Pair(domainString, offsetAfterName) or null on error.
     * Handles DNS compression pointers.
     */
    private fun parseDomainName(packet: ByteArray, startOffset: Int, length: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var offset = startOffset
        var jumped = false
        var jumpCount = 0
        var finalOffset = startOffset

        while (offset < length) {
            val labelLen = packet[offset].toInt() and 0xFF

            // Compression pointer: top 2 bits are 11
            if ((labelLen and 0xC0) == 0xC0) {
                if (offset + 1 >= length) return null
                if (!jumped) finalOffset = offset + 2
                val pointer = ((labelLen and 0x3F) shl 8) or (packet[offset + 1].toInt() and 0xFF)
                offset = pointer
                jumped = true
                jumpCount++
                if (jumpCount > 10) return null  // Prevent infinite loops
                continue
            }

            if (labelLen == 0) {
                if (!jumped) finalOffset = offset + 1
                break
            }

            offset++
            if (offset + labelLen > length) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(packet, offset, labelLen, Charsets.UTF_8))
            offset += labelLen
        }

        return if (sb.isNotEmpty()) Pair(sb.toString(), finalOffset) else null
    }

    private fun buildDnsResponse(
        query: DnsQuery,
        rcode: Int,
        answers: List<ByteArray>
    ): ByteArray {
        val buf = ByteBuffer.allocate(512)

        // Transaction ID
        buf.putShort(query.transactionId.toShort())

        // Flags: QR=1 (response), RD=1 (recursion desired), RA=1 (recursion available), RCODE
        val flags = DNS_FLAG_QR_RESPONSE or DNS_FLAG_RD or DNS_FLAG_RA or rcode
        buf.putShort(flags.toShort())

        // QDCOUNT = 1 (echo back the question)
        buf.putShort(1)
        // ANCOUNT
        buf.putShort(answers.size.toShort())
        // NSCOUNT, ARCOUNT
        buf.putShort(0)
        buf.putShort(0)

        // Echo the original question section (from dnsPayloadOffset + 12 to end of question)
        val rawDns = query.rawPacket
        val questionStart = query.dnsPayloadOffset + 12
        var pos = questionStart
        // Copy domain name labels
        while (pos < query.rawLength) {
            val len = rawDns[pos].toInt() and 0xFF
            buf.put(rawDns[pos])
            pos++
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) { buf.put(rawDns[pos]); pos++; break }
            repeat(len) { buf.put(rawDns[pos++]) }
        }
        // QTYPE and QCLASS (4 bytes)
        if (pos + 4 <= query.rawLength) {
            buf.put(rawDns, pos, 4)
        }

        // Answers
        answers.forEach { buf.put(it) }

        val len = buf.position()
        return buf.array().copyOf(len)
    }

    private fun buildARecord(domain: String, ip: ByteArray, ttl: Int): ByteArray {
        val buf = ByteBuffer.allocate(256)
        // Name — use a pointer to the question (offset 12 in the DNS payload = 0xC00C)
        buf.put(0xC0.toByte())
        buf.put(0x0C.toByte())
        buf.putShort(TYPE_A.toShort())        // TYPE: A
        buf.putShort(1)                        // CLASS: IN
        buf.putInt(ttl)                        // TTL
        buf.putShort(4)                        // RDLENGTH: 4 bytes for IPv4
        buf.put(ip)                            // RDATA: IP address
        return buf.array().copyOf(buf.position())
    }

    /**
     * Wraps a DNS payload in a UDP packet wrapped in an IPv4 packet.
     */
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

        // ── IPv4 Header ───────────────────────────────────────────────────────
        buf.put(0x45.toByte())                      // Version=4, IHL=5
        buf.put(0)                                   // DSCP/ECN
        buf.putShort(ipLen.toShort())               // Total length
        buf.putShort(0)                              // Identification
        buf.putShort(0x4000.toShort())              // Flags: Don't Fragment
        buf.put(64)                                  // TTL
        buf.put(PROTOCOL_UDP.toByte())              // Protocol: UDP
        buf.putShort(0)                              // Checksum (filled below)
        buf.put(srcIp)
        buf.put(dstIp)

        // Calculate IP checksum
        val ipHeader = buf.array().copyOf(20)
        val checksum = ipChecksum(ipHeader)
        buf.array()[10] = (checksum ushr 8).toByte()
        buf.array()[11] = (checksum and 0xFF).toByte()

        // ── UDP Header ────────────────────────────────────────────────────────
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0)                              // UDP checksum (optional for IPv4, 0 = disabled)

        // ── DNS Payload ───────────────────────────────────────────────────────
        buf.put(dnsPayload)

        return buf.array().copyOf(ipLen)
    }

    private fun ipChecksum(header: ByteArray): Int {
        var sum = 0
        for (i in header.indices step 2) {
            val word = ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            sum += word
        }
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }
    /**
     * Wraps a raw upstream DNS response in an IPv4/UDP packet
     * addressed back to the original querying client.
     */
    fun wrapUpstreamResponse(
        originalQuery: DnsQuery,
        dnsResponse: ByteArray
    ): ByteArray = wrapInIpUdp(
        srcIp    = originalQuery.dstIp,
        dstIp    = originalQuery.srcIp,
        srcPort  = originalQuery.dstPort,
        dstPort  = originalQuery.srcPort,
        dnsPayload = dnsResponse
    )



}