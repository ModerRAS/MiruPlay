package com.miruplay.tv.core.common

/**
 * 与语言无关的用户可见文本：纯 Kotlin 模块（core/*、repository-api、data、scanner …）
 * 以 UiText 传递用户文案，只在 Android UI 层解析成当前语言。
 *
 * - [key]：资源名（与各模块 values*/strings.xml 的 name 一一对应）
 * - [args]：格式化参数（String/Number，或嵌套 UiText，解析时递归处理）
 * - [fallback]：中文原文；资源缺失或纯 JVM 环境（单测）下的回退显示，
 *   也是中文文案的单测断言点（assertEquals("…", text.fallback)）。
 */
data class UiText(
    val key: String,
    val args: List<Any?> = emptyList(),
    val fallback: String,
) {
    override fun toString(): String = fallback
}

/** 无参数文案的快捷构造 */
fun uiText(key: String, fallback: String): UiText = UiText(key, emptyList(), fallback)

/** 单参数文案的快捷构造（fallback 在调用处已插值） */
fun uiText(key: String, arg: Any?, fallback: String): UiText = UiText(key, listOf(arg), fallback)

/** UiText 组合：把已构造的 UiText 作为另一条文案的参数 */
fun UiText.withArg(arg: Any?): UiText = copy(args = args + arg)
