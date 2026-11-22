package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleUiConventionsTest {

    @Test
    fun `stale banner reflects the real failure reason`() {
        val network = scheduleStaleBannerLabel(ScheduleFetchError.Network).fallback
        assertEquals("网络不可用，正在显示上次缓存的数据。", network)

        val timeout = scheduleStaleBannerLabel(ScheduleFetchError.Timeout).fallback
        assertTrue(timeout.contains("超时"))

        // 解析失败绝不能声称「网络不可用」（线上 2.19.0 的误导文案回归点）。
        val invalidData = scheduleStaleBannerLabel(ScheduleFetchError.InvalidData).fallback
        assertTrue(invalidData.contains("数据格式无法识别"))
        assertTrue(!invalidData.contains("网络"))

        val http = scheduleStaleBannerLabel(ScheduleFetchError.Http(503)).fallback
        assertTrue(http.contains("HTTP 503"))

        assertEquals(
            "网络不可用，正在显示上次缓存的数据。",
            scheduleStaleBannerLabel(null).fallback,
        )
    }

    @Test
    fun `time unknown and disputed labels are explicit`() {
        assertEquals("时间未知", scheduleTimeUnknownLabel().fallback)
        assertEquals("时间有争议", scheduleTimeDisputedLabel().fallback)
    }
}
