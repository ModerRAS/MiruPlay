package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.playbackChooseMediaLabel
import com.miruplay.tv.model.playbackEndReturnToDetailActionLabel
import com.miruplay.tv.model.playbackEndActionLabel
import com.miruplay.tv.model.playbackFullscreenToggleLabel
import com.miruplay.tv.model.playbackKeepOpenToggleLabel
import com.miruplay.tv.model.playbackLocalSourceLabel
import com.miruplay.tv.model.playbackMediaPathFieldLabel
import com.miruplay.tv.model.playbackMediaTitle
import com.miruplay.tv.model.playbackMpvRuntimeStateLabel
import com.miruplay.tv.model.mpvPlaybackStatusText
import com.miruplay.tv.model.playbackRemoteStreamLabel
import com.miruplay.tv.model.playbackRifeToggleLabel
import com.miruplay.tv.model.playbackRuntimeStatusText
import com.miruplay.tv.model.playbackStartPositionLabel
import com.miruplay.tv.model.playbackStartSecondsFieldLabel
import com.miruplay.tv.model.playbackSubtitlePathFieldLabel
import com.miruplay.tv.model.playbackUiLabels
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
        assertEquals(playbackChooseMediaLabel(), playbackMediaTitle(""))
        assertEquals("Frieren - S01E02", playbackMediaTitle("D:/Anime/Frieren - S01E02.mkv"))
    }

    @Test
    fun `desktop player controls use TV facing labels`() {
        val labels = playbackUiLabels()

        assertEquals(playbackMediaPathFieldLabel(), labels.mediaPath)
        assertEquals(playbackStartSecondsFieldLabel(), labels.startSeconds)
        assertEquals(playbackSubtitlePathFieldLabel(), labels.subtitlePath)
        assertEquals(playbackFullscreenToggleLabel(), labels.fullscreen)
        assertEquals(playbackKeepOpenToggleLabel(), labels.keepOpen)
        assertEquals(playbackRifeToggleLabel(), labels.rife)
        assertEquals(playbackEndReturnToDetailActionLabel(), PlaybackEndAction.RETURN_TO_DETAIL.playbackEndActionLabel())
        assertEquals("继续下一集", PlaybackEndAction.PLAY_NEXT_EPISODE.playbackEndActionLabel())
    }

    @Test
    fun `desktop player stage uses localized mpv chips`() {
        assertEquals("mpv 待命", playbackMpvRuntimeStateLabel(isPlayerActive = false))
        assertEquals("mpv 播放中", playbackMpvRuntimeStateLabel(isPlayerActive = true))
    }

    @Test
    fun `desktop player source line exposes mpv and RIFE state`() {
        val line = desktopPlaybackSourceLine(
            mediaPath = "https://example.test/video.mkv",
            rifeEnabled = true,
            rifeBackend = RifeBackend.DIRECTML,
            isPlayerActive = true,
        )

        assertTrue(line.contains(playbackRemoteStreamLabel()))
        assertTrue(line.contains(playbackMpvRuntimeStateLabel(true)))
        assertTrue(line.contains("RIFE DIRECTML"))
        assertTrue(
            desktopPlaybackSourceLine(
                mediaPath = "D:/Anime/Frieren.mkv",
                rifeEnabled = false,
                rifeBackend = RifeBackend.NVIDIA,
                isPlayerActive = false,
            ).contains(playbackLocalSourceLabel())
        )
    }

    @Test
    fun `desktop player seek labels use shared playback seconds`() {
        assertEquals(10, PLAYBACK_SEEK_BACK_SECONDS)
        assertEquals(30, PLAYBACK_SEEK_FORWARD_SECONDS)
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
    fun `desktop player stage navigation also accepts shared direction intents`() {
        assertEquals(
            DesktopPlayerStageFocusTarget.Primary,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.BackToDetails,
                intent = MiruPlayInputIntent.DirectionDown,
                isPlayerActive = false,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.BackToDetails,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                intent = MiruPlayInputIntent.DirectionUp,
                isPlayerActive = true,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.SeekBack,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                intent = MiruPlayInputIntent.DirectionLeft,
                isPlayerActive = true,
            ),
        )
        assertEquals(
            DesktopPlayerStageFocusTarget.SeekForward,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                intent = MiruPlayInputIntent.DirectionRight,
                isPlayerActive = true,
            ),
        )
        assertEquals(
            null,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                intent = MiruPlayInputIntent.DirectionRight,
                isPlayerActive = false,
            ),
        )
        assertEquals(
            null,
            desktopPlayerStageNavigationTarget(
                current = DesktopPlayerStageFocusTarget.Primary,
                intent = MiruPlayInputIntent.Activate,
                isPlayerActive = true,
            ),
        )
    }

    @Test
    fun `desktop player page keys mirror Android TV playback actions`() {
        assertEquals(DesktopPlayerKeyAction.Launch, desktopPlayerKeyAction(Key.DirectionCenter, isPlayerActive = false))
        assertEquals(DesktopPlayerKeyAction.Launch, desktopPlayerKeyAction(Key.Spacebar, isPlayerActive = false))
        assertEquals(DesktopPlayerKeyAction.Launch, desktopPlayerKeyAction(Key.MediaPlay, isPlayerActive = false))
        assertEquals(DesktopPlayerKeyAction.TogglePause, desktopPlayerKeyAction(Key.MediaPlayPause, isPlayerActive = true))
        assertEquals(DesktopPlayerKeyAction.Resume, desktopPlayerKeyAction(Key.MediaPlay, isPlayerActive = true))
        assertEquals(DesktopPlayerKeyAction.Pause, desktopPlayerKeyAction(Key.MediaPause, isPlayerActive = true))
        assertEquals(DesktopPlayerKeyAction.SeekBack, desktopPlayerKeyAction(Key.DirectionLeft, isPlayerActive = true))
        assertEquals(DesktopPlayerKeyAction.SeekForward, desktopPlayerKeyAction(Key.DirectionRight, isPlayerActive = true))
        assertEquals(DesktopPlayerKeyAction.Stop, desktopPlayerKeyAction(Key.MediaStop, isPlayerActive = true))
        assertEquals(null, desktopPlayerKeyAction(Key.MediaPause, isPlayerActive = false))
        assertEquals(null, desktopPlayerKeyAction(Key.DirectionUp, isPlayerActive = true))
    }

    @Test
    fun `desktop player key event dispatches only on key down`() {
        val actions = mutableListOf<DesktopPlayerKeyAction>()

        assertTrue(
            desktopPlayerKeyEvent(
                key = Key.MediaPlay,
                type = KeyEventType.KeyDown,
                isPlayerActive = true,
                onLaunch = { actions += DesktopPlayerKeyAction.Launch },
                onTogglePause = { actions += DesktopPlayerKeyAction.TogglePause },
                onResume = { actions += DesktopPlayerKeyAction.Resume },
                onPause = { actions += DesktopPlayerKeyAction.Pause },
                onSeekBack = { actions += DesktopPlayerKeyAction.SeekBack },
                onSeekForward = { actions += DesktopPlayerKeyAction.SeekForward },
                onStop = { actions += DesktopPlayerKeyAction.Stop },
            ),
        )
        assertEquals(listOf(DesktopPlayerKeyAction.Resume), actions)

        assertFalse(
            desktopPlayerKeyEvent(
                key = Key.MediaPause,
                type = KeyEventType.KeyUp,
                isPlayerActive = true,
                onLaunch = { actions += DesktopPlayerKeyAction.Launch },
                onTogglePause = { actions += DesktopPlayerKeyAction.TogglePause },
                onResume = { actions += DesktopPlayerKeyAction.Resume },
                onPause = { actions += DesktopPlayerKeyAction.Pause },
                onSeekBack = { actions += DesktopPlayerKeyAction.SeekBack },
                onSeekForward = { actions += DesktopPlayerKeyAction.SeekForward },
                onStop = { actions += DesktopPlayerKeyAction.Stop },
            ),
        )
        assertFalse(
            desktopPlayerKeyEvent(
                key = Key.DirectionUp,
                type = KeyEventType.KeyDown,
                isPlayerActive = true,
                onLaunch = { actions += DesktopPlayerKeyAction.Launch },
                onTogglePause = { actions += DesktopPlayerKeyAction.TogglePause },
                onResume = { actions += DesktopPlayerKeyAction.Resume },
                onPause = { actions += DesktopPlayerKeyAction.Pause },
                onSeekBack = { actions += DesktopPlayerKeyAction.SeekBack },
                onSeekForward = { actions += DesktopPlayerKeyAction.SeekForward },
                onStop = { actions += DesktopPlayerKeyAction.Stop },
            ),
        )
        assertEquals(listOf(DesktopPlayerKeyAction.Resume), actions)
    }

    @Test
    fun `desktop player page keys keep media controls global without stealing text navigation`() {
        assertEquals(DesktopPlayerKeyAction.Launch, desktopPlayerPageKeyAction(Key.MediaPlay, isPlayerActive = false))
        assertEquals(DesktopPlayerKeyAction.TogglePause, desktopPlayerPageKeyAction(Key.MediaPlayPause, isPlayerActive = true))
        assertEquals(DesktopPlayerKeyAction.Resume, desktopPlayerPageKeyAction(Key.MediaPlay, isPlayerActive = true))
        assertEquals(DesktopPlayerKeyAction.Pause, desktopPlayerPageKeyAction(Key.MediaPause, isPlayerActive = true))
        assertEquals(DesktopPlayerKeyAction.Stop, desktopPlayerPageKeyAction(Key.MediaStop, isPlayerActive = true))
        assertEquals(null, desktopPlayerPageKeyAction(Key.MediaPause, isPlayerActive = false))
        assertEquals(null, desktopPlayerPageKeyAction(Key.DirectionLeft, isPlayerActive = true))
        assertEquals(null, desktopPlayerPageKeyAction(Key.DirectionRight, isPlayerActive = true))
        assertEquals(null, desktopPlayerPageKeyAction(Key.Spacebar, isPlayerActive = true))
        assertEquals(null, desktopPlayerPageKeyAction(Key.Enter, isPlayerActive = true))
    }

    @Test
    fun `desktop player page key event dispatches global media keys only on key down`() {
        val actions = mutableListOf<DesktopPlayerKeyAction>()

        assertTrue(
            desktopPlayerPageKeyEvent(
                key = Key.MediaStop,
                type = KeyEventType.KeyDown,
                isPlayerActive = true,
                onLaunch = { actions += DesktopPlayerKeyAction.Launch },
                onTogglePause = { actions += DesktopPlayerKeyAction.TogglePause },
                onResume = { actions += DesktopPlayerKeyAction.Resume },
                onPause = { actions += DesktopPlayerKeyAction.Pause },
                onStop = { actions += DesktopPlayerKeyAction.Stop },
            ),
        )
        assertEquals(listOf(DesktopPlayerKeyAction.Stop), actions)

        assertFalse(
            desktopPlayerPageKeyEvent(
                key = Key.MediaPlayPause,
                type = KeyEventType.KeyUp,
                isPlayerActive = true,
                onLaunch = { actions += DesktopPlayerKeyAction.Launch },
                onTogglePause = { actions += DesktopPlayerKeyAction.TogglePause },
                onResume = { actions += DesktopPlayerKeyAction.Resume },
                onPause = { actions += DesktopPlayerKeyAction.Pause },
                onStop = { actions += DesktopPlayerKeyAction.Stop },
            ),
        )
        assertFalse(
            desktopPlayerPageKeyEvent(
                key = Key.DirectionLeft,
                type = KeyEventType.KeyDown,
                isPlayerActive = true,
                onLaunch = { actions += DesktopPlayerKeyAction.Launch },
                onTogglePause = { actions += DesktopPlayerKeyAction.TogglePause },
                onResume = { actions += DesktopPlayerKeyAction.Resume },
                onPause = { actions += DesktopPlayerKeyAction.Pause },
                onStop = { actions += DesktopPlayerKeyAction.Stop },
            ),
        )
        assertEquals(listOf(DesktopPlayerKeyAction.Stop), actions)
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
            PlaybackSettingFocusTarget.EndAction,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.SubtitlePath, Key.DirectionDown),
        )
        assertEquals(
            PlaybackSettingFocusTarget.SubtitlePath,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.EndAction, Key.DirectionUp),
        )
        assertEquals(
            PlaybackSettingFocusTarget.Fullscreen,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.EndAction, Key.DirectionDown),
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
            PlaybackSettingFocusTarget.EndAction,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.KeepOpen, Key.DirectionUp),
        )
        assertEquals(
            PlaybackSettingFocusTarget.NextPanel,
            playbackSettingNavigationTarget(PlaybackSettingFocusTarget.KeepOpen, Key.DirectionDown),
        )
    }

    @Test
    fun `desktop player settings toggles also accept shared direction intents`() {
        assertEquals(
            PlaybackSettingFocusTarget.KeepOpen,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.Fullscreen,
                MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            PlaybackSettingFocusTarget.RifeToggle,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.RifeBackend,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            PlaybackSettingFocusTarget.EndAction,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.KeepOpen,
                MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertEquals(
            PlaybackSettingFocusTarget.NextPanel,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.KeepOpen,
                MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertEquals(
            null,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.RifeBackend,
                MiruPlayInputIntent.DirectionRight,
            ),
        )
    }

    @Test
    fun `desktop player settings form also accepts shared direction intents`() {
        assertEquals(
            PlaybackSettingFocusTarget.StartSeconds,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.MediaPath,
                MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            PlaybackSettingFocusTarget.MediaPath,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.StartSeconds,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            PlaybackSettingFocusTarget.SubtitlePath,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.MediaPath,
                MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertEquals(
            PlaybackSettingFocusTarget.PreviousPanel,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.MediaPath,
                MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertEquals(
            PlaybackSettingFocusTarget.EndAction,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.SubtitlePath,
                MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertEquals(
            null,
            playbackSettingNavigationTarget(
                PlaybackSettingFocusTarget.MediaPath,
                MiruPlayInputIntent.Activate,
            ),
        )
    }

    @Test
    fun `desktop playback end action rows move left and right within the selection group`() {
        assertEquals(
            PlaybackEndAction.PLAY_NEXT_EPISODE,
            playbackEndActionNavigationTarget(PlaybackEndAction.RETURN_TO_DETAIL, Key.DirectionRight),
        )
        assertEquals(
            PlaybackEndAction.RETURN_TO_DETAIL,
            playbackEndActionNavigationTarget(PlaybackEndAction.PLAY_NEXT_EPISODE, Key.DirectionLeft),
        )
        assertEquals(
            null,
            playbackEndActionNavigationTarget(PlaybackEndAction.RETURN_TO_DETAIL, Key.DirectionLeft),
        )
    }

    @Test
    fun `desktop playback end action rows also accept shared direction intents`() {
        assertEquals(
            PlaybackEndAction.PLAY_NEXT_EPISODE,
            playbackEndActionNavigationTarget(
                PlaybackEndAction.RETURN_TO_DETAIL,
                MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            PlaybackEndAction.RETURN_TO_DETAIL,
            playbackEndActionNavigationTarget(
                PlaybackEndAction.PLAY_NEXT_EPISODE,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            null,
            playbackEndActionNavigationTarget(
                PlaybackEndAction.RETURN_TO_DETAIL,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            null,
            playbackEndActionNavigationTarget(
                PlaybackEndAction.RETURN_TO_DETAIL,
                MiruPlayInputIntent.Activate,
            ),
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
    fun `desktop runtime controls also accept shared direction intents`() {
        assertEquals(
            RuntimeFocusTarget.ConfigDir,
            runtimeNavigationTarget(RuntimeFocusTarget.MpvPath, MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            RuntimeFocusTarget.CheckRuntime,
            runtimeNavigationTarget(RuntimeFocusTarget.ConfigDir, MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            RuntimeFocusTarget.ConfigDir,
            runtimeNavigationTarget(RuntimeFocusTarget.CheckRuntime, MiruPlayInputIntent.DirectionUp),
        )
        assertEquals(
            RuntimeFocusTarget.PreviousPanel,
            runtimeNavigationTarget(RuntimeFocusTarget.MpvPath, MiruPlayInputIntent.DirectionUp),
        )
        assertEquals(null, runtimeNavigationTarget(RuntimeFocusTarget.CheckRuntime, MiruPlayInputIntent.DirectionDown))
        assertEquals(null, runtimeNavigationTarget(RuntimeFocusTarget.ConfigDir, MiruPlayInputIntent.DirectionRight))
    }

    @Test
    fun `desktop player status text localizes backend statuses`() {
        assertEquals("mpv 待命。", mpvPlaybackStatusText("mpv is idle."))
        assertEquals("mpv 已启动：pid 1234", mpvPlaybackStatusText("mpv launched: pid 1234"))
        assertEquals("已后退 10 秒。", mpvPlaybackStatusText("mpv seeked back 10s."))
        assertEquals("已快进 30 秒。", mpvPlaybackStatusText("mpv seeked forward 30s."))
        assertEquals("播放进度已同步至 02:03。", mpvPlaybackStatusText("mpv position synced at 02:03."))
        assertEquals("custom status", mpvPlaybackStatusText("custom status"))
    }

    @Test
    fun `desktop player status text localizes actionable launch errors`() {
        assertEquals(
            "播放出错：找不到 mpv.exe：C:/MiruPlay/mpv.exe。请选择内置运行时路径、安装 mpv，或先检查运行时。",
            mpvPlaybackStatusText(
                "播放出错：mpv executable not found: C:/MiruPlay/mpv.exe. Choose the bundled runtime path, install mpv, or run Check runtime before launching.",
            ),
        )
        assertEquals(
            "播放出错：已开启 RIFE，但找不到脚本：C:/MiruPlay/portable_config/vs/MEMC_RIFE_DML.vpy。请选择已安装后端、准备内置运行时，或关闭 RIFE。",
            mpvPlaybackStatusText(
                "播放出错：RIFE is enabled but script was not found: C:/MiruPlay/portable_config/vs/MEMC_RIFE_DML.vpy. Pick an installed backend, prepare the bundled runtime, or turn RIFE off.",
            ),
        )
    }

    @Test
    fun `desktop runtime status text localizes verifier output`() {
        val status = playbackRuntimeStatusText(
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
            playbackRuntimeStatusText(
                "mpv runtime is playable. RIFE scripts are missing; leave RIFE off or prepare a RIFE backend.",
            ),
        )
        assertEquals(
            "mpv 运行时不完整。缺少：mpv.exe, portable_config/。",
            playbackRuntimeStatusText("mpv runtime is incomplete. Missing: mpv.exe, portable_config/."),
        )
        assertEquals(
            "mpv 运行时可播放，但运行时清单声明的条目缺失或无效：portable_config/vs/missing.vpy。清单：已发现。",
            playbackRuntimeStatusText(
                "mpv runtime is playable. Runtime manifest entries are missing or invalid: portable_config/vs/missing.vpy. Manifest: present.",
            ),
        )
        assertEquals(
            "运行时检查失败：missing path",
            playbackRuntimeStatusText("Runtime check failed: missing path"),
        )
    }

    @Test
    fun `desktop player start position formats seconds for the timeline`() {
        assertEquals("00:00", playbackStartPositionLabel(""))
        assertEquals("01:30", playbackStartPositionLabel("90"))
    }
}
