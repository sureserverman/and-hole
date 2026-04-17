package org.pihole.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.data.prefs.AppPreferences

@RunWith(AndroidJUnit4::class)
class AppPreferencesInstrumentedTest {

    @Test
    fun persistPortPreference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)
        prefs.setDnsListenPort(53000)
        val port = prefs.dnsListenPort.first()
        assertEquals(53000, port)
    }

    @Test
    fun persistBindAllInterfacesPreference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)
        prefs.setDnsBindAllInterfaces(true)
        assertEquals(true, prefs.dnsBindAllInterfaces.first())
        prefs.setDnsBindAllInterfaces(false)
        assertEquals(false, prefs.dnsBindAllInterfaces.first())
    }

    @Test
    fun persistOnboardingFlags() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)

        prefs.setOnboardingCompleted(false)
        prefs.setSetupClientMode("")
        prefs.setSetupBindAllInterfacesDismissed(false)
        assertFalse(prefs.onboardingCompleted.first())
        assertEquals("", prefs.setupClientMode.first())
        assertFalse(prefs.setupBindAllInterfacesDismissed.first())

        prefs.setOnboardingCompleted(true)
        prefs.setSetupClientMode("sing_box")
        prefs.setSetupBindAllInterfacesDismissed(true)
        assertTrue(prefs.onboardingCompleted.first())
        assertEquals("sing_box", prefs.setupClientMode.first())
        assertTrue(prefs.setupBindAllInterfacesDismissed.first())
    }

    @Test
    fun persistUpstreamUseTorPreference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)
        prefs.setUpstreamUseTor(true)
        assertTrue(prefs.upstreamUseTor.first())
        prefs.setUpstreamUseTor(false)
        assertFalse(prefs.upstreamUseTor.first())
    }
}
