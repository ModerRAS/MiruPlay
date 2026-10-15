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

/** 无需翻译的原文透传（文件名、URL、数字等，或兼容层归一化后的旧文案） */
fun rawUiText(value: String): UiText = UiText("", emptyList(), value)

/** 无参数文案的快捷构造 */
fun uiText(key: String, fallback: String): UiText = UiText(key, emptyList(), fallback)

private val joinKeys = mapOf(
    1 to "join_1",
    2 to "join_2",
    3 to "join_3",
    4 to "join_4",
    5 to "join_5",
    6 to "join_6",
)

/**
 * 可变数量文案的「 · 」拼接（各语言共用同一分隔符）。
 * 空列表回退为空原文；超长列表截断到 6 段。
 */
fun joinUiText(parts: List<UiText>): UiText {
    if (parts.isEmpty()) return rawUiText("")
    val safe = parts.take(6)
    val key = joinKeys[safe.size] ?: "join_6"
    return UiText(key, safe, safe.joinToString(" · ") { it.fallback })
}

private val joinCommaKeys = mapOf(
    1 to "join_comma_1",
    2 to "join_comma_2",
    3 to "join_comma_3",
)

/** 可变数量文案的逗号拼接（中文「，」/ 英文 ", " 由资源决定）。 */
fun joinCommaUiText(parts: List<UiText>): UiText {
    if (parts.isEmpty()) return rawUiText("")
    val safe = parts.take(3)
    val key = joinCommaKeys[safe.size] ?: "join_comma_3"
    val separator = if (safe.size == 1) "" else "，"
    return UiText(key, safe, safe.joinToString(separator) { it.fallback })
}

/** 单参数文案的快捷构造（fallback 在调用处已插值） */
fun uiText(key: String, arg: Any?, fallback: String): UiText = UiText(key, listOf(arg), fallback)

/**
 * 带严重程度的用户消息：替代散落在 UI 里的 `contains("失败")` 文案嗅探。
 * ViewModel 以 Info/Error 显式构造，渲染层按类型着色。
 */
sealed interface UiMessage {
    val text: UiText
    data class Info(override val text: UiText) : UiMessage
    data class Error(override val text: UiText) : UiMessage
}

fun UiText.asInfo(): UiMessage = UiMessage.Info(this)

fun UiText.asError(): UiMessage = UiMessage.Error(this)

/** 透传的异常/诊断原文（展示用中文兜底），标记为 Error。 */
fun errorMessage(value: String): UiMessage = UiMessage.Error(rawUiText(value))

/** UiText 组合：把已构造的 UiText 作为另一条文案的参数 */
fun UiText.withArg(arg: Any?): UiText = copy(args = args + arg)
