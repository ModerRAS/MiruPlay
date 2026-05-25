package com.miruplay.tv.model

fun bangumiSyncMissingTokenMessage(): String =
    "请先在设置里保存 Access Token"

fun bangumiSyncMissingSubjectIdMessage(): String =
    "当前番剧还没有 Bangumi 条目 ID，请先重新刮削"

fun bangumiSyncNoEpisodesMessage(): String =
    "当前番剧没有可同步剧集"

fun bangumiSyncFailedMessage(): String =
    "同步失败"

fun bangumiSyncEpisodeFailedMessage(): String =
    "同步剧集失败"
