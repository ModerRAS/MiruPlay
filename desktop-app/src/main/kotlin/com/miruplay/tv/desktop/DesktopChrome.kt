package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.repository.displayLabel

@Composable
internal fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = MiruPlayUiMetrics.FIELD_TEXT_SP.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = AnimeRed,
            unfocusedLabelColor = TextSecondary,
            cursorColor = AnimeRed,
            focusedBorderColor = AnimeRed,
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            focusedContainerColor = CardBg.copy(alpha = 0.44f),
            unfocusedContainerColor = CardBg.copy(alpha = 0.32f),
        ),
    )
}

@Composable
internal fun SavedSourcePicker(
    sources: List<MediaSourceInfo>,
    activeSourceId: Long?,
    onSelected: (MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sources.firstOrNull { it.id == activeSourceId }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected?.displayLabel() ?: "Saved sources")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (sources.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No saved sources") },
                    onClick = { expanded = false },
                )
            } else {
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.displayLabel()) },
                        onClick = {
                            onSelected(source)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(MiruPlayUiMetrics.SMALL_GAP_DP.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun RifeBackendPicker(
    selected: RifeBackend,
    onSelected: (RifeBackend) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .width(MiruPlayUiMetrics.BACKEND_PICKER_WIDTH_DP.dp)
                .height(MiruPlayUiMetrics.BACKEND_PICKER_HEIGHT_DP.dp),
        ) {
            Text(selected.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RifeBackend.entries.forEach { backend ->
                DropdownMenuItem(
                    text = { Text(backend.name) },
                    onClick = {
                        onSelected(backend)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun TvPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(DarkSurface)
            .border(1.dp, Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.PANEL_PADDING_DP.dp),
        content = content,
    )
}

@Composable
internal fun DesktopEmptyState(
    text: String,
    heightDp: Int = MiruPlayUiMetrics.EMPTY_STATE_HEIGHT_DP,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.48f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextSecondary, fontSize = MiruPlayUiMetrics.SECTION_BODY_SP.sp)
    }
}

@Composable
internal fun DesktopSelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = MiruPlayUiMetrics.LIST_ROW_HEIGHT_DP,
    inactiveAlpha: Float = 0.55f,
    content: @Composable (active: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val active = selected || isFocused
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) CardBg else CardBg.copy(alpha = inactiveAlpha),
            contentColor = TextPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, focusedElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) AnimeRed else Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            ),
    ) {
        content(active)
    }
}

@Composable
internal fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        secondary -> Color.Transparent
        isFocused -> AnimeRed
        else -> AnimeRed.copy(alpha = 0.82f)
    }
    val border = when {
        isFocused -> Color.White
        secondary -> Color.White.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(MiruPlayUiMetrics.ACTION_BUTTON_RADIUS_DP.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, focusedElevation = 0.dp),
        modifier = modifier
            .defaultMinSize(minWidth = MiruPlayUiMetrics.ACTION_BUTTON_MIN_WIDTH_DP.dp)
            .height(MiruPlayUiMetrics.ACTION_BUTTON_HEIGHT_DP.dp)
            .border(2.dp, border, RoundedCornerShape(MiruPlayUiMetrics.ACTION_BUTTON_RADIUS_DP.dp))
            .focusable(),
    ) {
        Text(text, fontSize = MiruPlayUiMetrics.ACTION_BUTTON_TEXT_SP.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun PosterPlaceholder() {
    Box(
        modifier = Modifier
            .width(MiruPlayUiMetrics.POSTER_WIDTH_DP.dp)
            .height(MiruPlayUiMetrics.POSTER_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(
                Brush.verticalGradient(
                    listOf(AccentBlue, CardBg, Color.Black.copy(alpha = 0.86f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(MiruPlayUiMetrics.BADGE_PADDING_DP.dp)
                .size(MiruPlayUiMetrics.BADGE_SIZE_DP.dp)
                .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                .background(AnimeRed.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("RIFE", color = Color.White, fontSize = MiruPlayUiMetrics.BADGE_TEXT_SP.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
        ) {
            Text("mpv", color = Color.White, fontSize = MiruPlayUiMetrics.HERO_TITLE_SP.sp, fontWeight = FontWeight.Bold)
            Text(
                "Windows runtime",
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun StatusBox(status: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.STATUS_BOX_PADDING_DP.dp),
    ) {
        Text(
            status,
            color = TextPrimary,
            fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
            lineHeight = 18.sp,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
