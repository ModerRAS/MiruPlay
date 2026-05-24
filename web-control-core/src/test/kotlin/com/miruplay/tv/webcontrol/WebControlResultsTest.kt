package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlResultsTest {
    @Test
    fun `require success returns success data`() {
        val value = Any()

        assertSame(value, requireWebControlSuccess(Result.success(value), "读取失败"))
    }

    @Test
    fun `require success throws user facing error message`() {
        val failure = runCatching {
            requireWebControlSuccess(
                Result.failure(AppError.MediaSourceError.PermissionDenied("/storage/anime")),
                "读取失败",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("读取失败: 无权限访问：/storage/anime", failure?.message)
    }
}
