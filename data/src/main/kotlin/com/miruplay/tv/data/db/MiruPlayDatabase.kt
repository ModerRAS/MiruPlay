package com.miruplay.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.miruplay.tv.data.dao.*
import com.miruplay.tv.data.entity.*

@Database(
    version = 1,
    entities = [
        AnimeEntity::class,
        EpisodeEntity::class,
        MediaSourceEntity::class,
        ProgressEntity::class,
        IndexEntryEntity::class
    ]
)
@TypeConverters(GenreListConverter::class)
abstract class MiruPlayDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun mediaSourceDao(): MediaSourceDao
    abstract fun progressDao(): ProgressDao
    abstract fun indexDao(): IndexDao
}
