package com.miruplay.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView

@Stable
class InitialFocusHandle internal constructor(
    private val focusRequester: FocusRequester,
    private val markPlaced: () -> Unit,
    private val updateFocused: (Boolean) -> Unit,
) {
    fun modifier(): Modifier =
        Modifier
            .focusRequester(focusRequester)
            .onGloballyPositioned { markPlaced() }
            .onFocusChanged { state ->
                updateFocused(state.isFocused || state.hasFocus)
            }
}

@Composable
fun rememberInitialFocusHandle(
    key: Any? = Unit,
    enabled: Boolean = true,
    initialDelayMillis: Long = 120,
): InitialFocusHandle {
    val composeView = LocalView.current
    val focusRequester = remember(key) { FocusRequester() }
    var targetPlaced by remember(key) { mutableStateOf(false) }
    var targetFocused by remember(key) { mutableStateOf(false) }
    var initialFocusConsumed by remember { mutableStateOf(false) }

    // TV UIs should never keep pulling focus back after the user or the system
    // has already moved it elsewhere. This helper is only a first-frame fallback
    // for the initial target when the screen appears with no focused child.
    androidx.compose.runtime.LaunchedEffect(key, enabled, targetPlaced, targetFocused, initialFocusConsumed) {
        if (!enabled || !targetPlaced || targetFocused || initialFocusConsumed) return@LaunchedEffect

        kotlinx.coroutines.delay(initialDelayMillis)

        if (targetFocused || initialFocusConsumed || !composeView.hasWindowFocus()) return@LaunchedEffect

        initialFocusConsumed = true
        composeView.post {
            if (!targetFocused) {
                composeView.requestFocus()
                focusRequester.requestFocus()
            }
        }
    }

    return remember(key) {
        InitialFocusHandle(
            focusRequester = focusRequester,
            markPlaced = { targetPlaced = true },
            updateFocused = { focused -> targetFocused = focused },
        )
    }
}
