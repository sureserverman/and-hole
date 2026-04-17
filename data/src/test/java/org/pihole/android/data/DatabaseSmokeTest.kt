package org.pihole.android.data

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.AdlistSourceEntity
import org.pihole.android.data.db.entity.CustomRuleEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseSmokeTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadAdlistSource() = runBlocking {
        val id = db.adlistSourceDao().insert(
            AdlistSourceEntity(
                url = "https://example.com/list.txt",
                enabled = true,
                etag = null,
                lastModified = null,
                lastRefreshStartedAt = null,
                lastSuccessAt = null,
                lastResult = null,
                lastError = null,
            ),
        )
        val row = db.adlistSourceDao().getById(id)
        assertNotNull(row)
        assertEquals("https://example.com/list.txt", row!!.url)
    }

    @Test
    fun insertAndReadCustomRule() = runBlocking {
        val now = System.currentTimeMillis()
        val id = db.customRuleDao().insert(
            CustomRuleEntity(
                kind = "exact_deny",
                value = "bad.example.com.",
                enabled = true,
                comment = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val row = db.customRuleDao().getById(id)
        assertNotNull(row)
        assertEquals("bad.example.com.", row!!.value)
    }

    @Test
    fun deleteCustomRuleById() = runBlocking {
        val now = System.currentTimeMillis()
        val id = db.customRuleDao().insert(
            CustomRuleEntity(
                kind = "exact_allow",
                value = "ok.example.com.",
                enabled = true,
                comment = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertEquals(1, db.customRuleDao().deleteById(id))
        assertEquals(null, db.customRuleDao().getById(id))
    }
}
