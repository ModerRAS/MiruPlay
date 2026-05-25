package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.window.WindowPlacement
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.model.desktopRouteRailSubtitleLabel
import com.miruplay.tv.model.desktopWindowTitleLabel
import com.miruplay.tv.model.libraryScanActionLabel
import com.miruplay.tv.model.librarySettingsActionLabel
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSectionContractTest {
    @Test
    fun `desktop rail follows shared TV route surface`() {
        assertEquals(
            listOf(
                MiruPlayRouteSurface.LIBRARY_ID,
                MiruPlayRouteSurface.DETAILS_ID,
                MiruPlayRouteSurface.PLAYER_ID,
                MiruPlayRouteSurface.SETTINGS_ID,
            ),
            MiruPlayRouteSurface.desktopSectionOrder.map { it.id },
        )
        assertEquals(
            listOf("探索", "详情", "播放", "设置"),
            MiruPlayRouteSurface.desktopSectionOrder.map { it.menuLabel },
        )
        assertEquals(
            listOf("探索", "详情", "播放", "设置"),
            MiruPlayRouteSurface.desktopSectionOrder.map { it.title },
        )
    }

    @Test
    fun `desktop route rail chrome uses TV facing copy`() {
        assertEquals("电视式导航", desktopRouteRailSubtitleLabel())
    }

    @Test
    fun `desktop library header keeps Android TV scan and settings actions only`() {
        assertEquals(
            listOf(libraryScanActionLabel(), librarySettingsActionLabel()),
            desktopLibraryHeaderActions().map { it.label },
        )
    }

    @Test
    fun `desktop library header focus moves like a TV action row`() {
        assertEquals(
            DesktopLibraryHeaderFocusTarget.Action(DesktopLibraryHeaderAction.Settings),
            desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Scan, Key.DirectionRight),
        )
        assertEquals(
            DesktopLibraryHeaderFocusTarget.Action(DesktopLibraryHeaderAction.Scan),
            desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Settings, Key.DirectionLeft),
        )
        assertEquals(
            DesktopLibraryHeaderFocusTarget.NextPanel,
            desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Scan, Key.DirectionDown),
        )
        assertNull(desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Scan, Key.DirectionLeft))
        assertNull(desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Settings, Key.DirectionRight))
        assertNull(desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Scan, Key.DirectionUp))
    }

    @Test
    fun `desktop library header focus also uses shared direction intents`() {
        assertEquals(
            DesktopLibraryHeaderFocusTarget.Action(DesktopLibraryHeaderAction.Settings),
            desktopLibraryHeaderFocusTarget(
                DesktopLibraryHeaderAction.Scan,
                MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            DesktopLibraryHeaderFocusTarget.Action(DesktopLibraryHeaderAction.Scan),
            desktopLibraryHeaderFocusTarget(
                DesktopLibraryHeaderAction.Settings,
                MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            DesktopLibraryHeaderFocusTarget.NextPanel,
            desktopLibraryHeaderFocusTarget(
                DesktopLibraryHeaderAction.Scan,
                MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertNull(
            desktopLibraryHeaderFocusTarget(
                DesktopLibraryHeaderAction.Scan,
                MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertNull(
            desktopLibraryHeaderFocusTarget(
                DesktopLibraryHeaderAction.Scan,
                MiruPlayInputIntent.Activate,
            ),
        )
    }

    @Test
    fun `desktop escape back follows Android TV route hierarchy`() {
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.player.desktopBackTarget())
        assertEquals(MiruPlayRouteSurface.library, MiruPlayRouteSurface.details.desktopBackTarget())
        assertEquals(MiruPlayRouteSurface.library, MiruPlayRouteSurface.settings.desktopBackTarget())
        assertNull(MiruPlayRouteSurface.library.desktopBackTarget())
    }

    @Test
    fun `desktop back keys accept TV and navigation remotes without swallowing text editing`() {
        assertEquals(MiruPlayInputIntent.Back, Key.Escape.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Back, Key.Back.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.NavigatePrevious, Key.NavigatePrevious.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.NavigateOut, Key.NavigateOut.toMiruPlayInputIntent())
        assertTrue(isDesktopBackKey(Key.Escape))
        assertTrue(isDesktopBackKey(Key.Back))
        assertTrue(isDesktopBackKey(Key.NavigatePrevious))
        assertTrue(isDesktopBackKey(Key.NavigateOut))
        assertFalse(isDesktopBackKey(Key.Backspace))
        assertFalse(isDesktopBackKey(Key.DirectionLeft))
    }

    @Test
    fun `desktop automation start section falls back to library`() {
        assertEquals(MiruPlayRouteSurface.library, desktopInitialSection(null))
        assertEquals(MiruPlayRouteSurface.library, desktopInitialSection(""))
        assertEquals(MiruPlayRouteSurface.library, desktopInitialSection("missing"))
        assertEquals(MiruPlayRouteSurface.player, desktopInitialSection("player"))
        assertEquals(MiruPlayRouteSurface.settings, desktopInitialSection(" SETTINGS "))
    }

    @Test
    fun `desktop entry smoke is opt in by launcher argument`() {
        assertTrue(shouldRunDesktopEntrySmoke(arrayOf(DESKTOP_ENTRY_SMOKE_ARG)))
        assertTrue(shouldRunDesktopEntrySmoke(arrayOf("ignored", DESKTOP_ENTRY_SMOKE_ARG)))
        assertFalse(shouldRunDesktopEntrySmoke(emptyArray()))
        assertFalse(shouldRunDesktopEntrySmoke(arrayOf("$DESKTOP_ENTRY_SMOKE_ARG=false")))
    }

    @Test
    fun `desktop entry smoke report path is parsed from launcher argument`() {
        assertEquals(
            Paths.get("D:/MiruPlay/build/native-entry-smoke.json"),
            desktopEntrySmokeReportPath(
                arrayOf("${DESKTOP_ENTRY_SMOKE_REPORT_ARG_PREFIX}D:/MiruPlay/build/native-entry-smoke.json"),
            ),
        )
        assertNull(desktopEntrySmokeReportPath(emptyArray()))
        assertNull(desktopEntrySmokeReportPath(arrayOf(DESKTOP_ENTRY_SMOKE_REPORT_ARG_PREFIX)))
    }

    @Test
    fun `desktop entry smoke report serializes launcher contract`() {
        val windowTitle = desktopWindowTitleLabel()
        val report = DesktopEntrySmokeReport(
            status = "ok",
            entryPoint = "com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt",
            windowTitle = windowTitle,
            initialSection = "library",
            runtimeRoot = "D:\\MiruPlay\\runtime\\mpv",
            mpvExecutable = "D:\\MiruPlay\\runtime\\mpv\\mpv.exe",
            configDirectory = "D:\\MiruPlay\\runtime\\mpv\\portable_config",
        ).toJson()

        assertTrue(report.contains("\"status\": \"ok\""))
        assertTrue(report.contains("\"entryPoint\": \"com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt\""))
        assertTrue(report.contains("\"windowTitle\": \"$windowTitle\""))
        assertTrue(report.contains("\"initialSection\": \"library\""))
        assertTrue(report.contains("\"runtimeRoot\": \"D:\\\\MiruPlay\\\\runtime\\\\mpv\""))
        assertTrue(report.contains("\"mpvExecutable\": \"D:\\\\MiruPlay\\\\runtime\\\\mpv\\\\mpv.exe\""))
        assertTrue(report.contains("\"configDirectory\": \"D:\\\\MiruPlay\\\\runtime\\\\mpv\\\\portable_config\""))
    }

    @Test
    fun `desktop player fullscreen only applies while the Player route is active`() {
        assertFalse(shouldUseDesktopPlayerFullscreen(MiruPlayRouteSurface.library, fullscreen = true))
        assertFalse(shouldUseDesktopPlayerFullscreen(MiruPlayRouteSurface.details, fullscreen = true))
        assertFalse(shouldUseDesktopPlayerFullscreen(MiruPlayRouteSurface.settings, fullscreen = true))
        assertFalse(shouldUseDesktopPlayerFullscreen(MiruPlayRouteSurface.player, fullscreen = false))
        assertTrue(shouldUseDesktopPlayerFullscreen(MiruPlayRouteSurface.player, fullscreen = true))
    }

    @Test
    fun `desktop player fullscreen restores prior window placement`() {
        val fromFloating = desktopPlayerFullscreenPlacement(
            currentPlacement = WindowPlacement.Floating,
            restorePlacement = null,
            active = true,
        )
        assertEquals(WindowPlacement.Fullscreen, fromFloating.placement)
        assertEquals(WindowPlacement.Floating, fromFloating.restorePlacement)

        val whileFullscreen = desktopPlayerFullscreenPlacement(
            currentPlacement = WindowPlacement.Fullscreen,
            restorePlacement = WindowPlacement.Maximized,
            active = true,
        )
        assertEquals(WindowPlacement.Fullscreen, whileFullscreen.placement)
        assertEquals(WindowPlacement.Maximized, whileFullscreen.restorePlacement)

        val restored = desktopPlayerFullscreenPlacement(
            currentPlacement = WindowPlacement.Fullscreen,
            restorePlacement = WindowPlacement.Maximized,
            active = false,
        )
        assertEquals(WindowPlacement.Maximized, restored.placement)
        assertNull(restored.restorePlacement)
    }

    @Test
    fun `desktop route rail navigation stops at TV list edges`() {
        assertNull(MiruPlayRouteSurface.library.stepDesktopSection(-1))
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.library.stepDesktopSection(1))
        assertEquals(MiruPlayRouteSurface.player, MiruPlayRouteSurface.details.stepDesktopSection(1))
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.player.stepDesktopSection(-1))
        assertEquals(MiruPlayRouteSurface.settings, MiruPlayRouteSurface.player.stepDesktopSection(1))
        assertNull(MiruPlayRouteSurface.settings.stepDesktopSection(1))
        assertEquals(
            MiruPlayRouteSurface.desktopSectionStep(MiruPlayRouteSurface.player, -1),
            MiruPlayRouteSurface.player.stepDesktopSection(-1),
        )
    }

    @Test
    fun `desktop route rail navigation uses shared direction intents`() {
        assertEquals(
            MiruPlayRouteSurface.details,
            MiruPlayRouteSurface.library.stepDesktopSection(MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            MiruPlayRouteSurface.details,
            MiruPlayRouteSurface.player.stepDesktopSection(MiruPlayInputIntent.DirectionUp),
        )
        assertNull(MiruPlayRouteSurface.library.stepDesktopSection(MiruPlayInputIntent.DirectionUp))
        assertNull(MiruPlayRouteSurface.library.stepDesktopSection(MiruPlayInputIntent.DirectionRight))
        assertNull(MiruPlayRouteSurface.library.stepDesktopSection(MiruPlayInputIntent.Activate))
    }
}
