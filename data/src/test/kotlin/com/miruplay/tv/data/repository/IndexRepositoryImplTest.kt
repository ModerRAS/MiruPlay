package com.miruplay.tv.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.data.db.MiruPlayDatabase
import com.miruplay.tv.repository.MediaIndexEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndexRepositoryImplTest {
    private lateinit var database: MiruPlayDatabase
    private lateinit var repository: IndexRepositoryImpl

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MiruPlayDatabase::class.java,
        ).build()
        repository = IndexRepositoryImpl(
            indexDao = database.indexDao(),
            database = database,
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `getAnimeInIndex prefers metadata id when present`() = runBlocking {
        repository.rebuildIndex(
            sourceId = 7L,
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 7L,
                    path = "/Series/01.mkv",
                    animeName = "HK1 MLIP Test",
                    metadataId = "mlip:7:series-uuid",
                    metadataTitle = "HK1 MLIP Test",
                ),
                MediaIndexEntry(
                    sourceId = 7L,
                    path = "/Loose/01.mkv",
                    animeName = "Loose Name",
                ),
            ),
        )

        val keys = repository.getAnimeInIndex(7L).getOrNull().orEmpty()

        assertEquals(listOf("Loose Name", "mlip:7:series-uuid"), keys)
    }
}
