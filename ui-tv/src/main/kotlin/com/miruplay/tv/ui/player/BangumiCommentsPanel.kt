package com.miruplay.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.miruplay.tv.repository.BangumiCommentContent
import com.miruplay.tv.repository.BangumiEpisodeComment
import com.miruplay.tv.ui.components.RemoteImage
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import java.net.URI

@Composable
internal fun BangumiCommentsPanel(
    state: EpisodeCommentsUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.comments, state.isLoading, state.errorMessage) {
        if (!state.isLoading && (state.comments.isNotEmpty() || state.errorMessage != null)) {
            focusRequester.requestFocus()
        }
    }
    Column(
        modifier = modifier
            .width(620.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.84f))
            .border(1.dp, Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 24.dp, vertical = 26.dp),
    ) {
        Text("Bangumi 当集评论", style = TvTypography.subtitle, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.episodeId?.let { "Bangumi Ep. $it" } ?: "当前剧集",
            style = TvTypography.caption,
            color = TextSecondary,
        )
        Spacer(Modifier.height(18.dp))

        when {
            state.isLoading && state.comments.isEmpty() -> PanelMessage("正在加载评论…")
            state.errorMessage != null && state.comments.isEmpty() -> {
                PanelActionMessage(
                    message = state.errorMessage,
                    action = "重试",
                    onClick = onRetry,
                    actionModifier = Modifier.focusRequester(focusRequester),
                )
            }
            state.comments.isEmpty() -> PanelMessage("这一集还没有评论。")
            else -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.comments, key = { _, comment -> comment.id }) { index, comment ->
                    BangumiCommentThread(
                        comment = comment,
                        focusRequester = focusRequester.takeIf { index == 0 },
                    )
                }
                if (state.hasMore || state.errorMessage != null) {
                    item {
                        PanelActionMessage(
                            message = state.errorMessage ?: if (state.isLoading) "正在加载更多…" else "还有更多评论",
                            action = if (state.errorMessage != null) "重试" else "加载更多",
                            onClick = onLoadMore,
                            enabled = !state.isLoading,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BangumiCommentThread(
    comment: BangumiEpisodeComment,
    depth: Int = 0,
    focusRequester: FocusRequester? = null,
) {
    Column(
        modifier = Modifier.padding(start = (depth.coerceAtMost(3) * 30).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BangumiCommentCard(comment, isReply = depth > 0, focusRequester = focusRequester)
        comment.replies.forEach { reply ->
            BangumiCommentThread(reply, depth + 1)
        }
    }
}

@Composable
private fun BangumiCommentCard(
    comment: BangumiEpisodeComment,
    isReply: Boolean,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RemoteImage(
            url = comment.user.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(if (isReply) 36.dp else 44.dp).clip(CircleShape),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = if (isReply) 0.06f else 0.10f))
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) FocusBorder else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                )
                .focusable(interactionSource = interaction)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(comment.user.name, style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                comment.createdAt?.let { Text(it.toCommentTimeLabel(), style = TvTypography.caption, color = TextSecondary) }
            }
            BangumiCommentBody(comment.content)
        }
    }
}

@Composable
private fun BangumiCommentBody(content: List<BangumiCommentContent>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content.forEachIndexed { index, node ->
            when (node) {
                is BangumiCommentContent.Text -> Text(
                    text = node.value,
                    style = TvTypography.body.copy(
                        fontWeight = if (node.style.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (node.style.italic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = when {
                            node.style.underline && node.style.strikethrough -> TextDecoration.combine(
                                listOf(TextDecoration.Underline, TextDecoration.LineThrough),
                            )
                            node.style.underline || node.style.linkUrl != null -> TextDecoration.Underline
                            node.style.strikethrough -> TextDecoration.LineThrough
                            else -> TextDecoration.None
                        },
                    ),
                    color = if (node.style.linkUrl != null) AnimeRed else TextPrimary,
                )
                is BangumiCommentContent.Image -> RemoteImage(
                    url = safeBangumiCommentImageUrl(node.url),
                    contentDescription = node.description ?: "评论图片",
                    contentScale = ContentScale.Fit,
                    modifier = if (node.inline) {
                        Modifier.size(32.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp)
                            .clip(RoundedCornerShape(6.dp))
                    },
                )
                is BangumiCommentContent.Spoiler -> BangumiSpoiler(node.children, key = index)
            }
        }
    }
}

@Composable
private fun BangumiSpoiler(content: List<BangumiCommentContent>, key: Int) {
    var revealed by remember(key) { mutableStateOf(false) }
    if (revealed) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(10.dp),
        ) { BangumiCommentBody(content) }
    } else {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .border(if (focused) 2.dp else 1.dp, if (focused) FocusBorder else Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                .clickable(interactionSource = interaction, indication = null) { revealed = true }
                .focusable(interactionSource = interaction)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Visibility, contentDescription = null, tint = AnimeRed, modifier = Modifier.size(18.dp))
            Text("剧透内容 · 按确认键显示", style = TvTypography.caption, color = TextSecondary)
        }
    }
}

@Composable
private fun PanelMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(message, style = TvTypography.body, color = TextSecondary)
    }
}

@Composable
private fun PanelActionMessage(
    message: String,
    action: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    actionModifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(message, style = TvTypography.body, color = TextSecondary)
        PlayerOptionButton(
            text = action,
            selected = false,
            onClick = onClick,
            enabled = enabled,
            modifier = actionModifier,
        )
    }
}

internal fun safeBangumiCommentImageUrl(value: String): String? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    val host = uri.host?.lowercase()?.trimEnd('.')?.removeSurrounding("[", "]") ?: return null
    if (host == "localhost" || host.endsWith(".local") || (!host.contains('.') && !host.contains(':'))) {
        return null
    }
    if (host.contains(':')) {
        if (host == "::1" || host.startsWith("fe8", ignoreCase = true) ||
            host.startsWith("fe9", ignoreCase = true) || host.startsWith("fea", ignoreCase = true) ||
            host.startsWith("feb", ignoreCase = true) || host.startsWith("fc", ignoreCase = true) ||
            host.startsWith("fd", ignoreCase = true)
        ) return null
    } else {
        val octets = host.split('.').map { it.toIntOrNull() }
        if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
            val first = octets[0]!!
            val second = octets[1]!!
            if (first == 0 || first == 10 || first == 127 ||
                first == 169 && second == 254 || first == 172 && second in 16..31 ||
                first == 192 && second == 168
            ) return null
        }
    }
    return value
}

internal fun String.toCommentTimeLabel(): String =
    replace('T', ' ').substringBefore('.').removeSuffix("Z")
