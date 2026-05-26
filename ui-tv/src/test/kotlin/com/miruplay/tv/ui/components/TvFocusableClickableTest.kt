package com.miruplay.tv.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class TvFocusableClickableTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `focusable clickable triggers from pointer and TV activation keys`() {
        var activations = 0

        setClickableContent(
            onClick = { activations += 1 },
        )

        compose.onNodeWithTag(CLICKABLE_TAG).performClick()
        compose.runOnIdle {
            assertEquals(1, activations)
        }

        requestCardFocus()
        compose.onNodeWithTag(CLICKABLE_TAG).performKeyInput {
            pressKey(Key.DirectionCenter)
            pressKey(Key.Enter)
            pressKey(Key.NumPadEnter)
        }

        compose.runOnIdle {
            assertEquals(4, activations)
        }
    }

    @Test
    fun `disabled focusable clickable ignores click and TV activation keys`() {
        var activations = 0

        setClickableContent(
            enabled = false,
            onClick = { activations += 1 },
        )

        compose.onNodeWithTag(CLICKABLE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(CLICKABLE_TAG).performKeyInput {
            pressKey(Key.DirectionCenter)
            pressKey(Key.Enter)
            pressKey(Key.NumPadEnter)
        }

        compose.runOnIdle {
            assertEquals(0, activations)
        }
    }

    private fun setClickableContent(
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        compose.setContent {
            val interactionSource = remember { MutableInteractionSource() }
            cardFocusRequester = remember { FocusRequester() }
            Box(
                modifier = Modifier
                    .focusRequester(cardFocusRequester)
                    .testTag(CLICKABLE_TAG)
                    .size(48.dp)
                    .tvFocusableClickable(
                        interactionSource = interactionSource,
                        enabled = enabled,
                        onClick = onClick,
                    ),
            )
        }
    }

    private fun requestCardFocus() {
        compose.runOnIdle {
            cardFocusRequester.requestFocus()
        }
    }

    private lateinit var cardFocusRequester: FocusRequester

    private companion object {
        const val CLICKABLE_TAG = "tv-clickable"
    }
}
