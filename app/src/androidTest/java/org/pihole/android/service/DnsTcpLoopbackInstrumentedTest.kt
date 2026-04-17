package org.pihole.android.service

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.testutil.DnsServiceTestPrep

/** Confirms the app no longer binds a TCP DNS listener once the UDP-only service is running. */
@RunWith(AndroidJUnit4::class)
class DnsTcpDisabledInstrumentedTest {

    @Before
    fun ensureStopped() {
        DnsServiceTestPrep.stopAndSettle(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        DnsServiceTestPrep.stopAndSettle(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun tcpDnsQuery_isRefusedWhenAppRunsUdpOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, DnsForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)

        val port = AppPreferences.DEFAULT_DNS_PORT
        waitForUdpListener(port)

        val error =
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = 2_000
                    socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
                }
                null
            } catch (e: ConnectException) {
                e
            }

        assertTrue("Expected TCP connection refusal while UDP listener is running", error is ConnectException)
        val message = error?.message?.lowercase().orEmpty()
        assertTrue("Expected refusal-style TCP error message, got '$message'", "refused" in message || "failed" in message)
    }

    private fun waitForUdpListener(port: Int) {
        val query =
            Base64.decode(
                "EjQBAAABAAAAAAAABHRlc3QHcGktaG9sZQVsb2NhbAAAAQAB",
                Base64.DEFAULT,
            )
        val deadline = System.currentTimeMillis() + 30_000L
        var listenerReady = false
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 2_000
                    socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                    socket.send(DatagramPacket(query, query.size))
                    val response = ByteArray(512)
                    val packet = DatagramPacket(response, response.size)
                    socket.receive(packet)
                    listenerReady = packet.length >= 12
                }
                if (listenerReady) break
            } catch (e: Throwable) {
                lastError = e
            }
            Thread.sleep(100)
        }

        assertTrue(
            "Expected UDP listener before checking TCP refusal (lastError=$lastError)",
            listenerReady,
        )
    }
}
