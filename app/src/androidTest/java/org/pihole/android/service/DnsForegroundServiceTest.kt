package org.pihole.android.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.testutil.DnsServiceTestPrep

@RunWith(AndroidJUnit4::class)
class DnsForegroundServiceTest {

    @Before
    fun ensureStopped() {
        DnsServiceTestPrep.stopAndSettle(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        DnsServiceTestPrep.stopAndSettle(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun startForeground_createsNotificationChannel() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, DnsForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)

        val nm = context.getSystemService(NotificationManager::class.java)
        var channel = nm.getNotificationChannel(ServiceNotification.CHANNEL_ID)
        var attempts = 0
        while (channel == null && attempts < 100) {
            Thread.sleep(20)
            channel = nm.getNotificationChannel(ServiceNotification.CHANNEL_ID)
            attempts++
        }
        assertNotNull(channel)
    }
}
