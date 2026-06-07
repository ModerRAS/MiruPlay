package com.miruplay.tv.di

import com.miruplay.tv.data.db.MiruPlayDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class AppModuleTest {
    @Test
    fun `database builder migrations include schema 5 to 6 upgrade`() {
        assertEquals(
            listOf(
                MiruPlayDatabase.MIGRATION_1_2,
                MiruPlayDatabase.MIGRATION_2_3,
                MiruPlayDatabase.MIGRATION_3_4,
                MiruPlayDatabase.MIGRATION_4_5,
                MiruPlayDatabase.MIGRATION_5_6,
            ),
            miruPlayDatabaseMigrations().toList(),
        )
    }
}
