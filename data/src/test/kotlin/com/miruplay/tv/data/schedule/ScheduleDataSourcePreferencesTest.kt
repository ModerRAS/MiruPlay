package com.miruplay.tv.data.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleDataSourcePreferencesTest {

    private lateinit var context: Context
    private lateinit var preferences: ScheduleDataSourcePreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ScheduleDataSourcePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferences = ScheduleDataSourcePreferences(context)
    }

    @Test
    fun `old install without the key reads as not configured`() {
        assertEquals("", preferences.rawDataSourceUrl)
        assertNull(preferences.configuredUrlOrNull())
    }

    @Test
    fun `save normalizes trailing slash and trims whitespace`() {
        assertTrue(preferences.save("  https://example.com/  "))

        assertEquals("https://example.com", preferences.rawDataSourceUrl)
        assertEquals("https://example.com", preferences.configuredUrlOrNull())
    }

    @Test
    fun `invalid save keeps the previous url`() {
        assertTrue(preferences.save("https://example.com/base"))

        assertFalse(preferences.save("not-a-url"))
        assertFalse(preferences.save("ftp://example.com"))
        assertFalse(preferences.save(null))

        assertEquals("https://example.com/base", preferences.configuredUrlOrNull())
    }

    @Test
    fun `clear removes the configured url`() {
        assertTrue(preferences.save("https://example.com"))
        preferences.clear()

        assertEquals("", preferences.rawDataSourceUrl)
        assertNull(preferences.configuredUrlOrNull())
    }
}
