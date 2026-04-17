package org.pihole.android.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.dns.codec.DnsQuestion
import org.pihole.android.core.tor.TorController
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.db.entity.CompiledSnapshotEntity
import org.pihole.android.data.lists.AdlistSnapshotManifest
import org.pihole.android.data.lists.storage.CompiledSnapshotManifestStore
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.testutil.DnsServiceTestPrep

/**
 * Single on-device run: one [DnsForegroundService] lifetime (avoids repeated Tor cold-start / FGS timeouts),
 * then authoritative checks for local test host, manifest blocking, and Tor DoT upstream.
 */
@RunWith(AndroidJUnit4::class)
class DnsEndToEndInstrumentedTest {

    @Before
    fun ensureStopped() {
        DnsServiceTestPrep.stopAndSettle(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        DnsServiceTestPrep.stopAndSettle(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun dnsEndToEnd_authoritativeChecks_singleServiceLifetime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Gradle `connectedDebugAndroidTest` reinstall can clear app data; seed minimal blocklist so list checks run.
        ensureBlocklistFixtureIfNeeded(context)

        startDnsService(context)
        waitForListenerUdp(context)
        val port = dnsPort(context)

        // 1) Local policy test net (no upstream)
        run {
            val query =
                buildQueryPacket(0xAB10, listOf(DnsQuestion("test.pi-hole.local.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)))
            val response = udpDnsExchange(query, port, timeoutMs = 5_000)
            assertTrue("test.pi-hole.local: expected DNS response", response != null && response.size >= 12)
            assertTrue(
                "test.pi-hole.local: expected A 192.0.2.1, last4=${response!!.takeLast(4).joinToString { "%02x".format(it) }}",
                aRdata192_0_2_1(response),
            )
        }

        // 2) Manifest-driven blocklist (fixture or device Lists → Refresh data)
        run {
            val manifestText = CompiledSnapshotManifestStore.readTextOrNull(context)
            assertTrue("manifest.json missing after fixture", !manifestText.isNullOrBlank())
            val suffixDeny = runCatching { AdlistSnapshotManifest.parseSuffixDenyDomains(manifestText!!) }.getOrElse { emptySet() }
            assertTrue("suffixDeny empty after fixture", suffixDeny.isNotEmpty())

            val blocked = selectBlocked(suffixDeny, 4)
            assertTrue("need at least one usable multi-label blocked FQDN in manifest", blocked.isNotEmpty())

            for ((i, fqdn) in blocked.withIndex()) {
                val query = buildQueryPacket(0xAB20 + i, listOf(DnsQuestion(fqdn, DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)))
                val response = udpDnsExchange(query, port, timeoutMs = 8_000)
                assertTrue(
                    "blocked $fqdn: expected NULL A response",
                    response != null && aRdataAllZero(response),
                )
            }
        }

        // 3) Upstream via Tor DoT — best-effort on real hardware (Tor exits / DoT vary widely).
        if (!waitForTorSocks(TOR_WAIT_MS)) {
            assumeTrue("Tor SOCKS not ready within ${TOR_WAIT_MS}ms — skipping upstream-over-Tor check", false)
        }

        // Try Cloudflare first (often more stable over Tor); example.com as second check.
        // Require at least one success — some Tor exits consistently fail particular CDNs.
        val names = listOf("one.one.one.one.", "example.com.")
        val lastRcodes = Array<String?>(names.size) { null }
        var anyUpstreamOk = false
        for ((i, fqdn) in names.withIndex()) {
            var response: ByteArray? = null
            var upstreamOk = false
            repeat(UPSTREAM_RETRIES) { attempt ->
                val query =
                    buildQueryPacket(0xAC01 + i + attempt * 10, listOf(DnsQuestion(fqdn, DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)))
                // Single forward() can exceed 90s (several DoT endpoints × SOCKS connect timeouts) before .onion succeeds.
                response = udpDnsExchange(query, port, timeoutMs = UPSTREAM_UDP_TIMEOUT_MS)
                val r = response
                lastRcodes[i] = r?.let { readRcode(it).toString() }
                if (r != null &&
                    r.size >= 12 &&
                    readRcode(r) == DnsConstants.RCODE_NOERROR &&
                    hasNonZeroIpv4ARecord(r)
                ) {
                    upstreamOk = true
                    anyUpstreamOk = true
                    return@repeat
                }
                if (attempt < UPSTREAM_RETRIES - 1) Thread.sleep(8_000)
            }
            if (!upstreamOk) {
                android.util.Log.w(
                    "DnsEndToEnd",
                    "upstream $fqdn: no NOERROR+A after $UPSTREAM_RETRIES attempts (last rcode=${response?.let { readRcode(it) }})",
                )
            }
        }
        assumeTrue(
            "Upstream over Tor not verifiable for $names (last rcodes=${lastRcodes.contentToString()}) — skipping",
            anyUpstreamOk,
        )
    }

    private fun dnsPort(context: Context): Int =
        runBlocking { AppPreferences(context).dnsListenPort.first() }

    private fun ensureBlocklistFixtureIfNeeded(context: Context) {
        val db = DatabaseProvider.get(context)
        val snapshot = runBlocking { db.compiledSnapshotDao().getLatest() }
        val manifestText = CompiledSnapshotManifestStore.readTextOrNull(context)
        // Fresh test install often has neither; rare partial state has one without the other.
        if (snapshot != null && !manifestText.isNullOrBlank()) return

        val now = System.currentTimeMillis()
        val json =
            AdlistSnapshotManifest.serializeSuffixDenyDomains(
                listOf(E2E_BLOCKED_FQDN),
            )
        CompiledSnapshotManifestStore.write(context, json)
        runBlocking {
            db.compiledSnapshotDao().insert(
                CompiledSnapshotEntity(
                    createdAt = now,
                    ruleCountExactAllow = 0,
                    ruleCountExactDeny = 0,
                    ruleCountSuffixDeny = 1,
                    ruleCountRegexAllow = 0,
                    ruleCountRegexDeny = 0,
                    checksum = "e2e-instrumented-$now",
                ),
            )
        }
    }

    private fun startDnsService(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, DnsForegroundService::class.java))
    }

    private fun waitForListenerUdp(context: Context) {
        val query =
            android.util.Base64.decode(
                "EjQBAAABAAAAAAAABHRlc3QHcGktaG9sZQVsb2NhbAAAAQAB",
                android.util.Base64.DEFAULT,
            )
        val deadline = System.currentTimeMillis() + LISTENER_WAIT_MS
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                val port = dnsPort(context)
                val r = udpDnsExchange(query, port, timeoutMs = 3_000)
                if (r != null && r.size >= 12) return
            } catch (e: Throwable) {
                lastError = e
            }
            Thread.sleep(200)
        }
        throw AssertionError("DNS listener did not respond in time (lastError=$lastError)")
    }

    private fun waitForTorSocks(maxWaitMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(LOOPBACK, TorController.DEFAULT_SOCKS_PORT), 1_500)
                }
                return true
            } catch (_: Exception) {
                Thread.sleep(500)
            }
        }
        return false
    }

    private fun udpDnsExchange(query: ByteArray, port: Int, timeoutMs: Int): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val addr = InetAddress.getByName(LOOPBACK)
                socket.connect(InetSocketAddress(addr, port))
                socket.send(DatagramPacket(query, query.size))
                val buf = ByteArray(4096)
                val pkt = DatagramPacket(buf, buf.size)
                socket.receive(pkt)
                buf.copyOf(pkt.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildQueryPacket(id: Int, questions: List<DnsQuestion>): ByteArray {
        val bb = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        bb.putShort(id.toShort())
        bb.putShort(0)
        bb.putShort(questions.size.toShort())
        bb.putShort(0)
        bb.putShort(0)
        bb.putShort(0)
        for (q in questions) {
            bb.put(DnsCodec.encodeName(q.qname))
            bb.putShort(q.qtype.toShort())
            bb.putShort(q.qclass.toShort())
        }
        return bb.array().copyOf(bb.position())
    }

    private fun readRcode(packet: ByteArray): Int {
        val flags = readUInt16(packet, 2)
        return flags and 0xF
    }

    private fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    private fun readUInt32(packet: ByteArray, offset: Int): Long =
        ((packet[offset].toLong() and 0xFF) shl 24) or
            ((packet[offset + 1].toLong() and 0xFF) shl 16) or
            ((packet[offset + 2].toLong() and 0xFF) shl 8) or
            (packet[offset + 3].toLong() and 0xFF)

    private fun aRdataAllZero(resp: ByteArray): Boolean {
        if (resp.size < 12) return false
        val ancount = readUInt16(resp, 6)
        if (ancount < 1) return false
        return resp.size >= 4 &&
            resp[resp.size - 4] == 0.toByte() &&
            resp[resp.size - 3] == 0.toByte() &&
            resp[resp.size - 2] == 0.toByte() &&
            resp[resp.size - 1] == 0.toByte()
    }

    private fun aRdata192_0_2_1(resp: ByteArray): Boolean =
        resp.size >= 4 &&
            (resp[resp.size - 4].toInt() and 0xFF) == 192 &&
            (resp[resp.size - 3].toInt() and 0xFF) == 0 &&
            (resp[resp.size - 2].toInt() and 0xFF) == 2 &&
            (resp[resp.size - 1].toInt() and 0xFF) == 1

    private fun hasNonZeroIpv4ARecord(packet: ByteArray): Boolean {
        if (packet.size < 12) return false
        val ancount = readUInt16(packet, 6)
        if (ancount < 1) return false
        var pos = 12
        val (_, afterQname) = DnsCodec.readName(packet, pos)
        pos = afterQname + 4
        repeat(ancount) {
            if (pos >= packet.size) return false
            val (_, afterName) = DnsCodec.readName(packet, pos)
            pos = afterName
            if (pos + 10 > packet.size) return false
            val type = readUInt16(packet, pos)
            pos += 2
            pos += 2
            readUInt32(packet, pos)
            pos += 4
            val rdlen = readUInt16(packet, pos)
            pos += 2
            if (pos + rdlen > packet.size) return false
            if (type == DnsConstants.QTYPE_A && rdlen == 4) {
                val a = packet[pos].toInt() and 0xFF
                val b = packet[pos + 1].toInt() and 0xFF
                val c = packet[pos + 2].toInt() and 0xFF
                val d = packet[pos + 3].toInt() and 0xFF
                if (a != 0 || b != 0 || c != 0 || d != 0) return true
            }
            pos += rdlen
        }
        return false
    }

    private fun selectBlocked(suffixDeny: Set<String>, k: Int): List<String> {
        val out = ArrayList<String>(k)
        for (e in suffixDeny) {
            if (!isUsableBlocked(e)) continue
            val fqdn = normalizeFqdn(e)
            out.add(fqdn)
            if (out.size >= k) break
        }
        return out
    }

    private fun normalizeFqdn(s: String): String {
        val n = s.trim().lowercase()
        return if (n.endsWith(".")) n else "$n."
    }

    private fun isUsableBlocked(entry: String): Boolean {
        var n = entry.trim().lowercase()
        if (!n.endsWith(".")) n += "."
        val body = n.removeSuffix(".")
        if (body.isEmpty() || !body.contains('.')) return false
        return body.split(".").all { label ->
            label.isNotEmpty() && label.length <= 63 && label.all { it.code in 0x20..0x7E }
        }
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val LISTENER_WAIT_MS = 90_000L
        /** Must cover cold Tor + SOCKS weak-ready after TorController bootstrap window (see core/tor). */
        private const val TOR_WAIT_MS = 300_000L
        /** One DoT-over-Tor forward can take ~30–90s (onion + TLS); keep bounded so retries do not hang CI for 20+ min. */
        private const val UPSTREAM_UDP_TIMEOUT_MS = 120_000
        private const val UPSTREAM_RETRIES: Int = 3

        /** Multi-label suffix used when DB/manifest are empty after test APK install. */
        private const val E2E_BLOCKED_FQDN: String = "blocked.e2e.test."
    }
}
