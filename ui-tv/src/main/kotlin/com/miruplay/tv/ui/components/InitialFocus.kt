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
import kotlinx.coroutines.delay

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
    retryDelayMillis: Long = 180,
    maxAttempts: Int = 12,
): InitialFocusHandle {
    val composeView = LocalView.current
    val focusRequester = remember(key) { FocusRequester() }
    var targetPlaced by remember(key) { mutableStateOf(false) }
    var targetFocused by remember(key) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(key, enabled, targetPlaced, targetFocused) {
        if (!enabled || !targetPlaced || targetFocused) return@LaunchedEffect

        repeat(maxAttempts) { attempt ->
            if (targetFocused) return@LaunchedEffect

            delay(if (attempt == 0) initialDelayMillis else retryDelayMillis)

            if (targetFocused || !composeView.hasWindowFocus()) return@repeat

            composeView.post {
                if (!targetFocused) {
                    composeView.requestFocus()
                    focusRequester.requestFocus()
                }
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
