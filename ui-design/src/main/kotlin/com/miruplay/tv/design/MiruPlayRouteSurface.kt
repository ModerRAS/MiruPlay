package com.miruplay.tv.design

object MiruPlayRouteSurface {
    const val LIBRARY_ID = "library"
    const val DETAILS_ID = "details"
    const val PLAYER_ID = "player"
    const val SETTINGS_ID = "settings"

    const val LIBRARY_ROUTE = LIBRARY_ID
    const val SETTINGS_ROUTE = SETTINGS_ID
    const val ANIME_ROUTE_PREFIX = "anime"
    const val PLAYER_ROUTE_PREFIX = PLAYER_ID

    val library = Section(
        id = LIBRARY_ID,
        menuLabel = "探索",
        title = "探索",
        subtitle = "本地媒体库 · Bangumi 元数据",
        summary = "媒体源与索引内容",
    )

    val details = Section(
        id = DETAILS_ID,
        menuLabel = "详情",
        title = "详情",
        subtitle = "番剧资料 · Bangumi 匹配",
        summary = "搜索、批量审核、撤销",
    )

    val player = Section(
        id = PLAYER_ID,
        menuLabel = "播放",
        title = "播放",
        subtitle = "mpv 播放控制 · RIFE 插帧",
        summary = "启动、进度、运行时",
    )

    val settings = Section(
        id = SETTINGS_ID,
        menuLabel = "设置",
        title = "设置",
        subtitle = "自动化、RSS 与桌面服务",
        summary = "CloudDrive2 与 RSS",
    )

    val desktopSectionOrder = listOf(library, details, player, settings)

    fun sectionForId(id: String?): Section? {
        val normalized = id?.trim()?.lowercase().orEmpty()
        return desktopSectionOrder.firstOrNull { it.id == normalized }
    }

    fun desktopSectionStep(section: Section, delta: Int): Section? {
        return desktopSectionOrder.focusTargetAfter(section, delta)
    }

    fun backTarget(section: Section): Section? =
        when (section.id) {
            PLAYER_ID -> details
            DETAILS_ID,
            SETTINGS_ID,
            -> library
            LIBRARY_ID -> null
            else -> library
        }

    data class Section(
        val id: String,
        val menuLabel: String,
        val title: String,
        val subtitle: String,
        val summary: String,
    )
}
