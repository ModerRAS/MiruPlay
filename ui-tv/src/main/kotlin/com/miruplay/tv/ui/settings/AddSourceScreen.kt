package com.miruplay.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.components.TvTextField
import com.miruplay.tv.ui.theme.AccentBlue
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.CardBg
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.ProgressGreen
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import com.miruplay.tv.ui.theme.WarningYellow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEFAULT_LOCAL_PATH = "/storage/emulated/0/Download"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddSourceScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val savedToken by viewModel.bangumiToken.collectAsStateWithLifecycle()
    val autoScanEnabled by viewModel.autoScanEnabled.collectAsStateWithLifecycle()
    val autoScanIntervalHours by viewModel.autoScanIntervalHours.collectAsStateWithLifecycle()
    val lastScanAt by viewModel.lastScanAt.collectAsStateWithLifecycle()

    var selectedType by remember { mutableStateOf(MediaSourceType.LOCAL) }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(DEFAULT_LOCAL_PATH) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(false) }

    val firstFieldRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFieldRequester.requestFocus()
    }

    LaunchedEffect(selectedType) {
        viewModel.clearTestResult()
        if (name.isBlank()) {
            name = when (selectedType) {
                MediaSourceType.LOCAL -> "本地下载"
                MediaSourceType.WEBDAV -> "WebDAV 媒体库"
                MediaSourceType.SMB -> "SMB 共享"
            }
        }
        if (selectedType == MediaSourceType.LOCAL && location.isBlank()) {
            location = DEFAULT_LOCAL_PATH
        }
    }

    LaunchedEffect(tokenSaved) {
        if (tokenSaved) {
            delay(1800)
            tokenSaved = false
        }
    }

    OverscanContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(onNavigateBack = onNavigateBack)

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SourceListPanel(
                    sources = sources,
                    onDelete = viewModel::removeSource,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                )

                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    SourceFormPanel(
                        selectedType = selectedType,
                        onTypeSelected = { type ->
                            if (type != selectedType) {
                                selectedType = type
                                name = sourceNameOrDefault("", type)
                                location = defaultLocationFor(type)
                                username = ""
                                password = ""
                                viewModel.clearTestResult()
                            }
                        },
                        name = name,
                        onNameChange = { name = it },
                        location = location,
                        onLocationChange = { location = it },
                        username = username,
                        onUsernameChange = { username = it },
                        password = password,
                        onPasswordChange = { password = it },
                        testResult = testResult,
                        firstFieldRequester = firstFieldRequester,
                        onTestConnection = {
                            viewModel.testConnection(selectedType, location, username, password)
                        },
                        onSave = {
                            viewModel.addSource(
                                MediaSourceInfo(
                                    name = sourceNameOrDefault(name, selectedType),
                                    type = selectedType,
                                    connectionInfo = sourceConnectionInfo(
                                        type = selectedType,
                                        location = location,
                                        username = username,
                                        password = password
                                    )
                                )
                            )
                            name = ""
                            location = if (selectedType == MediaSourceType.LOCAL) DEFAULT_LOCAL_PATH else ""
                            username = ""
                            password = ""
                            viewModel.clearTestResult()
                        }
                    )

                    ScanPanel(
                        autoScanEnabled = autoScanEnabled,
                        autoScanIntervalHours = autoScanIntervalHours,
                        lastScanAt = lastScanAt,
                        onToggleAutoScan = { viewModel.setAutoScanEnabled(!autoScanEnabled) },
                        onIntervalSelected = viewModel::setAutoScanIntervalHours
                    )

                    MetadataPanel(
                        savedToken = savedToken,
                        tokenInput = tokenInput,
                        tokenSaved = tokenSaved,
                        onTokenChange = { tokenInput = it },
                        onSaveToken = {
                            val token = tokenInput.trim()
                            if (token.isNotBlank()) {
                                viewModel.saveBangumiToken(token)
                                tokenInput = ""
                                tokenSaved = true
                            }
                        },
                        onClearToken = {
                            viewModel.clearBangumiToken()
                            tokenInput = ""
                            tokenSaved = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "设置",
                style = TvTypography.title,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "管理媒体源和元数据服务",
                style = TvTypography.body,
                color = TextSecondary
            )
        }
        TvButton(text = "返回", onClick = onNavigateBack)
    }
}

@Composable
private fun SourceListPanel(
    sources: List<MediaSourceInfo>,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsPanel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "媒体源", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (sources.isEmpty()) {
                "还没有配置媒体源"
            } else {
                "已配置 ${sources.size} 个源"
            },
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(18.dp))

        if (sources.isEmpty()) {
            EmptySourceHint()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceListItem(source = source, onDelete = { onDelete(source.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptySourceHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "先添加一个本地或网络媒体库",
                style = TvTypography.body,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "MuMu 共享文件夹通常可用默认 Download 路径。",
                style = TvTypography.caption,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SourceListItem(
    source: MediaSourceInfo,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val location = source.connectionInfo["url"] ?: source.connectionInfo["path"] ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) AccentBlue else DarkSurface)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = source.type.sourceIcon(),
            contentDescription = null,
            tint = if (source.isConnected) ProgressGreen else TextSecondary,
            modifier = Modifier.size(30.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name.ifBlank { source.type.label() },
                color = TextPrimary,
                style = TvTypography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${source.type.label()} · ${if (source.isConnected) "可连接" else "待验证"}",
                color = if (source.isConnected) ProgressGreen else WarningYellow,
                style = TvTypography.caption
            )
            if (location.isNotBlank()) {
                Text(
                    text = location,
                    color = TextSecondary,
                    style = TvTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TvButton(
            text = "删除",
            icon = Icons.Filled.Delete,
            onClick = onDelete,
            modifier = Modifier.width(128.dp)
        )
    }
}

@Composable
private fun SourceFormPanel(
    selectedType: MediaSourceType,
    onTypeSelected: (MediaSourceType) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    testResult: ConnectionTestResult?,
    firstFieldRequester: FocusRequester,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    SettingsPanel {
        Text(text = "添加媒体源", style = TvTypography.subtitle, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "选择媒体库所在位置，保存后可在首页手动扫描。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MediaSourceType.entries.forEach { type ->
                SourceTypeChip(
                    type = type,
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        TvTextField(
            value = name,
            onValueChange = onNameChange,
            label = "显示名称",
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(firstFieldRequester)
        )

        Spacer(Modifier.height(12.dp))

        TvTextField(
            value = location,
            onValueChange = onLocationChange,
            label = selectedType.locationLabel(),
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedType != MediaSourceType.LOCAL) {
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "用户名（可选）",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "密码（可选）",
                isPassword = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = if (testResult is ConnectionTestResult.Testing) "测试中" else "测试连接",
                icon = Icons.Filled.WifiTethering,
                enabled = testResult !is ConnectionTestResult.Testing,
                onClick = onTestConnection
            )
            TvButton(
                text = "保存源",
                icon = Icons.Filled.Save,
                enabled = location.isNotBlank(),
                onClick = onSave
            )
        }

        ConnectionStatus(result = testResult)
    }
}

@Composable
private fun SourceTypeChip(
    type: MediaSourceType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.12f)
    }

    Row(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AnimeRed.copy(alpha = 0.18f) else DarkSurface)
            .border(if (selected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = type.sourceIcon(),
            contentDescription = null,
            tint = if (selected) AnimeRed else TextSecondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = type.label(),
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = type.hint(),
                style = TvTypography.caption,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ConnectionStatus(result: ConnectionTestResult?) {
    when (result) {
        is ConnectionTestResult.Success -> StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = "连接正常，可以保存并返回首页扫描。",
            color = ProgressGreen
        )
        is ConnectionTestResult.Failed -> StatusMessage(
            icon = Icons.Filled.Refresh,
            text = result.message,
            color = WarningYellow
        )
        is ConnectionTestResult.Testing -> StatusMessage(
            icon = Icons.Filled.WifiTethering,
            text = "正在验证连接...",
            color = TextSecondary
        )
        null -> Unit
    }
}

@Composable
private fun ScanPanel(
    autoScanEnabled: Boolean,
    autoScanIntervalHours: Int,
    lastScanAt: Long,
    onToggleAutoScan: () -> Unit,
    onIntervalSelected: (Int) -> Unit
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "媒体库扫描", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "首页的扫描按钮会立即执行；定时扫描只会在到达间隔后回到首页时触发。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = if (autoScanEnabled) "定时已开" else "定时关闭",
                icon = Icons.Filled.Refresh,
                selected = autoScanEnabled,
                enabled = true,
                onClick = onToggleAutoScan,
                modifier = Modifier.width(150.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanPreferencesManager.INTERVAL_OPTIONS_HOURS.forEach { hours ->
                ScanOptionChip(
                    text = "${hours}小时",
                    selected = autoScanEnabled && hours == autoScanIntervalHours,
                    enabled = autoScanEnabled,
                    onClick = { onIntervalSelected(hours) },
                    modifier = Modifier.width(112.dp)
                )
            }
        }

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = "当前间隔 ${autoScanIntervalHours} 小时 · ${formatLastScanAt(lastScanAt)}",
            color = if (autoScanEnabled) ProgressGreen else TextSecondary
        )
    }
}

@Composable
private fun ScanOptionChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.18f)
    }
    val background = when {
        !enabled -> DarkSurface
        selected -> AnimeRed.copy(alpha = 0.28f)
        isFocused -> AccentBlue
        else -> DarkSurface
    }
    val contentColor = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.55f)

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (selected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun MetadataPanel(
    savedToken: String,
    tokenInput: String,
    tokenSaved: Boolean,
    onTokenChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "元数据", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Bangumi Token 是可选项，只用于更完整的在线元数据。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = tokenInput,
            onValueChange = onTokenChange,
            label = "Bangumi Access Token",
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = "保存",
                icon = Icons.Filled.Save,
                enabled = tokenInput.isNotBlank(),
                onClick = onSaveToken
            )
            TvButton(
                text = "清除",
                icon = Icons.Filled.Delete,
                enabled = savedToken.isNotBlank(),
                onClick = onClearToken
            )
        }

        val hasToken = savedToken.isNotBlank() || tokenSaved
        StatusMessage(
            icon = if (hasToken) Icons.Filled.CheckCircle else Icons.Filled.Key,
            text = if (hasToken) "Token 已保存在加密存储中。" else "当前未设置 Token。",
            color = if (hasToken) ProgressGreen else TextSecondary
        )
    }
}

@Composable
private fun StatusMessage(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = TvTypography.body, color = TextPrimary)
    }
}

@Composable
private fun SettingsPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(22.dp),
        content = content
    )
}

private fun sourceNameOrDefault(name: String, type: MediaSourceType): String =
    name.ifBlank {
        when (type) {
            MediaSourceType.LOCAL -> "本地下载"
            MediaSourceType.WEBDAV -> "WebDAV 媒体库"
            MediaSourceType.SMB -> "SMB 共享"
        }
    }

private fun defaultLocationFor(type: MediaSourceType): String = when (type) {
    MediaSourceType.LOCAL -> DEFAULT_LOCAL_PATH
    MediaSourceType.WEBDAV -> ""
    MediaSourceType.SMB -> "smb://"
}

private fun sourceConnectionInfo(
    type: MediaSourceType,
    location: String,
    username: String,
    password: String
): Map<String, String> = buildMap {
    put("url", location.trim())
    if (type == MediaSourceType.LOCAL) {
        put("path", location.trim())
    }
    if (username.isNotBlank()) put("username", username.trim())
    if (password.isNotBlank()) put("password", password)
}

private fun formatLastScanAt(lastScanAt: Long): String {
    if (lastScanAt <= 0L) return "还没有扫描记录"
    return "上次扫描 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(lastScanAt))
}

private fun MediaSourceType.label(): String = when (this) {
    MediaSourceType.LOCAL -> "本地"
    MediaSourceType.WEBDAV -> "WebDAV"
    MediaSourceType.SMB -> "SMB"
}

private fun MediaSourceType.hint(): String = when (this) {
    MediaSourceType.LOCAL -> "设备文件夹"
    MediaSourceType.WEBDAV -> "HTTP 文件服务"
    MediaSourceType.SMB -> "局域网共享"
}

private fun MediaSourceType.locationLabel(): String = when (this) {
    MediaSourceType.LOCAL -> "文件夹路径"
    MediaSourceType.WEBDAV -> "WebDAV 地址"
    MediaSourceType.SMB -> "SMB 地址"
}

private fun MediaSourceType.sourceIcon(): ImageVector = when (this) {
    MediaSourceType.LOCAL -> Icons.Filled.Folder
    MediaSourceType.WEBDAV -> Icons.Filled.Cloud
    MediaSourceType.SMB -> Icons.Filled.Dns
}
