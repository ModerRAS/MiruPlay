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
    fun `full latest endpoint is stripped to base url`() {
        assertEquals(
            "https://example.com",
            ScheduleDataSourceUrl.normalize("https://example.com/api/schedule/latest"),
        )
        assertEquals(
            "https://example.com",
            ScheduleDataSourceUrl.normalize("  https://example.com/api/schedule/latest/  "),
        )
        assertEquals(
            "https://example.com/base",
            ScheduleDataSourceUrl.normalize("https://example.com/base/api/schedule/latest"),
        )
        assertEquals(
            "https://example.com",
            ScheduleDataSourceUrl.normalize("https://example.com/API/Schedule/Latest"),
        )
        // 不是完整接口地址的路径不能被误删
        assertEquals(
            "https://example.com/api/schedule",
            ScheduleDataSourceUrl.normalize("https://example.com/api/schedule"),
        )
        assertEquals(
            "https://example.com/api/schedule/latest-2",
            ScheduleDataSourceUrl.normalize("https://example.com/api/schedule/latest-2"),
        )
        // 归一化后再拼接口不会出现重复路径
        val normalized = ScheduleDataSourceUrl.normalize("https://example.com/api/schedule/latest")!!
        assertEquals(
            "https://example.com/api/schedule/latest",
            ScheduleDataSourceUrl.latestScheduleEndpoint(normalized),
        )
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
