package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.player.mpv.RifeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPlaybackPanelTest {
    @Test
    fun `desktop RIFE is opt in by default`() {
        assertFalse(DEFAULT_DESKTOP_RIFE_ENABLED)
    }

    @Test
    fun `desktop player chrome derives a TV style title from media path`() {
        assertEquals("选择媒体", desktopPlaybackTitle(""))
        assertEquals("Frieren - S01E02", desktopPlaybackTitle("D:/Anime/Frieren - S01E02.mkv"))
    }

    @Test
    fun `desktop player controls use TV facing labels`() {
        val labels = desktopPlaybackUiLabels()

        assertEquals("媒体 URI 或文件路径", labels.mediaPath)
        assertEquals("起播秒数", labels.startSeconds)
        assertEquals("外挂字幕路径", labels.subtitlePath)
        assertEquals("全屏", labels.fullscreen)
        assertEquals("播完保留窗口", labels.keepOpen)
        assertEquals("RIFE", labels.rife)
    }

    @Test
    fun `desktop player stage uses localized mpv chips`() {
        assertEquals("mpv 待命", desktopPlaybackStatusChip(isPlayerActive = false))
        assertEquals("mpv 播放中", desktopPlaybackStatusChip(isPlayerActive = true))
    }

    @Test
    fun `desktop player source line exposes mpv and RIFE state`() {
        val line = desktopPlaybackSourceLine(
            mediaPath = "https://example.test/video.mkv",
            rifeEnabled = true,
            rifeBackend = RifeBackend.DIRECTML,
            isPlayerActive = true,
        )

        assertTrue(line.contains("远程串流"))
        assertTrue(line.contains("mpv 运行中"))
        assertTrue(line.contains("RIFE DIRECTML"))
    }

    @Test
    fun `desktop player stage exposes transport targets for active and idle states`() {
        assertEquals(
            listOf(DesktopPlayerStageFocusTarget.Primary),
            desktopPlayerTransportTargets(isPlayerActive = false),
        )
        assertEquals(
            listOf(
                DesktopPlayerStageFocusTarget.SeekBack,
                DesktopPlayerStageFocusTarget.Primary,
                DesktopPlayerStageFocusTarget.SeekForward,
                DesktopPlayerStageFocusTarget.Stop,
            ),
            desktopPlayerTransportTargets(isPlayerActive = true),
        )
    }

    @Test
    fun `desktop player stage links return action and transport controls vertically`() {
        assertEquals(
            DesktopPlayerStageFocusTarget.Primary,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.BackToDetails,
                key = Key.DirectionDown,
                isPlayerActive = false,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.BackToDetails,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                key = Key.DirectionUp,
                isPlayerActive = false,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.BackToDetails,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.SeekForward,
                key = Key.DirectionUp,
                isPlayerActive = true,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.NextPanel,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                key = Key.DirectionDown,
                isPlayerActive = false,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.NextPanel,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Stop,
                key = Key.DirectionDown,
                isPlayerActive = true,
            ),
        )
    }

    @Test
    fun `desktop player stage moves horizontally only inside active transport controls`() {
        assertEquals(
            DesktopPlayerStageFocusTarget.SeekBack,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                key = Key.DirectionLeft,
                isPlayerActive = true,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.SeekForward,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                key = Key.DirectionRight,
                isPlayerActive = true,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.Stop,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.SeekForward,
                key = Key.DirectionRight,
                isPlayerActive = true,
            ),
        )
        assertEquals(
            null,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                key = Key.DirectionRight,
                isPlayerActive = false,
            ),
        )
        assertEquals(
            null,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.SeekBack,
                key = Key.DirectionLeft,
                isPlayerActive = true,
            ),
        )
        assertEquals(
            null,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Stop,
                key = Key.DirectionRight,
                isPlayerActive = true,
            ),
        )
    }

    @Test
    fun `desktop player settings form moves from fields into the TV control row`() {
        assertEquals(
            PlaybackSettingFocusTarget.StartSeconds,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.MediaPath, Key.DirectionRight),
        )
        assertEquals(
            PlaybackSettingFocusTarget.MediaPath,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.StartSeconds, Key.DirectionLeft),
        )
        assertEquals(
            PlaybackSettingFocusTarget.SubtitlePath,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.MediaPath, Key.DirectionDown),
        )
        assertEquals(
            PlaybackSettingFocusTarget.SubtitlePath,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.StartSeconds, Key.DirectionDown),
        )
        assertEquals(
            PlaybackSettingFocusTarget.PreviousPanel,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.MediaPath, Key.DirectionUp),
        )
        assertEquals(
            PlaybackSettingFocusTarget.MediaPath,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.SubtitlePath, Key.DirectionUp),
        )
        assertEquals(
            PlaybackSettingFocusTarget.Fullscreen,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.SubtitlePath, Key.DirectionDown),
        )
    }

    @Test
    fun `desktop player settings toggles move across the TV control row and adjacent panels`() {
        assertEquals(
            PlaybackSettingFocusTarget.KeepOpen,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.Fullscreen, Key.DirectionRight),
        )
        assertEquals(
            PlaybackSettingFocusTarget.RifeToggle,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.KeepOpen, Key.DirectionRight),
        )
        assertEquals(
            PlaybackSettingFocusTarget.RifeBackend,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.RifeToggle, Key.DirectionRight),
        )
        assertEquals(
            PlaybackSettingFocusTarget.RifeToggle,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.RifeBackend, Key.DirectionLeft),
        )
        assertEquals(
            null,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.Fullscreen, Key.DirectionLeft),
        )
        assertEquals(
            null,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.RifeBackend, Key.DirectionRight),
        )
        assertEquals(
            PlaybackSettingFocusTarget.SubtitlePath,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.KeepOpen, Key.DirectionUp),
        )
        assertEquals(
            PlaybackSettingFocusTarget.NextPanel,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.KeepOpen, Key.DirectionDown),
        )
    }

    @Test
    fun `desktop runtime controls move through the TV form column`() {
        assertEquals(
            RuntimeFocusTarget.ConfigDir,
            runtimeNavigationTarget(RuntimeFocusTarget.MpvPath, Key.DirectionDown),
        )
        assertEquals(
            RuntimeFocusTarget.CheckRuntime,
            runtimeNavigationTarget(RuntimeFocusTarget.ConfigDir, Key.DirectionDown),
        )
        assertEquals(
            RuntimeFocusTarget.ConfigDir,
            runtimeNavigationTarget(RuntimeFocusTarget.CheckRuntime, Key.DirectionUp),
        )
        assertEquals(
            RuntimeFocusTarget.PreviousPanel,
            runtimeNavigationTarget(RuntimeFocusTarget.MpvPath, Key.DirectionUp),
        )
        assertEquals(
            null,
            runtimeNavigationTarget(RuntimeFocusTarget.CheckRuntime, Key.DirectionDown),
        )
        assertEquals(
            null,
            runtimeNavigationTarget(RuntimeFocusTarget.ConfigDir, Key.DirectionRight),
        )
    }

    @Test
    fun `desktop player status text localizes backend statuses`() {
        assertEquals("mpv 待命。", desktopPlaybackStatusText("mpv is idle."))
        assertEquals("mpv 已启动：pid 1234", desktopPlaybackStatusText("mpv launched: pid 1234"))
        assertEquals("已后退 10 秒。", desktopPlaybackStatusText("mpv seeked back 10s."))
        assertEquals("已快进 30 秒。", desktopPlaybackStatusText("mpv seeked forward 30s."))
        assertEquals("播放进度已同步至 02:03。", desktopPlaybackStatusText("mpv position synced at 02:03."))
        assertEquals("custom status", desktopPlaybackStatusText("custom status"))
    }

    @Test
    fun `desktop player status text localizes actionable launch errors`() {
        assertEquals(
            "播放出错：找不到 mpv.exe：C:/MiruPlay/mpv.exe。请选择内置运行时路径、安装 mpv，或先检查运行时。",
            desktopPlaybackStatusText(
                "播放出错：mpv executable not found: C:/MiruPlay/mpv.exe. Choose the bundled runtime path, install mpv, or run Check runtime before launching.",
            ),
        )
        assertEquals(
            "播放出错：已开启 RIFE，但找不到脚本：C:/MiruPlay/portable_config/vs/MEMC_RIFE_DML.vpy。请选择已安装后端、准备内置运行时，或关闭 RIFE。",
            desktopPlaybackStatusText(
                "播放出错：RIFE is enabled but script was not found: C:/MiruPlay/portable_config/vs/MEMC_RIFE_DML.vpy. Pick an installed backend, prepare the bundled runtime, or turn RIFE off.",
            ),
        )
    }

    @Test
    fun `desktop runtime status text localizes verifier output`() {
        val status = desktopRuntimeStatusText(
            """
            Bundled mpv runtime is ready. RIFE: NVIDIA, DIRECTML. Manifest: present.

            Runtime manifest
            Verified at: 2026-05-15T00:00:00+08:00
            Source: D:/Downloads/mpv.exe
            Overlay source: D:/Downloads/mpv-vsNV.7z.001
            Runtime root: D:/WorkSpace/Android/MiruPlay/runtime/mpv
            Required RIFE: NVIDIA, DIRECTML
            Manifest files: mpv.exe, portable_config/
            """.trimIndent(),
        )

        assertTrue(status.contains("内置 mpv 运行时已就绪。RIFE：NVIDIA, DIRECTML。清单：已发现。"))
        assertTrue(status.contains("运行时清单"))
        assertTrue(status.contains("验证时间：2026-05-15T00:00:00+08:00"))
        assertTrue(status.contains("来源：D:/Downloads/mpv.exe"))
        assertTrue(status.contains("叠加包来源：D:/Downloads/mpv-vsNV.7z.001"))
        assertTrue(status.contains("运行时目录：D:/WorkSpace/Android/MiruPlay/runtime/mpv"))
        assertTrue(status.contains("要求的 RIFE：NVIDIA, DIRECTML"))
        assertTrue(status.contains("清单文件：mpv.exe, portable_config/"))
    }

    @Test
    fun `desktop runtime status text localizes missing runtime guidance`() {
        assertEquals(
            "mpv 运行时可播放。缺少 RIFE 脚本；请关闭 RIFE 或准备 RIFE 后端。",
            desktopRuntimeStatusText(
                "mpv runtime is playable. RIFE scripts are missing; leave RIFE off or prepare a RIFE backend.",
            ),
        )
        assertEquals(
            "mpv 运行时不完整。缺少：mpv.exe, portable_config/。",
            desktopRuntimeStatusText("mpv runtime is incomplete. Missing: mpv.exe, portable_config/."),
        )
        assertEquals(
            "mpv 运行时可播放，但运行时清单声明的条目缺失或无效：portable_config/vs/missing.vpy。清单：已发现。",
            desktopRuntimeStatusText(
                "mpv runtime is playable. Runtime manifest entries are missing or invalid: portable_config/vs/missing.vpy. Manifest: present.",
            ),
        )
        assertEquals(
            "运行时检查失败：missing path",
            desktopRuntimeStatusText("Runtime check failed: missing path"),
        )
    }

    @Test
    fun `desktop player start position formats seconds for the timeline`() {
        assertEquals("00:00", desktopPlaybackStartPositionLabel(""))
        assertEquals("01:30", desktopPlaybackStartPositionLabel("90"))
    }
}
