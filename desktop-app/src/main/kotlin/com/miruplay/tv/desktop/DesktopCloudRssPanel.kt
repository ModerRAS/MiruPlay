package com.miruplay.tv.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.RssSubscriptionInfo

@Composable
internal fun CloudRssPanel(
    endpointUrl: String,
    onEndpointUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    inboxPath: String,
    onInboxPathChange: (String) -> Unit,
    libraryPath: String,
    onLibraryPathChange: (String) -> Unit,
    intervalMinutes: String,
    onIntervalMinutesChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    proxyEnabled: Boolean,
    onProxyEnabledChange: (Boolean) -> Unit,
    proxyHost: String,
    onProxyHostChange: (String) -> Unit,
    proxyPort: String,
    onProxyPortChange: (String) -> Unit,
    rssName: String,
    onRssNameChange: (String) -> Unit,
    rssUrl: String,
    onRssUrlChange: (String) -> Unit,
    rssFilter: String,
    onRssFilterChange: (String) -> Unit,
    rssEnabled: Boolean,
    onRssEnabledChange: (Boolean) -> Unit,
    subscriptions: List<RssSubscriptionInfo>,
    selectedSubscription: RssSubscriptionInfo?,
    status: String,
    schedulerStatus: String,
    linkedSourceLabel: String,
    onSaveConfig: () -> Unit,
    onSaveCredentials: () -> Unit,
    onLoginCloudDrive: () -> Unit,
    onVerifyApiToken: () -> Unit,
    onClearCredentials: () -> Unit,
    onRunSync: () -> Unit,
    onStartScheduler: () -> Unit,
    onStopScheduler: () -> Unit,
    onUseActiveScanSource: () -> Unit,
    onClearScanSource: () -> Unit,
    onSaveSubscription: () -> Unit,
    onSubscriptionSelected: (RssSubscriptionInfo) -> Unit,
    onDeleteSubscription: () -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.46f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text("Cloud/RSS sync", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField("CloudDrive2 endpoint", endpointUrl, onValueChange = onEndpointUrlChange)
                LabeledTextField("CloudDrive2 username", username, onValueChange = onUsernameChange)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField("API token", token, onValueChange = onTokenChange, modifier = Modifier.weight(1f))
                    LabeledTextField("Password", password, onValueChange = onPasswordChange, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField("Inbox path", inboxPath, onValueChange = onInboxPathChange, modifier = Modifier.weight(1f))
                    LabeledTextField("Library path", libraryPath, onValueChange = onLibraryPathChange, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField("Interval minutes", intervalMinutes, onValueChange = onIntervalMinutesChange, modifier = Modifier.weight(1f))
                    LabeledTextField("Proxy host", proxyHost, onValueChange = onProxyHostChange, modifier = Modifier.weight(1f))
                    LabeledTextField("Proxy port", proxyPort, onValueChange = onProxyPortChange, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
                    ToggleRow("Enabled", enabled, onEnabledChange)
                    ToggleRow("RSS proxy", proxyEnabled, onProxyEnabledChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    TvActionButton("Use active source", onClick = onUseActiveScanSource, secondary = true)
                    TvActionButton("Clear source", onClick = onClearScanSource, secondary = true)
                }
                Text("Post-sync source: $linkedSourceLabel", color = TextSecondary, fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp)
                Column(verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SMALL_GAP_DP.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton("Save sync config", onClick = onSaveConfig)
                        TvActionButton("Run sync now", onClick = onRunSync, secondary = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton("Save credentials", onClick = onSaveCredentials, secondary = true)
                        TvActionButton("Clear credentials", onClick = onClearCredentials, secondary = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton("Login", onClick = onLoginCloudDrive, secondary = true)
                        TvActionButton("Verify token", onClick = onVerifyApiToken, secondary = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton("Start scheduler", onClick = onStartScheduler, secondary = true)
                        TvActionButton("Stop scheduler", onClick = onStopScheduler, secondary = true)
                    }
                }
                StatusBox(status)
                Text(
                    schedulerStatus,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                    lineHeight = 18.sp,
                )
            }
            Column(
                modifier = Modifier.weight(0.54f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                Text("RSS subscriptions", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField("Subscription name", rssName, onValueChange = onRssNameChange)
                LabeledTextField("Subscription URL", rssUrl, onValueChange = onRssUrlChange)
                LabeledTextField("Filter regex", rssFilter, onValueChange = onRssFilterChange)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
                    ToggleRow("Enabled", rssEnabled, onRssEnabledChange)
                    TvActionButton("Save subscription", onClick = onSaveSubscription, secondary = true)
                    TvActionButton("Delete", onClick = onDeleteSubscription, secondary = true)
                }
                if (subscriptions.isEmpty()) {
                    DesktopEmptyState(
                        text = "Save a subscription to show it here.",
                        heightDp = MiruPlayUiMetrics.RSS_EMPTY_STATE_HEIGHT_DP,
                    )
                } else {
                    subscriptions.forEach { subscription ->
                        RssSubscriptionRow(
                            subscription = subscription,
                            selected = selectedSubscription?.id == subscription.id,
                            onClick = { onSubscriptionSelected(subscription) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RssSubscriptionRow(
    subscription: RssSubscriptionInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                subscription.name,
                color = TextPrimary,
                fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subscription.url,
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
