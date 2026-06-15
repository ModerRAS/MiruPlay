package com.miruplay.tv.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InitialFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `initial focus handle does not reclaim focus after user moves away`() {
        lateinit var secondaryFocusRequester: FocusRequester

        compose.mainClock.autoAdvance = false
        compose.setContent {
            val initialFocus = rememberInitialFocusHandle(
                initialDelayMillis = 0,
            )
            secondaryFocusRequester = remember { FocusRequester() }

            Row {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(INITIAL_TAG)
                        .then(initialFocus.modifier())
                        .focusable(),
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(SECONDARY_TAG)
                        .focusRequester(secondaryFocusRequester)
                        .focusable(),
                )
            }
        }

        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag(INITIAL_TAG).assertIsFocused()

        compose.runOnIdle {
            secondaryFocusRequester.requestFocus()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(SECONDARY_TAG).assertIsFocused()

        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag(SECONDARY_TAG).assertIsFocused()
    }

    @Test
    fun `initial focus handle does not retarget and steal focus after state changes`() {
        lateinit var secondaryFocusRequester: FocusRequester
        lateinit var tertiaryFocusRequester: FocusRequester
        var bindInitialFocusToPrimary by mutableStateOf(true)

        compose.mainClock.autoAdvance = false
        compose.setContent {
            val initialFocus = rememberInitialFocusHandle(
                key = bindInitialFocusToPrimary,
                initialDelayMillis = 0,
            )
            secondaryFocusRequester = remember { FocusRequester() }
            tertiaryFocusRequester = remember { FocusRequester() }

            Row {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(INITIAL_TAG)
                        .then(if (bindInitialFocusToPrimary) initialFocus.modifier() else Modifier)
                        .focusable(),
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(SECONDARY_TAG)
                        .focusRequester(secondaryFocusRequester)
                        .then(if (bindInitialFocusToPrimary) Modifier else initialFocus.modifier())
                        .focusable(),
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(TERTIARY_TAG)
                        .focusRequester(tertiaryFocusRequester)
                        .focusable(),
                )
            }
        }

        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag(INITIAL_TAG).assertIsFocused()

        compose.runOnIdle {
            tertiaryFocusRequester.requestFocus()
            bindInitialFocusToPrimary = false
        }
        compose.waitForIdle()
        compose.onNodeWithTag(TERTIARY_TAG).assertIsFocused()

        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag(TERTIARY_TAG).assertIsFocused()
    }

    private companion object {
        const val INITIAL_TAG = "initial-focus-target"
        const val SECONDARY_TAG = "secondary-focus-target"
        const val TERTIARY_TAG = "tertiary-focus-target"
    }
}
