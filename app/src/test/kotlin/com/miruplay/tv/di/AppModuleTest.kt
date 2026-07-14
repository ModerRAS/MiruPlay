package com.miruplay.tv.di

import com.miruplay.tv.data.db.MiruPlayDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class AppModuleTest {
    @Test
    fun `database builder includes every schema migration`() {
        assertEquals(
            listOf(
                MiruPlayDatabase.MIGRATION_1_2,
                MiruPlayDatabase.MIGRATION_2_3,
                MiruPlayDatabase.MIGRATION_3_4,
                MiruPlayDatabase.MIGRATION_4_5,
                MiruPlayDatabase.MIGRATION_5_6,
                MiruPlayDatabase.MIGRATION_6_7,
                MiruPlayDatabase.MIGRATION_7_8,
                MiruPlayDatabase.MIGRATION_8_9,
            ),
            miruPlayDatabaseMigrations().toList(),
        )
    }
}
