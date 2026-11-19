package com.miruplay.tv.data.schedule

import android.content.Context
import com.miruplay.tv.model.ScheduleDataSourceUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 时间表数据源地址的本地持久化（沿用项目现有 SharedPreferences 设置体系）。
 *
 * 默认空字符串表示未配置：老版本升级后没有这个 key，读到的就是空值，不需要迁移；
 * 地址只保存在本机，不写入仓库/文档/日志。
 */
@Singleton
class ScheduleDataSourcePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 运行时配置的原始地址；空表示未配置。 */
    var rawDataSourceUrl: String
        get() = prefs.getString(KEY_DATA_SOURCE_URL, null).orEmpty()
        set(value) {
            prefs.edit().putString(KEY_DATA_SOURCE_URL, value.trim()).apply()
        }

    /** 已归一化、可直接拼接口的地址；未配置或非法时为 null。 */
    fun configuredUrlOrNull(): String? = ScheduleDataSourceUrl.normalize(rawDataSourceUrl)

    /** 保存地址；非法地址返回 false 且不覆盖旧值（UI 会提示校验失败）。 */
    fun save(rawUrl: String?): Boolean {
        val normalized = ScheduleDataSourceUrl.normalize(rawUrl) ?: return false
        rawDataSourceUrl = normalized
        return true
    }

    fun clear() {
        prefs.edit().remove(KEY_DATA_SOURCE_URL).apply()
    }

    companion object {
        internal const val PREFS_NAME = "miruplay_schedule_prefs"
        internal const val KEY_DATA_SOURCE_URL = "schedule_data_source_url"
    }
}
