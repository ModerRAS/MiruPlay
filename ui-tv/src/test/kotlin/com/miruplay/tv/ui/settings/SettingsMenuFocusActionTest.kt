package com.miruplay.tv.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMenuFocusActionTest {
    @Test
    fun `focus moving inside the menu switches the section`() {
        assertEquals(
            SettingsMenuFocusAction.SWITCH_SECTION,
            settingsMenuFocusAction(isFocused = true, switchOnFocus = true, selected = false),
        )
    }

    @Test
    fun `focus entering the menu from the content pane never switches the section`() {
        // 从右侧内容区按左/返回误入菜单：焦点落在别的分类项上，只能把焦点拉回当前选中项
        assertEquals(
            SettingsMenuFocusAction.RESTORE_SELECTION,
            settingsMenuFocusAction(isFocused = true, switchOnFocus = false, selected = false),
        )
    }

    @Test
    fun `focus entering the menu on the selected item does nothing`() {
        assertEquals(
            SettingsMenuFocusAction.NONE,
            settingsMenuFocusAction(isFocused = true, switchOnFocus = false, selected = true),
        )
    }

    @Test
    fun `losing focus never switches the section`() {
        assertEquals(
            SettingsMenuFocusAction.NONE,
            settingsMenuFocusAction(isFocused = false, switchOnFocus = true, selected = false),
        )
    }
}
