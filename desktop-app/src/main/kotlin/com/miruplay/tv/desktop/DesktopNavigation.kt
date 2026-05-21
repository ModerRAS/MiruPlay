package com.miruplay.tv.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.design.MiruPlayUiMetrics

@Composable
internal fun DesktopTvNavigation(
    selectedSection: DesktopSection,
    onSectionSelected: (DesktopSection) -> Unit,
) {
    val sectionFocusRequesters = remember {
        MiruPlayRouteSurface.desktopSectionOrder.associateWith { FocusRequester() }
    }
    LaunchedEffect(selectedSection) {
        sectionFocusRequesters[selectedSection]?.requestFocus()
    }

    TvPanel(
        modifier = Modifier
            .width(MiruPlayUiMetrics.NAV_RAIL_WIDTH_DP.dp)
            .fillMaxHeight()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionDown -> {
                            selectedSection.stepDesktopSection(1)?.let(onSectionSelected) != null
                        }
                        Key.DirectionUp -> {
                            selectedSection.stepDesktopSection(-1)?.let(onSectionSelected) != null
                        }
                        else -> false
                    }
                }
            },
    ) {
        Text("MiruPlay", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_TITLE_SP.sp, fontWeight = FontWeight.Bold)
        Text(desktopRouteRailSubtitle(), color = TextSecondary, fontSize = MiruPlayUiMetrics.SECTION_LEAD_SP.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height((MiruPlayUiMetrics.NAV_ITEM_GAP_DP * 1.5f).dp))
        MiruPlayRouteSurface.desktopSectionOrder.forEach { section ->
            val selected = section == selectedSection
            Button(
                onClick = { onSectionSelected(section) },
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) AnimeRed else Color.Transparent,
                    contentColor = if (selected) Color.White else TextPrimary,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, focusedElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiruPlayUiMetrics.NAV_ITEM_HEIGHT_DP.dp)
                    .focusRequester(sectionFocusRequesters.getValue(section))
                    .onPreviewKeyEvent { event ->
                        desktopConfirmOrNavigationKeyEvent(
                            key = event.key,
                            type = event.type,
                            onClick = { onSectionSelected(section) },
                        )
                    }
                    .border(
                        width = 1.dp,
                        color = if (selected) AnimeRed else Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA),
                        shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
                    ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        section.menuLabel,
                        color = if (selected) Color.White else TextPrimary,
                        fontSize = MiruPlayUiMetrics.ACTION_BUTTON_TEXT_SP.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                    Text(
                        section.summary,
                        color = if (selected) Color.White.copy(alpha = 0.76f) else TextSecondary,
                        fontSize = MiruPlayUiMetrics.SECTION_SMALL_SP.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(MiruPlayUiMetrics.NAV_ITEM_GAP_DP.dp))
        }
    }
}

internal fun desktopRouteRailSubtitle(): String =
    "电视式导航"

internal fun DesktopSection.stepDesktopSection(delta: Int): DesktopSection? {
    val sections = MiruPlayRouteSurface.desktopSectionOrder
    val nextIndex = sections.indexOf(this) + delta
    return sections.getOrNull(nextIndex)
}

internal fun DesktopSection.desktopBackTarget(): DesktopSection? =
    when (id) {
        MiruPlayRouteSurface.PLAYER_ID -> MiruPlayRouteSurface.details
        MiruPlayRouteSurface.DETAILS_ID,
        MiruPlayRouteSurface.SETTINGS_ID,
        -> MiruPlayRouteSurface.library
        MiruPlayRouteSurface.LIBRARY_ID -> null
        else -> MiruPlayRouteSurface.library
    }

internal fun isDesktopBackKey(key: Key): Boolean =
    key == Key.Escape ||
        key == Key.Back ||
        key == Key.NavigatePrevious ||
        key == Key.NavigateOut

@Composable
internal fun DesktopTvHeader(selectedSection: DesktopSection) {
    Column {
        Text(selectedSection.title, color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_TITLE_SP.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            selectedSection.subtitle,
            color = TextSecondary,
            fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
        )
    }
}
