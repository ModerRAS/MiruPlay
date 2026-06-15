package com.miruplay.tv.player

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardPlaybackPlayer

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalPlaybackPlayer
