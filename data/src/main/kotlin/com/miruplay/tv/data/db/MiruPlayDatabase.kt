package com.miruplay.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.miruplay.tv.data.dao.*
import com.miruplay.tv.data.entity.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 3,
    entities = [
        AnimeEntity::class,
        EpisodeEntity::class,
        MediaSourceEntity::class,
        ProgressEntity::class,
        IndexEntryEntity::class,
        CloudDriveConfigEntity::class,
        RssSubscriptionEntity::class,
        RssProcessedItemEntity::class,
        RssDownloadTaskEntity::class
    ]
)
@TypeConverters(GenreListConverter::class)
abstract class MiruPlayDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun mediaSourceDao(): MediaSourceDao
    abstract fun progressDao(): ProgressDao
    abstract fun indexDao(): IndexDao
    abstract fun cloudDriveAutomationDao(): CloudDriveAutomationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE anime ADD COLUMN bangumi_collection_type INTEGER")
                database.execSQL("ALTER TABLE anime ADD COLUMN bangumi_ep_status INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE episode ADD COLUMN bangumi_episode_id INTEGER")
                database.execSQL("ALTER TABLE episode ADD COLUMN bangumi_collection_type INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cloud_drive_config (
                        id INTEGER NOT NULL PRIMARY KEY,
                        endpoint_url TEXT NOT NULL DEFAULT '',
                        username TEXT NOT NULL DEFAULT '',
                        webdav_source_id INTEGER,
                        inbox_path TEXT NOT NULL DEFAULT '',
                        library_path TEXT NOT NULL DEFAULT '',
                        interval_minutes INTEGER NOT NULL DEFAULT 30,
                        enabled INTEGER NOT NULL DEFAULT 0,
                        last_run_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rss_subscription (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        url TEXT NOT NULL,
                        filter_regex TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        last_checked_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rss_subscription_url ON rss_subscription(url)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rss_processed_item (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subscription_id INTEGER NOT NULL,
                        item_key TEXT NOT NULL,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        processed_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_rss_processed_item_subscription_id_item_key " +
                        "ON rss_processed_item(subscription_id, item_key)"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rss_download_task (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subscription_id INTEGER NOT NULL,
                        item_key TEXT NOT NULL,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        status TEXT NOT NULL,
                        message TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_rss_download_task_subscription_id_item_key " +
                        "ON rss_download_task(subscription_id, item_key)"
                )
            }
        }
    }
}
