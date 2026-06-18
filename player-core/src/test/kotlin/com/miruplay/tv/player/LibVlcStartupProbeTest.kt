package com.miruplay.tv.player

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibVlcStartupProbeTest {
    @Test
    fun `probe reports false when provider returns failure result`() {
        val resolver = mockk<ContentResolver>()
        val authority = "com.miruplay.tv.libvlc_probe"
        val probeUri = Uri.parse("content://$authority")
        every {
            resolver.call(
                probeUri,
                LibVlcStartupProbeContract.METHOD_CAN_START_LIBVLC,
                null,
                any(),
            )
        } returns Bundle().apply {
            putBoolean(LibVlcStartupProbeContract.EXTRA_CAN_START, false)
            putString(LibVlcStartupProbeContract.EXTRA_ERROR_MESSAGE, "native exit")
        }

        val result = ContentResolverLibVlcStartupProbe(
            contentResolver = resolver,
            authority = authority,
        ).canStartLibVlc()

        assertFalse(result.canStart)
        assertEquals("native exit", result.errorMessage)
    }

    @Test
    fun `probe passes libvlc options through provider extras`() {
        val resolver = mockk<ContentResolver>()
        val authority = "com.miruplay.tv.libvlc_probe"
        val probeUri = Uri.parse("content://$authority")
        every {
            resolver.call(
                probeUri,
                LibVlcStartupProbeContract.METHOD_CAN_START_LIBVLC,
                null,
                any(),
            )
        } returns Bundle().apply {
            putBoolean(LibVlcStartupProbeContract.EXTRA_CAN_START, true)
        }

        val options = listOf("--verbose=2", "--vout=android_display,none")
        val result = ContentResolverLibVlcStartupProbe(
            contentResolver = resolver,
            authority = authority,
        ).canStartLibVlc(options)

        assertTrue(result.canStart)
        verify(exactly = 1) {
            resolver.call(
                probeUri,
                LibVlcStartupProbeContract.METHOD_CAN_START_LIBVLC,
                null,
                match { extras ->
                    assertEquals(
                        options,
                        extras.getStringArrayList(LibVlcStartupProbeContract.EXTRA_LIBVLC_OPTIONS),
                    )
                    true
                },
            )
        }
    }
}
