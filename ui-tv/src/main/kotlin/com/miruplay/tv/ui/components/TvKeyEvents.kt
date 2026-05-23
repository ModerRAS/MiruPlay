package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.Key

internal fun Key.isTvActivateKey(): Boolean =
    this == Key.DirectionCenter ||
        this == Key.Enter ||
        this == Key.NumPadEnter ||
        this == Key.Spacebar
