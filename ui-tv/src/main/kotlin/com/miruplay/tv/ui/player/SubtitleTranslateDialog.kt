package com.miruplay.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.miruplay.tv.ui.tv.R
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.miruplay.tv.translation.SUPPORTED_TARGET_LANGUAGES
import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.rawUiText
import com.miruplay.tv.core.common.uiText
import com.miruplay.tv.ui.common.resolve
import com.miruplay.tv.translation.TranslationProvider
import com.miruplay.tv.ui.components.rememberInitialFocusHandle
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography

/**
 * YouTube 式字幕翻译对话框：先选翻译服务，再点目标语言即开始翻译。
 * 翻译中显示进度，出错在对话框内显示消息，成功后由 [onTranslated] 收尾。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubtitleTranslateDialog(
    sourceTrackLabel: String?,
    deepSeekKeyConfigured: Boolean,
    defaultTargetLanguageCode: String,
    state: SubtitleTranslationState,
    onTranslate: (TranslationProvider, String) -> Unit,
    onDismiss: () -> Unit,
    onTranslated: () -> Unit,
) {
    val languages = remember { SUPPORTED_TARGET_LANGUAGES }
    val defaultLanguage = languages.firstOrNull { it.code == defaultTargetLanguageCode } ?: languages.first()
    var selectedProvider by remember { mutableStateOf(TranslationProvider.GOOGLE) }
    var selectedLanguageCode by remember { mutableStateOf(defaultLanguage.code) }
    var translationStarted by remember { mutableStateOf(false) }
    val isTranslating = state is SubtitleTranslationState.Translating
    val initialFocusHandle = rememberInitialFocusHandle()

    LaunchedEffect(state) {
        when (state) {
            is SubtitleTranslationState.Translating -> translationStarted = true
            is SubtitleTranslationState.Error -> translationStarted = false
            SubtitleTranslationState.Idle -> if (translationStarted) {
                translationStarted = false
                onTranslated()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 720.dp, max = 980.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.translate_dialog_title),
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
            if (sourceTrackLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.translate_dialog_source, sourceTrackLabel ?: ""),
                    style = TvTypography.body,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.translate_provider_title),
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerOptionButton(
                    text = subtitleTranslationProviderLabel(TranslationProvider.GOOGLE).resolve(),
                    selected = selectedProvider == TranslationProvider.GOOGLE,
                    enabled = !isTranslating,
                    onClick = { selectedProvider = TranslationProvider.GOOGLE },
                )
                PlayerOptionButton(
                    text = subtitleTranslationProviderLabel(TranslationProvider.BING).resolve(),
                    selected = selectedProvider == TranslationProvider.BING,
                    enabled = !isTranslating,
                    onClick = { selectedProvider = TranslationProvider.BING },
                )
                PlayerOptionButton(
                    text = subtitleTranslationProviderLabel(
                        TranslationProvider.DEEPSEEK,
                        deepSeekKeyConfigured = deepSeekKeyConfigured,
                    ).resolve(),
                    selected = selectedProvider == TranslationProvider.DEEPSEEK,
                    enabled = !isTranslating,
                    onClick = { selectedProvider = TranslationProvider.DEEPSEEK },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.translate_target_title),
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                languages.forEach { language ->
                    PlayerOptionButton(
                        text = language.displayName.resolve(),
                        selected = language.code == selectedLanguageCode,
                        enabled = !isTranslating,
                        onClick = {
                            selectedLanguageCode = language.code
                            onTranslate(selectedProvider, language.code)
                        },
                        modifier = if (language.code == defaultLanguage.code) {
                            Modifier.then(initialFocusHandle.modifier())
                        } else {
                            Modifier
                        },
                    )
                }
            }
            if (isTranslating) {
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = AnimeRed,
                    )
                    Text(
                        text = stringResource(R.string.translate_in_progress),
                        style = TvTypography.body,
                        color = TextPrimary,
                    )
                }
            }
            (state as? SubtitleTranslationState.Error)?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error.message.resolve(),
                    style = TvTypography.body,
                    color = AnimeRed,
                )
            }
        }
    }
}

private fun subtitleTranslationProviderLabel(
    provider: TranslationProvider,
    deepSeekKeyConfigured: Boolean = true,
): UiText = when (provider) {
    TranslationProvider.GOOGLE -> uiText("translate_provider_google", "Google 翻译")
    TranslationProvider.BING -> uiText("translate_provider_bing", "必应翻译")
    TranslationProvider.DEEPSEEK ->
        if (deepSeekKeyConfigured) {
            rawUiText("DeepSeek")
        } else {
            uiText("translate_provider_deepseek_missing", "DeepSeek（需在 WebControl 设置 Key）")
        }
}
