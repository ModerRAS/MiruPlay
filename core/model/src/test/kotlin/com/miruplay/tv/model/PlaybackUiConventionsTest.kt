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
