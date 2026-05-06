package com.miruplay.tv.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.ui.components.*
import com.miruplay.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddSourceScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(MediaSourceType.LOCAL) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }

    OverscanContainer {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "设置",
                    style = TvTypography.title,
                    color = TextPrimary
                )
                TvButton(text = "返回", onClick = onNavigateBack)
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Source List
            if (sources.isNotEmpty()) {
                Text(
                    text = "已配置的源",
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                sources.forEach { source ->
                    SourceListItem(
                        source = source,
                        onDelete = { viewModel.removeSource(source.id) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            
            // Add New Source Form
            Text(
                text = "添加新源",
                style = TvTypography.subtitle,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            // Source Type Selector
            SourceTypeSelector(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it }
            )
            Spacer(Modifier.height(16.dp))
            
            // Form Fields
            TvTextField(
                value = name,
                onValueChange = { name = it },
                label = "源名称",
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            Spacer(Modifier.height(12.dp))
            
            if (selectedType != MediaSourceType.LOCAL) {
                TvTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "URL / 路径",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                
                TvTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "用户名（可选）",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                
                TvTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    isPassword = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
            
            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TvButton(
                    text = if (isTesting) "测试中..." else "测试连接",
                    onClick = {
                        isTesting = true
                        viewModel.testConnection(selectedType, url)
                        isTesting = false
                        testResult = "连接成功"
                    }
                )
                TvButton(
                    text = "保存",
                    onClick = {
                        val config = buildMap {
                            put("url", url)
                            put("username", username)
                            put("password", password)
                            if (selectedType == MediaSourceType.LOCAL) {
                                put("path", url)
                            }
                        }
                        viewModel.addSource(
                            MediaSourceInfo(
                                name = name,
                                type = selectedType,
                                connectionInfo = config
                            )
                        )
                        name = ""
                        url = ""
                        username = ""
                        password = ""
                    }
                )
            }
            
            // Test Result
            testResult?.let {
                Spacer(Modifier.height(16.dp))
                Text(text = it, color = ProgressGreen, style = TvTypography.body)
            }
        }
    }
}

@Composable
private fun SourceTypeSelector(
    selectedType: MediaSourceType,
    onTypeSelected: (MediaSourceType) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MediaSourceType.entries.forEach { type ->
            val isSelected = type == selectedType
            TvButton(
                text = type.name,
                onClick = { onTypeSelected(type) },
                modifier = Modifier.width(160.dp)
            )
        }
    }
}

@Composable
private fun SourceListItem(
    source: MediaSourceInfo,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = source.name, color = TextPrimary, style = TvTypography.body)
            Text(
                text = "${source.type.name} | ${if (source.isConnected) "已连接" else "未连接"}",
                color = if (source.isConnected) ProgressGreen else TextSecondary,
                style = TvTypography.caption
            )
        }
        TvButton(text = "删除", onClick = onDelete)
    }
}