package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.Result

fun <T> requireWebControlSuccess(result: Result<T>, message: String): T =
    when (result) {
        is Result.Success -> result.data
        is Result.Error -> throw IllegalStateException("$message: ${result.error.toUserMessage()}")
    }
