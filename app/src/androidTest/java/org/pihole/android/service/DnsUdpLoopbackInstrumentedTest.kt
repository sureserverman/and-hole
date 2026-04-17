package org.pihole.android.service

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.testutil.DnsServiceTestPrep

/**
 * UDP loopback check without host `adb forward udp:`: same process net stack as other instrumented
 * tests can reach [DnsForegroundService]'s [org.pihole.android.core.dns.server.UdpDnsServer].
 */
@RunWith(AndroidJUnit4::class)
class DnsUdpLoopbackInstrumentedTest {

    @Before
    fun ensureStopped() {
        DnsServiceTestPrep.stopAndSettle(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.stopService(Intent(context, DnsForegroundService::class.java))
    }

    @Test
    fun udpDnsQuery_returnsNonEmptyResponse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ContextCompat.startForegroundService(context, Intent(context, DnsForegroundService::class.java))

        val port = AppPreferences.DEFAULT_DNS_PORT
        val query =
            Base64.decode(
                "EjQBAAABAAAAAAAABHRlc3QHcGktaG9sZQVsb2NhbAAAAQAB",
                Base64.DEFAULT,
            )
        require(query.size == 36) { "embedded query must be 36 bytes" }

        val deadline = System.currentTimeMillis() + 30_000L
        var lastError: Throwable? = null
        var responseLen = 0
        while (System.currentTimeMillis() < deadline) {
            try {
                responseLen = udpDnsExchange(query, port).size
                if (responseLen > 0) break
            } catch (e: Throwable) {
                lastError = e
            }
            Thread.sleep(100)
        }

        assertTrue(
            "Expected UDP DNS response after service start (lastError=$lastError)",
            responseLen > 0,
        )
    }

    private fun udpDnsExchange(query: ByteArray, port: Int): ByteArray {
        DatagramSocket().use { socket ->
            socket.soTimeout = 5_000
            val addr = InetAddress.getByName("127.0.0.1")
            // connect() fixes "Poll timed out" / no-reply on some Android builds when sending to loopback.
            socket.connect(InetSocketAddress(addr, port))
            socket.send(DatagramPacket(query, query.size))
            val buf = ByteArray(2048)
            val pkt = DatagramPacket(buf, buf.size)
            socket.receive(pkt)
            return buf.copyOf(pkt.length)
        }
    }
}
