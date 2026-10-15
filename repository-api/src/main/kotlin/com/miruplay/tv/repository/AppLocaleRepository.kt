package com.miruplay.tv.repository

/** 应用界面语言；SYSTEM = 跟随系统，其他为手动指定。id 即 BCP-47，用于 res 限定符匹配。 */
enum class AppLanguage {
    SYSTEM,
    ZH_CN,
    ZH_TW,
    ZH_HK,
    EN_US,
    EN_GB;

    val id: String
        get() = when (this) {
            SYSTEM -> "system"
            ZH_CN -> "zh-CN"
            ZH_TW -> "zh-TW"
            ZH_HK -> "zh-HK"
            EN_US -> "en-US"
            EN_GB -> "en-GB"
        }

    /** 本语言自称（不翻译，直接展示）。SYSTEM 由调用方按当前语言渲染。 */
    val autonym: String
        get() = when (this) {
            SYSTEM -> ""
            ZH_CN -> "简体中文"
            ZH_TW -> "繁體中文（台灣）"
            ZH_HK -> "繁體中文（香港）"
            EN_US -> "English (US)"
            EN_GB -> "English (UK)"
        }

    fun toJavaLocale(): java.util.Locale? =
        if (this == SYSTEM) null else java.util.Locale.forLanguageTag(id)

    companion object {
        fun fromId(value: String?): AppLanguage? =
            entries.firstOrNull { it.id.equals(value?.trim(), ignoreCase = true) }
    }
}

/** 持久化的界面语言选择；data 模块提供实现，TV 设置 / WebAPI / WebUI 共享同一存储 */
interface AppLocaleStore {
    var appLanguage: AppLanguage

    /** 语言被其他表面（WebAPI/WebUI）修改时回调；默认不通知 */
    fun addLanguageChangeListener(onChanged: (AppLanguage) -> Unit): java.io.Closeable =
        java.io.Closeable { }

    companion object {
        const val PREFS_FILE = "miruplay_app_locale_prefs"
        const val KEY_APP_LANGUAGE = "app_language"
    }
}
