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
@Config(sdk = [34])
class AppModePreferencesManagerTest {
    private lateinit var manager: AppModePreferencesManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("miruplay_app_mode_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        manager = AppModePreferencesManager(context)
    }

    @Test
    fun `getSelectionState returns unselected defaults before onboarding`() = runBlocking {
        val state = manager.getSelectionState()

        assertEquals(null, state.currentAppMode)
        assertFalse(state.hasCompletedModeSelection)
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
