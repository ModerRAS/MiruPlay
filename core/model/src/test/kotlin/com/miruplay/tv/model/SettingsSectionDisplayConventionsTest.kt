package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsSectionDisplayConventionsTest {
    @Test
    fun `settings section copy is shared by Android TV and desktop`() {
        assertEquals(
            listOf("WebUI", "媒体源", "播放", "CloudDrive", "扫描", "日志上报", "元数据"),
            androidTvSettingsSectionOrder.map { it.androidTvTitle },
        )
        assertEquals(
            listOf("访问地址与二维码", "本地、WebDAV、SMB", "播完动作", "RSS 离线下载与入库", "媒体库更新策略", "OpenObserve JSON", "Bangumi Token"),
            androidTvSettingsSectionOrder.map { it.androidTvDescription },
        )
        assertEquals(
            listOf("媒体源", "播放", "云盘", "扫描", "日志", "元数据"),
            desktopSettingsSectionOrder.map { it.desktopTitle },
        )
        assertEquals(
            listOf("本地、WebDAV、SMB", "mpv 与 RIFE", "RSS 离线下载与入库", "媒体库更新", "OpenObserve JSON", "Bangumi 匹配"),
            desktopSettingsSectionOrder.map { it.desktopDescription },
        )
    }

    @Test
    fun `settings section orders keep platform entry points explicit`() {
        assertEquals(MiruPlaySettingsSection.WEB_UI, androidTvSettingsSectionOrder.first())
        assertEquals(MiruPlaySettingsSection.SOURCES, desktopSettingsSectionOrder.first())
        assertEquals(MiruPlaySettingsSection.METADATA, androidTvSettingsSectionOrder.last())
        assertEquals(MiruPlaySettingsSection.METADATA, desktopSettingsSectionOrder.last())
    }

    @Test
    fun `settings section navigation stops at platform list edges`() {
        assertNull(MiruPlaySettingsSection.WEB_UI.stepAndroidTvSettingsSection(-1))
        assertEquals(
            MiruPlaySettingsSection.SOURCES,
            MiruPlaySettingsSection.WEB_UI.stepAndroidTvSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.CLOUD_DRIVE,
            MiruPlaySettingsSection.PLAYBACK.stepAndroidTvSettingsSection(1),
        )
        assertNull(MiruPlaySettingsSection.METADATA.stepAndroidTvSettingsSection(1))

        assertNull(MiruPlaySettingsSection.SOURCES.stepDesktopSettingsSection(-1))
        assertEquals(
            MiruPlaySettingsSection.PLAYBACK,
            MiruPlaySettingsSection.SOURCES.stepDesktopSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.CLOUD_DRIVE,
            MiruPlaySettingsSection.PLAYBACK.stepDesktopSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.SCAN,
            MiruPlaySettingsSection.CLOUD_DRIVE.stepDesktopSettingsSection(1),
        )
        assertNull(MiruPlaySettingsSection.METADATA.stepDesktopSettingsSection(1))
    }
}
