package com.miruplay.tv.player

import com.miruplay.tv.model.PlaybackRenderBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalAudioBackendSupportTest {
    @Test
    fun `unsupported selected backends return explicit errors`() {
        assertEquals(
            "IJKPlayer 不支持加载外挂音轨",
            externalAudioUnsupportedMessage(PlaybackRenderBackend.EXPERIMENTAL_IJKPLAYER, true),
        )
        assertEquals(
            "外部 mpv-android 不支持通过 Intent 加载外挂音轨",
            externalAudioUnsupportedMessage(PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID, true),
        )
        assertEquals(
            "嵌入式 mpv 当前不支持为 WebDAV 视频加载外挂音轨",
            externalAudioUnsupportedMessage(
                PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED,
                hasExternalAudio = true,
                isWebDav = true,
            ),
        )
    }

    @Test
    fun `supported backends and sources without external audio have no error`() {
        assertNull(externalAudioUnsupportedMessage(PlaybackRenderBackend.STANDARD_EXO, true))
        assertNull(externalAudioUnsupportedMessage(PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED, true))
        assertNull(externalAudioUnsupportedMessage(PlaybackRenderBackend.EXPERIMENTAL_IJKPLAYER, false))
    }
}
