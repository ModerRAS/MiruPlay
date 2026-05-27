package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.design.MiruPlayComposeKeyProfile
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.isActivationIntent
import com.miruplay.tv.design.miruPlayInputIntentFromComposeKeyCode

internal fun Key.isTvActivateKey(): Boolean =
    toMiruPlayInputIntent()?.isActivationIntent() == true

internal fun Key.toMiruPlayInputIntent(): MiruPlayInputIntent? =
    miruPlayInputIntentFromComposeKeyCode(
        keyCode = keyCode,
        profile = MiruPlayComposeKeyProfile.Android,
    )
