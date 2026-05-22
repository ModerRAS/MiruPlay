package com.miruplay.tv.player.mpv

import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class MpvCommandBuilderTest {
    @Test
    fun `build command includes mpv config ipc RIFE subtitle and resume position`() {
        val command = MpvCommandBuilder(
            MpvRuntimeConfig(
                mpvExecutable = Paths.get("C:/MiruPlay/mpv/mpv.exe"),
                configDirectory = Paths.get("C:/MiruPlay/mpv/portable_config"),
                ipcServer = "miruplay-mpv",
                startFullscreen = true,
                rife = RifeInterpolationConfig(backend = RifeBackend.NVIDIA),
                extraArguments = listOf("--profile=anime")
            )
        ).build(
            PlaybackSource(
                uri = "D:/Anime/Test Episode.mkv",
                mediaSourceId = "local",
                startPosition = 90_500L,
                subtitleTracks = listOf(
                    SubtitleTrack(
                        language = "zh",
                        title = "简中",
                        isExternal = true,
                        path = "D:/Anime/Test Episode.ass",
                        format = SubtitleFormat.ASS
                    )
                )
            )
        )

        assertEquals(Paths.get("C:/MiruPlay/mpv/mpv.exe").toString(), command.first())
        assertTrue(command.contains("--config-dir=${Paths.get("C:/MiruPlay/mpv/portable_config")}"))
        assertTrue(command.contains("--input-ipc-server=miruplay-mpv"))
        assertTrue(command.contains("--fs"))
        assertTrue(command.contains("--vf-append=vapoursynth=~~home/vs/MEMC_RIFE_NV.vpy:4:auto:"))
        assertTrue(command.contains("--sub-file=D:/Anime/Test Episode.ass"))
        assertTrue(command.contains("--start=90.5"))
        assertTrue(command.contains("--profile=anime"))
        assertEquals("D:/Anime/Test Episode.mkv", command.last())
    }

    @Test
    fun `explicit RIFE script outside config dir is fixed length quoted`() {
        val script = Paths.get("D:/filters/custom script.vpy").toAbsolutePath().normalize().toString()
        val command = MpvCommandBuilder(
            MpvRuntimeConfig(
                mpvExecutable = Paths.get("C:/MiruPlay/mpv/mpv.exe"),
                rife = RifeInterpolationConfig(scriptPath = Paths.get("D:/filters/custom script.vpy"))
            )
        ).build(
            PlaybackSource(
                uri = "D:/Anime/Test Episode.mkv",
                mediaSourceId = "local"
            )
        )

        val expectedLength = script.toByteArray(Charsets.UTF_8).size
        assertTrue(command.contains("--vf-append=vapoursynth=%$expectedLength%$script:4:auto:"))
    }

    @Test
    fun `build preview quotes arguments with whitespace and escaped quotes`() {
        val preview = listOf(
            "C:/MiruPlay/mpv player.exe",
            "--title=MiruPlay \"Preview\"",
            "D:/Anime/Episode 01.mkv",
        ).toMpvCommandPreview()

        assertEquals(
            "\"C:/MiruPlay/mpv player.exe\" \"--title=MiruPlay \\\"Preview\\\"\" \"D:/Anime/Episode 01.mkv\"",
            preview,
        )
    }

    @Test
    fun `mpvCommandPreviewFromInputs builds preview from UI style inputs`() {
        val preview = mpvCommandPreviewFromInputs(
            mpvPath = "C:/MiruPlay/mpv player.exe",
            configDir = "",
            mediaPath = " D:/Anime/Episode 01.mkv ",
            subtitlePath = "",
            startSeconds = "90.5",
            fullscreen = false,
            keepOpen = true,
            rifeEnabled = false,
            rifeBackend = RifeBackend.DIRECTML,
        )

        val normalized = preview.replace('\\', '/')
        assertTrue(normalized.startsWith("\"C:/MiruPlay/mpv player.exe\""))
        assertTrue(normalized.contains("--input-ipc-server="))
        assertTrue(normalized.contains(DEFAULT_MPV_IPC_SERVER))
        assertTrue(normalized.contains("--keep-open=yes"))
        assertTrue(normalized.contains("--start=90.5"))
        assertTrue(normalized.endsWith("\"D:/Anime/Episode 01.mkv\""))
    }
}
