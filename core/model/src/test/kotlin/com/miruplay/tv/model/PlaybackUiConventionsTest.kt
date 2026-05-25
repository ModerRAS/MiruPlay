package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackUiConventionsTest {
    @Test
    fun `shared playback chrome labels match TV copy`() {
        assertEquals("返回", playbackBackLabel())
        assertEquals("返回详情", playbackBackToDetailsLabel())
        assertEquals("播放", playbackPlayLabel())
        assertEquals("暂停", playbackPauseLabel())
        assertEquals("重试", playbackRetryLabel())
        assertEquals("停止", playbackStopLabel())
        assertEquals("播放失败", playbackErrorTitle())
        assertEquals("选择媒体", playbackChooseMediaLabel())
        assertEquals("等待媒体", playbackWaitingForMediaLabel())
        assertEquals("本地播放", playbackLocalSourceLabel())
        assertEquals("远程串流", playbackRemoteStreamLabel())
        assertEquals("字幕外载", playbackExternalSubtitleLabel())
    }

    @Test
    fun `shared playback menu labels format counts and speeds`() {
        assertEquals("播放速度", playbackSpeedMenuTitle())
        assertEquals("字幕", playbackSubtitlesMenuTitle())
        assertEquals("音轨", playbackAudioMenuTitle())
        assertEquals("无字幕", playbackSubtitleCountLabel(0))
        assertEquals("字幕 2", playbackSubtitleCountLabel(2))
        assertEquals("音轨 0", playbackAudioTrackCountLabel(-1))
        assertEquals("音轨 3", playbackAudioTrackCountLabel(3))
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), playbackSpeedOptions())
        assertEquals("1x", playbackSpeedValueLabel(1f))
        assertEquals("1.25x", playbackSpeedValueLabel(1.25f))
        assertEquals("倍速 0.75x", playbackSpeedChipLabel(0.75f))
        assertEquals(PLAYBACK_SPEED_MIN, coercePlaybackSpeed(0.1f))
        assertEquals(PLAYBACK_SPEED_MAX, coercePlaybackSpeed(4.0f))
        assertEquals(PLAYBACK_SPEED_NORMAL, coercePlaybackSpeed(Float.NaN))
    }

    @Test
    fun `shared playback option labels prefer titles then language then fallback`() {
        assertEquals(
            "繁体中文",
            playbackSubtitleOptionLabel(
                SubtitleTrack(language = "zh-Hant", title = "繁体中文", path = "subtitle.ass"),
                index = 0,
            ),
        )
        assertEquals(
            "jpn",
            playbackSubtitleOptionLabel(
                SubtitleTrack(language = "jpn", title = "", path = "subtitle.ass"),
                index = 1,
            ),
        )
        assertEquals(
            "字幕 3",
            playbackSubtitleOptionLabel(
                SubtitleTrack(language = "", title = "", path = "subtitle.ass"),
                index = 2,
            ),
        )
        assertEquals("日语", playbackAudioOptionLabel(title = "日语", language = "jpn", index = 0))
        assertEquals("eng", playbackAudioOptionLabel(title = "", language = "eng", index = 1))
        assertEquals("音轨 3", playbackAudioOptionLabel(title = null, language = "", index = 2))
    }

    @Test
    fun `shared playback seek labels include configured seconds`() {
        assertEquals(10, PLAYBACK_SEEK_BACK_SECONDS)
        assertEquals(30, PLAYBACK_SEEK_FORWARD_SECONDS)
        assertEquals("快退 10 秒", playbackSeekBackLabel(10))
        assertEquals("快进 30 秒", playbackSeekForwardLabel(30))
        assertEquals("-10", playbackSeekBackCompactLabel())
        assertEquals("+30", playbackSeekForwardCompactLabel())
        assertEquals("--:--", playbackUnknownDurationLabel())
    }

    @Test
    fun `shared desktop playback labels cover mpv RIFE settings`() {
        assertEquals("播放设置", playbackSettingsTitleLabel())
        assertEquals("运行时", playbackRuntimeTitleLabel())
        assertEquals("mpv 诊断", playbackDiagnosticsTitleLabel())
        assertEquals("媒体 URI 或文件路径", playbackMediaPathFieldLabel())
        assertEquals("起播秒数", playbackStartSecondsFieldLabel())
        assertEquals("外挂字幕路径", playbackSubtitlePathFieldLabel())
        assertEquals("全屏", playbackFullscreenToggleLabel())
        assertEquals("播完保留窗口", playbackKeepOpenToggleLabel())
        assertEquals("RIFE", playbackRifeToggleLabel())
        assertEquals("mpv.exe", playbackMpvExecutableFieldLabel())
        assertEquals("portable_config", playbackPortableConfigFieldLabel())
        assertEquals("检查运行时", playbackCheckRuntimeActionLabel())
        assertEquals("请先选择媒体，再启动 mpv。", playbackBlankMediaMessage())
        assertEquals("无法生成 mpv 命令。", playbackCommandPreviewErrorMessage())
        assertEquals("mpv 待命", playbackMpvRuntimeStateLabel(false))
        assertEquals("mpv 播放中", playbackMpvRuntimeStateLabel(true))
        assertEquals("RIFE 关闭", playbackRifeStateLabel(false, "DIRECTML"))
        assertEquals("RIFE DIRECTML", playbackRifeStateLabel(true, "DIRECTML"))
    }

    @Test
    fun `shared mpv playback status text localizes stable wire statuses`() {
        assertEquals("选择媒体", playbackMediaTitle(""))
        assertEquals("Frieren - 02", playbackMediaTitle("D:/Anime/Frieren - 02.mkv"))
        assertEquals(
            "远程串流 · mpv 播放中 · RIFE DIRECTML",
            mpvPlaybackSourceLine("https://example.test/video.mkv", true, "DIRECTML", true),
        )
        assertEquals("00:01", playbackStartPositionLabel("1.4"))
        assertEquals("mpv 待命。", mpvPlaybackStatusText(""))
        assertEquals("mpv 待命。", mpvPlaybackStatusText("mpv is idle."))
        assertEquals("mpv 已启动：pid 42", mpvPlaybackStatusText("mpv launched: pid 42"))
        assertEquals("无法启动 mpv。", mpvPlaybackStatusText("Unable to launch mpv."))
        assertEquals("没有正在运行的 mpv 进程。", mpvPlaybackStatusText("No mpv process is active."))
        assertEquals("已切换暂停状态。", mpvPlaybackStatusText("mpv pause toggled."))
        assertEquals("已继续播放。", mpvPlaybackStatusText("mpv resumed."))
        assertEquals("已暂停播放。", mpvPlaybackStatusText("mpv paused."))
        assertEquals("已后退 10 秒。", mpvPlaybackStatusText("mpv seeked back 10s."))
        assertEquals("已快进 30 秒。", mpvPlaybackStatusText("mpv seeked forward 30s."))
        assertEquals("播放速度已设为 1.25x。", mpvPlaybackStatusText("mpv speed set to 1.25x."))
        assertEquals("mpv 已停止。", mpvPlaybackStatusText("mpv stopped."))
        assertEquals("mpv 已退出。", mpvPlaybackStatusText("mpv exited."))
        assertEquals("播放已完成：24:00。", mpvPlaybackStatusText("mpv playback completed at 24:00."))
        assertEquals("播放进度已同步至 00:30。", mpvPlaybackStatusText("mpv position synced at 00:30."))
        assertEquals("请先选择媒体，再启动 mpv。", mpvPlaybackStatusText("Choose a media URI or file path before launching mpv."))
        assertEquals("无法生成 mpv 命令。", mpvPlaybackStatusText("Unable to build mpv command."))
    }

    @Test
    fun `shared runtime status text localizes manifest reports`() {
        assertEquals("尚未检查运行时。", playbackRuntimeStatusText(""))
        assertEquals(
            "内置 mpv 运行时已就绪。RIFE：DIRECTML。清单：已发现。",
            playbackRuntimeStatusText("Bundled mpv runtime is ready. RIFE: DIRECTML. Manifest: present."),
        )
        assertEquals(
            "mpv 运行时可播放。缺少 RIFE 脚本；请关闭 RIFE 或准备 RIFE 后端。",
            playbackRuntimeStatusText("mpv runtime is playable. RIFE scripts are missing; leave RIFE off or prepare a RIFE backend."),
        )
        assertEquals(
            "运行时目录：D:/MiruPlay/runtime",
            playbackRuntimeStatusText("Runtime root: D:/MiruPlay/runtime"),
        )
        assertEquals("要求的 RIFE：无", playbackRuntimeStatusText("Required RIFE: none"))
    }

    @Test
    fun `shared playback end settings copy matches TV settings`() {
        assertEquals(PlaybackEndAction.RETURN_TO_DETAIL, PlaybackEndAction.fromStorageValue(null))
        assertEquals(PlaybackEndAction.RETURN_TO_DETAIL, PlaybackEndAction.fromStorageValue("return_to_detail"))
        assertEquals(PlaybackEndAction.PLAY_NEXT_EPISODE, PlaybackEndAction.fromStorageValue("play_next_episode"))
        assertEquals("播放结束", playbackEndSettingsTitleLabel())
        assertEquals("选定剧集播完后，可以直接回到详情页，也可以自动切到下一集。", playbackEndSettingsDescriptionLabel())
        assertEquals("返回详情", playbackEndReturnToDetailActionLabel())
        assertEquals("继续下一集", playbackEndPlayNextEpisodeActionLabel())
        assertEquals("播完后会停在详情页，方便手动挑下一集。", playbackEndReturnToDetailDetail())
        assertEquals("播完后会自动开始下一集，没有下一集时会回到详情页。", playbackEndPlayNextEpisodeDetail())
        assertEquals("播完返回", playbackEndReturnToDetailSummary())
        assertEquals("自动下一集", playbackEndPlayNextEpisodeSummary())
        assertEquals("播完返回", PlaybackEndAction.RETURN_TO_DETAIL.playbackEndMenuSummary())
        assertEquals("自动下一集", PlaybackEndAction.PLAY_NEXT_EPISODE.playbackEndMenuSummary())
    }
}
