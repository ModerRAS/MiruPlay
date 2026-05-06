package com.miruplay.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OverscanContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.padding(
            start = 48.dp,
            end = 48.dp,
            top = 27.dp,
            bottom = 27.dp
        )
    ) {
        content()
    }
}