package com.miruplay.tv

import android.os.Bundle
import com.miruplay.tv.player.LibVlcStartupProbeContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import io.mockk.mockk
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibVlcStartupProbeProviderTest {
    @After
    fun tearDown() {
        LibVlcStartupProbeRuntime.testOverride = null
    }

    @Test
    fun `provider runs probe with caller supplied libvlc options`() {
        val capturedOptions = mutableListOf<String>()
        LibVlcStartupProbeRuntime.testOverride = { _, options ->
            capturedOptions += options
        }
        val provider = Robolectric.buildContentProvider(LibVlcStartupProbeProvider::class.java)
            .create()
            .get()

        val result = provider.call(
            LibVlcStartupProbeContract.METHOD_CAN_START_LIBVLC,
            null,
            Bundle().apply {
                putStringArrayList(
                    LibVlcStartupProbeContract.EXTRA_LIBVLC_OPTIONS,
                    arrayListOf("--verbose=2", "--vout=android_display,none"),
                )
            },
        )

        assertTrue(result.getBoolean(LibVlcStartupProbeContract.EXTRA_CAN_START, false))
        assertEquals(
            listOf("--verbose=2", "--vout=android_display,none"),
            capturedOptions,
        )
    }

    @Test
    fun `runtime emits constructor checkpoints around libvlc initialization`() {
        val checkpoints = mutableListOf<String>()
        val fakeLibVlc = mockk<LibVLC>(relaxed = true)
        val fakeMediaPlayer = mockk<MediaPlayer>(relaxed = true)
        val provider = Robolectric.buildContentProvider(LibVlcStartupProbeProvider::class.java)
            .create()
            .get()

        LibVlcStartupProbeRuntime.run(
            context = requireNotNull(provider.context),
            options = emptyList(),
            checkpointWriter = { checkpoints += it },
            libVlcFactory = { _, _ -> fakeLibVlc },
            mediaPlayerFactory = { fakeMediaPlayer },
        )

        assertTrue(checkpoints.contains("call_enter"))
        assertTrue(checkpoints.contains("before_libvlc_ctor"))
        assertTrue(checkpoints.contains("after_libvlc_ctor"))
        assertTrue(checkpoints.contains("before_media_player_ctor"))
        assertTrue(checkpoints.contains("after_media_player_ctor"))
        assertTrue(checkpoints.contains("call_exit_success"))
    }

    @Test
    fun `provider reports probe failures without crashing caller process`() {
        LibVlcStartupProbeRuntime.testOverride = { _, _ ->
            error("probe boom")
        }
        val provider = Robolectric.buildContentProvider(LibVlcStartupProbeProvider::class.java)
            .create()
            .get()

        val result = provider.call(
            LibVlcStartupProbeContract.METHOD_CAN_START_LIBVLC,
            null,
            Bundle(),
        )

        assertFalse(result.getBoolean(LibVlcStartupProbeContract.EXTRA_CAN_START, true))
        assertTrue(
            result.getString(LibVlcStartupProbeContract.EXTRA_ERROR_MESSAGE)
                ?.contains("probe boom") == true,
        )
    }
}
