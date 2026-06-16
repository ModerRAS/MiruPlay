package com.miruplay.tv.data.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import javax.crypto.AEADBadTagException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurePreferencesManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("miruplay_secure_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences("miruplay_secure_prefs")
    }

    @Test
    fun `factory recreates secure prefs when encrypted prefs cannot be decrypted`() {
        context.getSharedPreferences("miruplay_secure_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("stale", "value")
            .commit()
        var createCalls = 0

        val prefs = RecoverableSecurePreferencesFactory(
            context = context,
            preferencesName = "miruplay_secure_prefs",
            createPreferences = {
                createCalls += 1
                if (createCalls == 1) {
                    throw AEADBadTagException("bad tag")
                }
                context.getSharedPreferences("miruplay_secure_prefs", Context.MODE_PRIVATE)
            },
        ).open()

        assertEquals(2, createCalls)
        assertTrue(!prefs.contains("stale"))
    }

    @Test
    fun `factory rethrows non-recoverable secure prefs failures`() {
        try {
            RecoverableSecurePreferencesFactory(
                context = context,
                preferencesName = "miruplay_secure_prefs",
                createPreferences = {
                    throw IllegalStateException("boom")
                },
            ).open()
            fail("Expected secure prefs factory to rethrow non-recoverable errors")
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }
    }
}
