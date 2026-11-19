package com.miruplay.tv.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMenuFocusActionTest {
    @Test
    fun `focus moving inside the menu only highlights and never switches the section`() {
        assertEquals(
            SettingsMenuFocusAction.NONE,
            settingsMenuFocusAction(isFocused = true, enteredFromContent = false, selected = false),
        )
    }

    @Test
    fun `focus entering the menu from the content pane restores the selected item`() {
        // 从右侧内容区误入菜单（例如点完按钮后焦点掉进菜单）：不切页，把焦点拉回当前选中项
        assertEquals(
            SettingsMenuFocusAction.RESTORE_SELECTION,
            settingsMenuFocusAction(isFocused = true, enteredFromContent = true, selected = false),
        )
    }

    @Test
    fun `focus entering the menu on the selected item does nothing`() {
        assertEquals(
            SettingsMenuFocusAction.NONE,
            settingsMenuFocusAction(isFocused = true, enteredFromContent = true, selected = true),
        )
    }

    @Test
    fun `losing focus never does anything`() {
        assertEquals(
            SettingsMenuFocusAction.NONE,
            settingsMenuFocusAction(isFocused = false, enteredFromContent = true, selected = false),
        )
    }
}
