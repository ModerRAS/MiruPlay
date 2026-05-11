package com.miruplay.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.miruplay.tv.data.dao.*
import com.miruplay.tv.data.entity.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 2,
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

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE anime ADD COLUMN bangumi_collection_type INTEGER")
                database.execSQL("ALTER TABLE anime ADD COLUMN bangumi_ep_status INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE episode ADD COLUMN bangumi_episode_id INTEGER")
                database.execSQL("ALTER TABLE episode ADD COLUMN bangumi_collection_type INTEGER")
            }
        }
    }
}
