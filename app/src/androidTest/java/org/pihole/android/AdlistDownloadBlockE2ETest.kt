package org.pihole.android

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.pihole.android.testutil.DnsServiceTestPrep
import org.junit.runner.RunWith
import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.dns.codec.DnsQuestion
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.db.entity.AdlistSourceEntity
import org.pihole.android.data.lists.AdlistRefreshEngine
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.service.DnsForegroundService
import android.util.Base64
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * End-to-end: HTTP adlist (MockWebServer) → parse → Room snapshot → [DnsForegroundService] matcher → blocked UDP A response.
 */
@RunWith(AndroidJUnit4::class)
class AdlistDownloadBlockE2ETest {

    private lateinit var context: Context
    private val mockServer = MockWebServer()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        DnsServiceTestPrep.stopAndSettle(context)
        mockServer.start()
    }

    @After
    fun tearDown() {
        DnsServiceTestPrep.stopAndSettle(context)
        mockServer.shutdown()
        DatabaseProvider.get(context).clearAllTables()
    }

    @Test
    fun downloadAdlist_refreshThenQuery_returnsNullIpv4ForBlockedHost() = runBlocking {
        val hosts =
            """
            # test list
            0.0.0.0 ads.e2e.block.test
            """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(hosts).setResponseCode(200))

        val db = DatabaseProvider.get(context)
        db.clearAllTables()

        val listUrl = mockServer.url("/list.txt").toString()
        db.adlistSourceDao().insert(
            AdlistSourceEntity(
                url = listUrl,
                enabled = true,
                etag = null,
                lastModified = null,
                lastRefreshStartedAt = null,
                lastSuccessAt = null,
                lastResult = null,
                lastError = null,
            ),
        )

        AdlistRefreshEngine.refreshAll(context, db)

        ContextCompat.startForegroundService(
            context,
            Intent(context, DnsForegroundService::class.java),
        )

        val port = AppPreferences(context).dnsListenPort.first()
        // Same A query as loopback instrumented tests — wait until the UDP listener is up
        // (must match DataStore port, not only DEFAULT_DNS_PORT).
        val warmupQuery =
            Base64.decode(
                "EjQBAAABAAAAAAAABHRlc3QHcGktaG9sZQVsb2NhbAAAAQAB",
                Base64.DEFAULT,
            )
        val warmupDeadline = System.currentTimeMillis() + 90_000L
        var listenerUp = false
        while (System.currentTimeMillis() < warmupDeadline) {
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 2_000
                    val loop = InetAddress.getByName("127.0.0.1")
                    socket.connect(InetSocketAddress(loop, port))
                    socket.send(DatagramPacket(warmupQuery, warmupQuery.size))
                    val buf = ByteArray(2048)
                    val pkt = DatagramPacket(buf, buf.size)
                    socket.receive(pkt)
                    if (pkt.length >= 12) listenerUp = true
                }
            } catch (_: Exception) {
                Thread.sleep(200)
            }
            if (listenerUp) break
        }
        assertTrue("DNS listener not answering test.pi-hole.local within 90s over UDP", listenerUp)

        val q = DnsQuestion("ads.e2e.block.test.", DnsConstants.QTYPE_A, DnsConstants.QCLASS_IN)
        val query = buildQueryPacket(0xBEE2, listOf(q))

        val deadline = System.currentTimeMillis() + 30_000L
        var response: ByteArray? = null
        while (System.currentTimeMillis() < deadline && response == null) {
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 3_000
                    val loop = InetAddress.getByName("127.0.0.1")
                    socket.connect(InetSocketAddress(loop, port))
                    socket.send(DatagramPacket(query, query.size))
                    val buf = ByteArray(2048)
                    val pkt = DatagramPacket(buf, buf.size)
                    socket.receive(pkt)
                    response = buf.copyOf(pkt.length)
                }
            } catch (_: Exception) {
                Thread.sleep(150)
            }
        }

        assertTrue("expected DNS response over UDP", response != null && response!!.size >= 16)
        val r = response!!
        val bb = ByteBuffer.wrap(r).order(ByteOrder.BIG_ENDIAN)
        bb.position(r.size - 4)
        val last4 = ByteArray(4)
        bb.get(last4)
        assertTrue(
            "blocked A should end with 0.0.0.0 RDATA",
            last4[0].toInt() == 0 && last4[1].toInt() == 0 &&
                last4[2].toInt() == 0 && last4[3].toInt() == 0,
        )
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
}
