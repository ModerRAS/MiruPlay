package com.miruplay.tv.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiruPlayDatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 adds bangumi columns`() {
        val database = RecordingSupportSQLiteDatabase()

        MiruPlayDatabase.MIGRATION_1_2.migrate(database.proxy)

        assertEquals(
            listOf(
                "ALTER TABLE anime ADD COLUMN bangumi_collection_type INTEGER",
                "ALTER TABLE anime ADD COLUMN bangumi_ep_status INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE episode ADD COLUMN bangumi_episode_id INTEGER",
                "ALTER TABLE episode ADD COLUMN bangumi_collection_type INTEGER"
            ),
            database.sql
        )
    }

    @Test
    fun `migration 2 to 3 creates cloud drive rss tables and indexes`() {
        val database = RecordingSupportSQLiteDatabase()

        MiruPlayDatabase.MIGRATION_2_3.migrate(database.proxy)

        val normalizedSql = database.sql.map { it.replace(Regex("\\s+"), " ").trim() }
        assertTrue(normalizedSql.any { it.startsWith("CREATE TABLE IF NOT EXISTS cloud_drive_config") })
        assertTrue(normalizedSql.any { it.startsWith("CREATE TABLE IF NOT EXISTS rss_subscription") })
        assertTrue(normalizedSql.any { it == "CREATE UNIQUE INDEX IF NOT EXISTS index_rss_subscription_url ON rss_subscription(url)" })
        assertTrue(normalizedSql.any { it.startsWith("CREATE TABLE IF NOT EXISTS rss_processed_item") })
        assertTrue(
            normalizedSql.any {
                it == "CREATE UNIQUE INDEX IF NOT EXISTS index_rss_processed_item_subscription_id_item_key ON rss_processed_item(subscription_id, item_key)"
            }
        )
        assertTrue(normalizedSql.any { it.startsWith("CREATE TABLE IF NOT EXISTS rss_download_task") })
        assertTrue(
            normalizedSql.any {
                it == "CREATE INDEX IF NOT EXISTS index_rss_download_task_subscription_id_item_key ON rss_download_task(subscription_id, item_key)"
            }
        )
    }

    @Test
    fun `migration 3 to 4 adds rss proxy columns`() {
        val database = RecordingSupportSQLiteDatabase()

        MiruPlayDatabase.MIGRATION_3_4.migrate(database.proxy)

        assertEquals(
            listOf(
                "ALTER TABLE cloud_drive_config ADD COLUMN rss_proxy_enabled INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE cloud_drive_config ADD COLUMN rss_proxy_host TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE cloud_drive_config ADD COLUMN rss_proxy_port INTEGER NOT NULL DEFAULT 1080"
            ),
            database.sql
        )
    }

    @Test
    fun `migration 4 to 5 adds library and metadata columns`() {
        val database = RecordingSupportSQLiteDatabase()

        MiruPlayDatabase.MIGRATION_4_5.migrate(database.proxy)

        assertEquals(
            listOf(
                "ALTER TABLE cloud_drive_config ADD COLUMN library_mode TEXT NOT NULL DEFAULT 'ORGANIZED_LIBRARY'",
                "ALTER TABLE anime ADD COLUMN poster_local_path TEXT",
                "ALTER TABLE index_entry ADD COLUMN episode_title TEXT",
                "ALTER TABLE index_entry ADD COLUMN plot TEXT",
                "ALTER TABLE index_entry ADD COLUMN metadata_source TEXT",
                "ALTER TABLE index_entry ADD COLUMN metadata_id TEXT",
                "ALTER TABLE index_entry ADD COLUMN metadata_title TEXT",
                "ALTER TABLE index_entry ADD COLUMN scrape_status TEXT",
                "ALTER TABLE index_entry ADD COLUMN scrape_message TEXT",
                "ALTER TABLE index_entry ADD COLUMN scraped_at INTEGER NOT NULL DEFAULT 0"
            ),
            database.sql
        )
    }

    @Test
    fun `migration 5 to 6 adds media source content mode with anime default`() {
        val database = RecordingSupportSQLiteDatabase()

        MiruPlayDatabase.MIGRATION_5_6.migrate(database.proxy)

        assertEquals(
            listOf(
                "ALTER TABLE media_source ADD COLUMN content_mode TEXT NOT NULL DEFAULT 'ANIME'"
            ),
            database.sql
        )
    }

    @Test
    fun `migration 6 to 7 creates drama series cache and backfills legacy drama rows`() {
        val database = RecordingSupportSQLiteDatabase()

        MiruPlayDatabase.MIGRATION_6_7.migrate(database.proxy)

        val normalizedSql = database.sql.map { it.replace(Regex("\\s+"), " ").trim() }
        assertTrue(normalizedSql.any { it.startsWith("CREATE TABLE IF NOT EXISTS drama_series_cache") })
        assertTrue(normalizedSql.any { it.contains("INSERT OR REPLACE INTO drama_series_cache") })
        assertTrue(normalizedSql.any { it.contains("FROM anime") && it.contains("WHERE id LIKE 'drama-series:%'") })
    }

    @Test
    fun `migration 7 to 8 adds external subtitle paths with empty list default`() {
        val database = RecordingSupportSQLiteDatabase()

        MiruPlayDatabase.MIGRATION_7_8.migrate(database.proxy)

        assertEquals(
            listOf("ALTER TABLE index_entry ADD COLUMN external_subtitle_paths TEXT NOT NULL DEFAULT '[]'"),
            database.sql,
        )
    }
}

private class RecordingSupportSQLiteDatabase : InvocationHandler {
    val sql = mutableListOf<String>()

    val proxy: SupportSQLiteDatabase =
        Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            this
        ) as SupportSQLiteDatabase

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        if (method.name == "execSQL" && args?.firstOrNull() is String) {
            sql += args.first() as String
            return null
        }

        return when (method.returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
    }
}
