package com.miruplay.tv.data.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.security.GeneralSecurityException
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
        context.deleteSharedPreferences("miruplay_secure_prefs_compat")
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
            fallbackPreferencesName = "miruplay_secure_prefs_compat",
            createPreferences = {
                createCalls += 1
                if (createCalls == 1) {
                    throw AEADBadTagException("bad tag")
                }
                context.getSharedPreferences("miruplay_secure_prefs", Context.MODE_PRIVATE)
            },
            openFallbackPreferences = {
                context.getSharedPreferences("miruplay_secure_prefs_compat", Context.MODE_PRIVATE)
            },
        ).open()

        assertEquals(2, createCalls)
        assertTrue(!prefs.contains("stale"))
    }

    @Test
    fun `factory falls back to compatibility prefs when encrypted prefs stay unavailable`() {
        val compatibilityPrefs = context.getSharedPreferences("miruplay_secure_prefs_compat", Context.MODE_PRIVATE)
        compatibilityPrefs.edit()
            .putString("legacy", "value")
            .commit()
        var createCalls = 0

        val prefs = RecoverableSecurePreferencesFactory(
            context = context,
            preferencesName = "miruplay_secure_prefs",
            fallbackPreferencesName = "miruplay_secure_prefs_compat",
            createPreferences = {
                createCalls += 1
                throw GeneralSecurityException("keystore unavailable")
            },
            openFallbackPreferences = {
                compatibilityPrefs
            },
        ).open()

        assertEquals(2, createCalls)
        assertEquals("value", prefs.getString("legacy", null))
    }

    @Test
    fun `factory falls back when encrypted prefs fail with io errors`() {
        val compatibilityPrefs = context.getSharedPreferences("miruplay_secure_prefs_compat", Context.MODE_PRIVATE)
        compatibilityPrefs.edit()
            .putString("legacy", "value")
            .commit()
        var createCalls = 0

        val prefs = RecoverableSecurePreferencesFactory(
            context = context,
            preferencesName = "miruplay_secure_prefs",
            fallbackPreferencesName = "miruplay_secure_prefs_compat",
            createPreferences = {
                createCalls += 1
                throw IOException("invalid encrypted preferences keyset")
            },
            openFallbackPreferences = {
                compatibilityPrefs
            },
        ).open()

        assertEquals(2, createCalls)
        assertEquals("value", prefs.getString("legacy", null))
    }

    @Test
    fun `factory falls back when encrypted prefs fail with security runtime errors`() {
        val compatibilityPrefs = context.getSharedPreferences("miruplay_secure_prefs_compat", Context.MODE_PRIVATE)
        compatibilityPrefs.edit()
            .putString("legacy", "value")
            .commit()
        var createCalls = 0

        val prefs = RecoverableSecurePreferencesFactory(
            context = context,
            preferencesName = "miruplay_secure_prefs",
            fallbackPreferencesName = "miruplay_secure_prefs_compat",
            createPreferences = {
                createCalls += 1
                throw RuntimeException("EncryptedSharedPreferences failed during AndroidKeyStore init")
            },
            openFallbackPreferences = {
                compatibilityPrefs
            },
        ).open()

        assertEquals(2, createCalls)
        assertEquals("value", prefs.getString("legacy", null))
    }

    @Test
    fun `factory rethrows non-recoverable secure prefs failures`() {
        try {
            RecoverableSecurePreferencesFactory(
                context = context,
                preferencesName = "miruplay_secure_prefs",
                fallbackPreferencesName = "miruplay_secure_prefs_compat",
                createPreferences = {
                    throw IllegalStateException("boom")
                },
                openFallbackPreferences = {
                    context.getSharedPreferences("miruplay_secure_prefs_compat", Context.MODE_PRIVATE)
                },
            ).open()
            fail("Expected secure prefs factory to rethrow non-recoverable errors")
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }
    }
}
