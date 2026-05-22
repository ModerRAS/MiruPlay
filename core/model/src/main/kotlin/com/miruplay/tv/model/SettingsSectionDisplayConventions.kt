package com.miruplay.tv.model

enum class MiruPlaySettingsSection(
    val androidTvTitle: String,
    val androidTvDescription: String,
    val desktopTitle: String,
    val desktopDescription: String,
) {
    WEB_UI(
        androidTvTitle = "WebUI",
        androidTvDescription = "访问地址与二维码",
        desktopTitle = "WebUI",
        desktopDescription = "访问地址与二维码",
    ),
    SOURCES(
        androidTvTitle = "媒体源",
        androidTvDescription = "本地、WebDAV、SMB",
        desktopTitle = "媒体源",
        desktopDescription = "本地、WebDAV、SMB",
    ),
    PLAYBACK(
        androidTvTitle = "播放",
        androidTvDescription = "播完动作",
        desktopTitle = "播放",
        desktopDescription = "mpv 与 RIFE",
    ),
    CLOUD_DRIVE(
        androidTvTitle = "CloudDrive",
        androidTvDescription = "RSS 离线下载与入库",
        desktopTitle = "云盘",
        desktopDescription = "RSS 离线下载与入库",
    ),
    SCAN(
        androidTvTitle = "扫描",
        androidTvDescription = "媒体库更新策略",
        desktopTitle = "扫描",
        desktopDescription = "媒体库更新",
    ),
    LOG_UPLOAD(
        androidTvTitle = "日志上报",
        androidTvDescription = "OTLP / OpenObserve",
        desktopTitle = "日志",
        desktopDescription = "OTLP / OpenObserve",
    ),
    METADATA(
        androidTvTitle = "元数据",
        androidTvDescription = "Bangumi Token",
        desktopTitle = "元数据",
        desktopDescription = "Bangumi 匹配",
    ),
}

val androidTvSettingsSectionOrder: List<MiruPlaySettingsSection> =
    listOf(
        MiruPlaySettingsSection.WEB_UI,
        MiruPlaySettingsSection.SOURCES,
        MiruPlaySettingsSection.PLAYBACK,
        MiruPlaySettingsSection.CLOUD_DRIVE,
        MiruPlaySettingsSection.SCAN,
        MiruPlaySettingsSection.LOG_UPLOAD,
        MiruPlaySettingsSection.METADATA,
    )

val desktopSettingsSectionOrder: List<MiruPlaySettingsSection> =
    listOf(
        MiruPlaySettingsSection.SOURCES,
        MiruPlaySettingsSection.PLAYBACK,
        MiruPlaySettingsSection.CLOUD_DRIVE,
        MiruPlaySettingsSection.SCAN,
        MiruPlaySettingsSection.LOG_UPLOAD,
        MiruPlaySettingsSection.METADATA,
    )

fun MiruPlaySettingsSection.stepInSettingsOrder(
    order: List<MiruPlaySettingsSection>,
    delta: Int,
): MiruPlaySettingsSection? {
    val nextIndex = order.indexOf(this) + delta
    return order.getOrNull(nextIndex)
}

fun MiruPlaySettingsSection.stepAndroidTvSettingsSection(delta: Int): MiruPlaySettingsSection? =
    stepInSettingsOrder(androidTvSettingsSectionOrder, delta)

fun MiruPlaySettingsSection.stepDesktopSettingsSection(delta: Int): MiruPlaySettingsSection? =
    stepInSettingsOrder(desktopSettingsSectionOrder, delta)
