package com.miruplay.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.miruplay.tv.data.dao.*
import com.miruplay.tv.data.entity.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 10,
    exportSchema = true,
    entities = [
        AnimeEntity::class,
        EpisodeEntity::class,
        MediaSourceEntity::class,
        ProgressEntity::class,
        IndexEntryEntity::class,
        DramaSeriesCacheEntity::class,
        CloudDriveConfigEntity::class,
        RssSubscriptionEntity::class,
        RssProcessedItemEntity::class,
        RssDownloadTaskEntity::class,
        MusicAlbumEntity::class,
        MusicTrackEntity::class
    ]
)
@TypeConverters(GenreListConverter::class)
abstract class MiruPlayDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun mediaSourceDao(): MediaSourceDao
    abstract fun progressDao(): ProgressDao
    abstract fun indexDao(): IndexDao
    abstract fun dramaSeriesCacheDao(): DramaSeriesCacheDao
    abstract fun musicAlbumDao(): MusicAlbumDao
    abstract fun musicTrackDao(): MusicTrackDao
    abstract fun cloudDriveAutomationDao(): CloudDriveAutomationDao

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS music_album (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artist TEXT,
                        cover_url TEXT,
                        track_count INTEGER NOT NULL DEFAULT 0,
                        source_id INTEGER NOT NULL,
                        last_updated INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS music_track (
                        id TEXT NOT NULL PRIMARY KEY,
                        album_id TEXT NOT NULL,
                        source_id INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        file_name TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT,
                        album_artist TEXT,
                        album_title TEXT,
                        track_number INTEGER,
                        disc_number INTEGER,
                        duration INTEGER NOT NULL DEFAULT 0,
                        cue_path TEXT,
                        cue_track_index INTEGER,
                        cue_start_ms INTEGER NOT NULL DEFAULT 0,
                        cue_end_ms INTEGER,
                        is_cue_virtual INTEGER NOT NULL DEFAULT 0,
                        cover_path TEXT,
                        last_modified INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_music_track_album_id ON music_track(album_id)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_music_track_source_id_file_path_cue_track_index ON music_track(source_id, file_path, cue_track_index)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE index_entry ADD COLUMN extra_kind INTEGER")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN extra_ordinal INTEGER")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN extra_sort_order INTEGER")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN duration INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE index_entry ADD COLUMN external_subtitle_paths TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS drama_series_cache (
                        series_id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        original_title TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        season_count INTEGER NOT NULL,
                        episode_count INTEGER NOT NULL,
                        poster_url TEXT,
                        fanart_url TEXT,
                        first_air_date TEXT,
                        metadata_source TEXT,
                        metadata_id TEXT,
                        last_updated INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO drama_series_cache (
                        series_id,
                        title,
                        original_title,
                        summary,
                        season_count,
                        episode_count,
                        poster_url,
                        fanart_url,
                        first_air_date,
                        metadata_source,
                        metadata_id,
                        last_updated
                    )
                    SELECT
                        substr(id, 14),
                        title,
                        COALESCE(title_cn, ''),
                        COALESCE(summary, ''),
                        0,
                        episode_count,
                        poster_url,
                        fanart_url,
                        air_date,
                        CASE
                            WHEN tmdb_id IS NOT NULL AND TRIM(tmdb_id) <> '' THEN 'TMDB'
                            ELSE NULL
                        END,
                        tmdb_id,
                        last_updated
                    FROM anime
                    WHERE id LIKE 'drama-series:%'
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media_source ADD COLUMN content_mode TEXT NOT NULL DEFAULT 'ANIME'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cloud_drive_config ADD COLUMN library_mode TEXT NOT NULL DEFAULT 'ORGANIZED_LIBRARY'")
                database.execSQL("ALTER TABLE anime ADD COLUMN poster_local_path TEXT")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN episode_title TEXT")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN plot TEXT")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN metadata_source TEXT")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN metadata_id TEXT")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN metadata_title TEXT")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN scrape_status TEXT")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN scrape_message TEXT")
                database.execSQL("ALTER TABLE index_entry ADD COLUMN scraped_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cloud_drive_config ADD COLUMN rss_proxy_enabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE cloud_drive_config ADD COLUMN rss_proxy_host TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE cloud_drive_config ADD COLUMN rss_proxy_port INTEGER NOT NULL DEFAULT 1080")
            }
        }

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
