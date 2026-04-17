package org.pihole.android.core.dns.codec

import java.nio.ByteBuffer
import java.nio.ByteOrder

object DnsCodec {

    fun parseQuestions(packet: ByteArray): Pair<Int, List<DnsQuestion>> {
        require(packet.size >= 12) { "DNS packet too short" }
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        val id = buf.short.toInt() and 0xFFFF
        val flags = buf.short.toInt() and 0xFFFF
        val qr = (flags shr 15) and 1
        require(qr == 0) { "Not a query" }
        val qdCount = buf.short.toInt() and 0xFFFF
        buf.short // anCount
        buf.short // nsCount
        buf.short // arCount
        val questions = mutableListOf<DnsQuestion>()
        repeat(qdCount) {
            val (name, newPos) = readName(packet, buf.position())
            buf.position(newPos)
            val qtype = buf.short.toInt() and 0xFFFF
            val qclass = buf.short.toInt() and 0xFFFF
            questions += DnsQuestion(name, qtype, qclass)
        }
        return id to questions
    }

    fun readName(packet: ByteArray, offset: Int): Pair<String, Int> {
        var pos = offset
        val labels = mutableListOf<String>()
        while (true) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) {
                pos++
                break
            }
            if ((len and 0xC0) == 0xC0) {
                val ptr = ((len and 0x3F) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                val (compressed, _) = readName(packet, ptr)
                labels.addAll(compressed.split('.').filter { it.isNotEmpty() })
                pos += 2
                break
            }
            pos++
            val label = String(packet, pos, len, Charsets.US_ASCII)
            labels.add(label)
            pos += len
        }
        val fqdn = if (labels.isEmpty()) "." else labels.joinToString(".") + "."
        return fqdn to pos
    }

    fun buildResponseQuery(
        queryId: Int,
        question: DnsQuestion,
        answerPayload: ByteArray?,
    ): ByteArray {
        val headerSize = 12
        val qnameWire = encodeName(question.qname)
        val questionSize = qnameWire.size + 4
        val answerSize = answerPayload?.size ?: 0
        val total = headerSize + questionSize + answerSize
        val buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(queryId.toShort())
        val flags = 0x8180 // QR=1, OPCODE=0, AA=1, RA=1
        buf.putShort(flags.toShort())
        buf.putShort(1) // QDCOUNT
        buf.putShort(if (answerPayload != null) 1 else 0) // ANCOUNT
        buf.putShort(0)
        buf.putShort(0)
        buf.put(qnameWire)
        buf.putShort(question.qtype.toShort())
        buf.putShort(question.qclass.toShort())
        if (answerPayload != null) {
            buf.put(answerPayload)
        }
        return buf.array()
    }

    fun encodeName(fqdn: String): ByteArray {
        val normalized = fqdn.trim().lowercase()
        val withoutDot = normalized.removeSuffix(".")
        if (withoutDot.isEmpty()) {
            return byteArrayOf(0)
        }
        val parts = withoutDot.split('.')
        val out = java.io.ByteArrayOutputStream()
        for (p in parts) {
            val bytes = p.toByteArray(Charsets.US_ASCII)
            require(bytes.size <= 63) { "Label too long" }
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        return out.toByteArray()
    }

    fun buildARecordAnswer(name: String, ttl: Int, ipv4: ByteArray): ByteArray {
        require(ipv4.size == 4)
        val nameWire = encodeName(name)
        val rdlength = 4
        val buf = ByteBuffer.allocate(nameWire.size + 10 + rdlength).order(ByteOrder.BIG_ENDIAN)
        buf.put(nameWire)
        buf.putShort(DnsConstants.QTYPE_A.toShort())
        buf.putShort(DnsConstants.QCLASS_IN.toShort())
        buf.putInt(ttl)
        buf.putShort(rdlength.toShort())
        buf.put(ipv4)
        return buf.array()
    }

    fun buildCnameRecordAnswer(ownerFqdn: String, ttl: Int, targetFqdn: String): ByteArray {
        val nameWire = encodeName(ownerFqdn)
        val targetWire = encodeName(targetFqdn)
        val rdlength = targetWire.size
        val buf = ByteBuffer.allocate(nameWire.size + 10 + rdlength).order(ByteOrder.BIG_ENDIAN)
        buf.put(nameWire)
        buf.putShort(DnsConstants.QTYPE_CNAME.toShort())
        buf.putShort(DnsConstants.QCLASS_IN.toShort())
        buf.putInt(ttl)
        buf.putShort(rdlength.toShort())
        buf.put(targetWire)
        return buf.array()
    }

    fun buildAaaaRecordAnswer(name: String, ttl: Int, ipv6: ByteArray): ByteArray {
        require(ipv6.size == 16)
        val nameWire = encodeName(name)
        val rdlength = 16
        val buf = ByteBuffer.allocate(nameWire.size + 10 + rdlength).order(ByteOrder.BIG_ENDIAN)
        buf.put(nameWire)
        buf.putShort(DnsConstants.QTYPE_AAAA.toShort())
        buf.putShort(DnsConstants.QCLASS_IN.toShort())
        buf.putInt(ttl)
        buf.putShort(rdlength.toShort())
        buf.put(ipv6)
        return buf.array()
    }

    fun ipv4Bytes(a: Int, b: Int, c: Int, d: Int): ByteArray =
        byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    fun nullIpv4(): ByteArray = byteArrayOf(0, 0, 0, 0)

    fun nullIpv6(): ByteArray = ByteArray(16)

    /**
     * Minimal SERVFAIL response: same ID and question as [query], QR=1, RCODE=SERVFAIL, no answers.
     */
    fun buildServFailFromQuery(query: ByteArray): ByteArray {
        require(query.size >= 12) { "DNS query too short" }
        val qdCount = readUInt16(query, 4)
        require(qdCount == 1) { "Only single-question SERVFAIL supported" }
        val questionStart = 12
        require(query.size > questionStart) { "Missing question" }
        val questionEnd = skipQuestion(query, questionStart)
        val question = query.copyOfRange(questionStart, questionEnd)
        val total = 12 + question.size
        val buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(readUInt16(query, 0).toShort())
        val flags = 0x8000 or DnsConstants.RCODE_SERVFAIL
        buf.putShort(flags.toShort())
        buf.putShort(1)
        buf.putShort(0)
        buf.putShort(0)
        buf.putShort(0)
        buf.put(question)
        return buf.array()
    }

    /** True if [response] looks like a DNS reply for [queryId] (QR=1, min length). */
    fun isValidResponseForQuery(queryId: Int, response: ByteArray): Boolean {
        if (response.size < 12) return false
        val id = readUInt16(response, 0)
        if (id != queryId) return false
        val flags = readUInt16(response, 2)
        val qr = (flags shr 15) and 1
        if (qr != 1) return false
        return true
    }

    fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    /**
     * Position in [packet] immediately after the question section (QD).
     */
    fun positionAfterQuestions(packet: ByteArray, qdCount: Int, startOffset: Int = 12): Int {
        var pos = startOffset
        repeat(qdCount) {
            val (_, end) = readName(packet, pos)
            pos = end + 4
        }
        return pos
    }

    /**
     * Invokes [consumer] with the target name of each CNAME RDATA in the answer section.
     */
    fun forEachCnameTargetInAnswers(packet: ByteArray, consumer: (String) -> Unit) {
        if (packet.size < 12) return
        val qdCount = readUInt16(packet, 4)
        val anCount = readUInt16(packet, 6)
        var pos = positionAfterQuestions(packet, qdCount)
        repeat(anCount) {
            if (pos >= packet.size) return
            val (_, nameEnd) = readName(packet, pos)
            if (nameEnd + 10 > packet.size) return
            val type = readUInt16(packet, nameEnd)
            val rdLength = readUInt16(packet, nameEnd + 8)
            val rdataStart = nameEnd + 10
            if (rdataStart + rdLength > packet.size) return
            if (type == DnsConstants.QTYPE_CNAME) {
                val (target, _) = readName(packet, rdataStart)
                consumer(target)
            }
            pos = rdataStart + rdLength
        }
    }

    private fun skipQuestion(packet: ByteArray, offset: Int): Int {
        var pos = offset
        while (pos < packet.size) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) {
                return pos + 1 + 4
            }
            if ((len and 0xC0) == 0xC0) {
                return pos + 2 + 4
            }
            pos += 1 + len
        }
        error("unterminated name")
    }
}
