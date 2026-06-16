package com.miruplay.tv.data.preferences

import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.repository.AppMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppModePreferencesManagerTest {
    private lateinit var context: android.content.Context
    private lateinit var manager: AppModePreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("miruplay_app_mode_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        manager = AppModePreferencesManager(context)
    }

    @Test
    fun `getSelectionState auto migrates missing onboarding state to anime`() = runBlocking {
        val state = manager.getSelectionState()

        assertEquals(AppMode.ANIME, state.currentAppMode)
        assertTrue(state.hasCompletedModeSelection)
        val prefs = context.getSharedPreferences("miruplay_app_mode_prefs", android.content.Context.MODE_PRIVATE)
        assertEquals(AppMode.ANIME.storageValue, prefs.getString("current_app_mode", null))
        assertTrue(prefs.getBoolean("has_completed_mode_selection", false))
    }

    @Test
    fun `getSelectionState preserves stored mode while upgrading incomplete onboarding state`() = runBlocking {
        context.getSharedPreferences("miruplay_app_mode_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("current_app_mode", AppMode.DRAMA.storageValue)
            .putBoolean("has_completed_mode_selection", false)
            .commit()

        val state = manager.getSelectionState()

        assertEquals(AppMode.DRAMA, state.currentAppMode)
        assertTrue(state.hasCompletedModeSelection)
    }

    @Test
    fun `completeModeSelection persists current mode and completion flag`() = runBlocking {
        manager.completeModeSelection(AppMode.DRAMA)

        val state = manager.getSelectionState()

        assertEquals(AppMode.DRAMA, state.currentAppMode)
        assertTrue(state.hasCompletedModeSelection)
    }

    @Test
    fun `setCurrentAppMode updates startup mode for next launch`() = runBlocking {
        manager.completeModeSelection(AppMode.ANIME)

        manager.setCurrentAppMode(AppMode.DRAMA)
        val state = manager.getSelectionState()

        assertEquals(AppMode.DRAMA, state.currentAppMode)
        assertTrue(state.hasCompletedModeSelection)
    }
}
