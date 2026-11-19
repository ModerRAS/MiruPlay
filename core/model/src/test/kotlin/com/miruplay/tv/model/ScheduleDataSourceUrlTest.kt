package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDataSourceUrlTest {

    @Test
    fun `accepts http and https base urls`() {
        assertEquals("https://example.com", ScheduleDataSourceUrl.normalize("https://example.com"))
        assertEquals("https://example.com", ScheduleDataSourceUrl.normalize("  https://example.com/  "))
        assertEquals("https://example.com:8443/api", ScheduleDataSourceUrl.normalize("https://example.com:8443/api/"))
        assertEquals("http://192.168.1.10:8080", ScheduleDataSourceUrl.normalize("http://192.168.1.10:8080"))
    }

    @Test
    fun `rejects blank and non-http schemes`() {
        assertNull(ScheduleDataSourceUrl.normalize(null))
        assertNull(ScheduleDataSourceUrl.normalize(""))
        assertNull(ScheduleDataSourceUrl.normalize("   "))
        assertNull(ScheduleDataSourceUrl.normalize("example.com"))
        assertNull(ScheduleDataSourceUrl.normalize("ftp://example.com"))
        assertNull(ScheduleDataSourceUrl.normalize("file:///sdcard/schedule.json"))
        assertNull(ScheduleDataSourceUrl.normalize("javascript:alert(1)"))
    }

    @Test
    fun `rejects url parts that break base-url joining`() {
        assertNull(ScheduleDataSourceUrl.normalize("https://example.com/path?x=1"))
        assertNull(ScheduleDataSourceUrl.normalize("https://example.com/path#frag"))
        assertNull(ScheduleDataSourceUrl.normalize("https://user:pass@example.com"))
        assertNull(ScheduleDataSourceUrl.normalize("https://exa mple.com"))
        assertNull(ScheduleDataSourceUrl.normalize("https://"))
    }

    @Test
    fun `isValid mirrors normalize`() {
        assertTrue(ScheduleDataSourceUrl.isValid("https://example.com"))
        assertFalse(ScheduleDataSourceUrl.isValid("example.com"))
        assertFalse(ScheduleDataSourceUrl.isValid(" "))
    }

    @Test
    fun `latest endpoint joins without double slash`() {
        assertEquals(
            "https://example.com/api/schedule/latest",
            ScheduleDataSourceUrl.latestScheduleEndpoint("https://example.com"),
        )
        assertEquals(
            "https://example.com:8443/base/api/schedule/latest",
            ScheduleDataSourceUrl.latestScheduleEndpoint("https://example.com:8443/base/"),
        )
    }
}
