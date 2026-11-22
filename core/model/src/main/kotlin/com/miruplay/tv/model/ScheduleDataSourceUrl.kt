package com.miruplay.tv.model

import java.net.URI

/**
 * 时间表数据源地址的校验与归一化。
 *
 * 该地址由用户在运行时填写，仓库里不保存任何默认值/真实域名。规则：
 * - 只接受 http / https；
 * - 必须有 host，不能带空白字符；
 * - 不接受 query / fragment（它是 base URL，客户端再拼 `/api/schedule/latest`）；
 * - 去掉末尾的 `/`，避免拼出双斜杠；
 * - 用户直接粘贴完整接口地址（`.../api/schedule/latest[/]`）时自动去掉接口后缀，
 *   避免客户端再拼一次变成 `.../latest/api/schedule/latest` → 404。
 */
object ScheduleDataSourceUrl {

    private val LATEST_ENDPOINT_SUFFIX = Regex("(?i)/api/schedule/latest/?$")

    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it.isWhitespace() }) return null

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port !in 1..65_535) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null

        val normalized = trimmed.trimEnd('/')
            .replace(LATEST_ENDPOINT_SUFFIX, "")
            .trimEnd('/')
        return normalized.takeIf { it.isNotEmpty() }
    }

    fun isValid(raw: String?): Boolean = normalize(raw) != null

    /** 拼接最新的时间表接口地址；[baseUrl] 必须是已归一化的地址。 */
    fun latestScheduleEndpoint(baseUrl: String): String =
        "${baseUrl.trimEnd('/')}/api/schedule/latest"
}
