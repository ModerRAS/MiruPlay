package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryUiConventionsTest {
    @Test
    fun `shared library chrome labels match TV copy`() {
        assertEquals("探索", libraryTitleLabel().fallback)
        assertEquals("本地媒体库 · Bangumi 元数据", librarySubtitleLabel().fallback)
        assertEquals("扫描", libraryScanActionLabel().fallback)
        assertEquals("设置", librarySettingsActionLabel().fallback)
        assertEquals("添加源", libraryAddSourceActionLabel().fallback)
        assertEquals("扫描媒体库", libraryScanNowActionLabel().fallback)
        assertEquals("手动扫描", libraryManualScanActionLabel().fallback)
        assertEquals("取消扫描", libraryCancelScanActionLabel().fallback)
    }

    @Test
    fun `shared library empty and scanning messages are clamped`() {
        assertEquals("添加媒体源开始使用", libraryNoSourcesMessage().fallback)
        assertEquals("已配置媒体源\n点击扫描建立媒体库", libraryHasSourcesEmptyMessage().fallback)
        assertEquals("未找到番剧内容，请检查媒体源路径", libraryNoContentAfterScanMessage().fallback)
        assertEquals("正在扫描媒体库...", libraryScanningTitle().fallback)
        assertEquals("已扫描 0 个文件", libraryFilesScannedLabel(-1).fallback)
        assertEquals("已扫描 7 个文件", libraryFilesScannedLabel(7).fallback)
        assertEquals("扫描失败", libraryScanFailedMessage("").fallback)
        assertEquals("扫描失败：TimeoutException", libraryScanFailedMessage(" TimeoutException ").fallback)
        assertEquals("已收录 0 部", libraryCollectedCountLabel(-1).fallback)
        assertEquals("已收录 12 部", libraryCollectedCountLabel(12).fallback)
        assertEquals("搜索媒体库", librarySearchFieldLabel().fallback)
        assertEquals("搜索", librarySearchActionLabel().fallback)
        assertEquals("0 部", librarySearchResultCountLabel(-1).fallback)
        assertEquals("12 部", librarySearchResultCountLabel(12).fallback)
    }

    @Test
    fun `shared library scan statuses emit TV text and localize legacy wire copy`() {
        assertEquals("正在扫描：Library", libraryScanningStatus("Library").fallback)
        assertEquals("正在扫描：探索", libraryScanningStatus("").fallback)
        assertEquals("扫描完成：0 个视频，0 个目录。", libraryScanCompleteStatus(-1, -2).fallback)
        assertEquals("扫描完成：12 个视频，3 个目录。", libraryScanCompleteStatus(12, 3).fallback)
        assertEquals("重扫完成：12 个视频，3 个目录。", libraryRescanCompleteStatus(12, 3).fallback)

        assertEquals("正在扫描：Library", localizedLibraryScanningStatus("Library").fallback)
        assertEquals("正在扫描：探索", localizedLibraryScanningStatus("").fallback)
        assertEquals("扫描完成：0 个视频，0 个目录。", localizedLibraryScanCompleteStatus(-1, -2).fallback)
        assertEquals("正在扫描：Library", localizedLibraryScanStatusText(libraryScanningStatus("Library").fallback))
        assertEquals("扫描完成：12 个视频，3 个目录。", localizedLibraryScanStatusText(libraryScanCompleteStatus(12, 3).fallback))
        assertEquals("重扫完成：12 个视频，3 个目录。", localizedLibraryScanStatusText(libraryRescanCompleteStatus(12, 3).fallback))
        assertEquals("扫描完成：12 个视频，3 个目录。", localizedLibraryScanStatusText("Scan complete: 12 videos, 3 directories."))
        assertEquals("重扫完成：12 个视频，3 个目录。", localizedLibraryScanStatusText("Rescan complete: 12 videos, 3 directories."))
        assertEquals("正在扫描：Library", localizedLibraryScanStatusText("Scanning Library..."))
        assertEquals(null, localizedLibraryScanStatusText("custom status"))
    }

    @Test
    fun `shared library section titles match across platforms`() {
        assertEquals("最高热度", libraryFeaturedSectionTitle().fallback)
        assertEquals("继续观看", libraryContinueWatchingSectionTitle().fallback)
        assertEquals("继续观看", libraryContinueWatchingSubtitle(null).fallback)
        assertEquals("继续观看 01", libraryContinueWatchingSubtitle(1).fallback)
        assertEquals("继续观看 12", libraryContinueWatchingSubtitle(12).fallback)
        assertEquals("最近添加", libraryRecentlyAddedSectionTitle().fallback)
        assertEquals("海报墙", libraryPosterWallSectionTitle().fallback)
        assertEquals("刷新", recentPlaybackRefreshActionLabel().fallback)
        assertEquals("清除条目", recentPlaybackClearActionLabel().fallback)
        assertEquals("开始播放后会在这里显示最近记录。", recentPlaybackEmptyMessage().fallback)
        assertEquals("媒体详情", mediaDetailsSectionTitle().fallback)
        assertEquals("选择媒体后会在这里显示详细信息。", mediaDetailsEmptyMessage().fallback)
    }
}
