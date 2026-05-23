package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataBatchOperationsTest {
    @Test
    fun `applyMetadataBatchPlan writes ready updates and stores rollback`() = runBlocking {
        val repository = FakeMediaIndexRepository()
        val original = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Frieren/01.mkv",
            animeName = "Frieren",
        )
        val plan = MetadataBatchPlanner.planFor(
            entries = listOf(original),
            matches = listOf(MetadataBatchMatch(query = "Frieren", result = result())),
        )

        val write = repository.applyMetadataBatchPlan(sourceId = 7L, plan = plan)

        assertEquals(listOf(original.copy(sourceId = 7L)), write.rollbackEntries)
        assertEquals(write.rollbackEntries, repository.lastUndo)
        assertEquals(1, write.updatedEntries.size)
        assertEquals(7L, write.updatedEntries.single().sourceId)
        assertEquals("431767", write.updatedEntries.single().metadataId)
        assertEquals(write.updatedEntries.single(), repository.entries.single())
    }

    @Test
    fun `restoreMetadataBatchUndo prefers in-memory rollback and clears saved undo`() = runBlocking {
        val repository = FakeMediaIndexRepository()
        repository.lastUndo = listOf(
            MediaIndexEntry(sourceId = 7L, path = "D:/Anime/Saved/01.mkv", animeName = "Saved")
        )
        val rollback = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren")
        )

        val result = repository.restoreMetadataBatchUndo(
            sourceId = 7L,
            preferredRollbackEntries = rollback,
        ) as Result.Success

        assertEquals(1, result.data.restoredCount)
        assertEquals(rollback.map { it.copy(sourceId = 7L) }, result.data.rollbackEntries)
        assertEquals(rollback.single().copy(sourceId = 7L), repository.entries.single())
        assertTrue(repository.clearedUndo)
        assertEquals(emptyList<MediaIndexEntry>(), repository.lastUndo)
    }

    @Test
    fun `restoreMetadataBatchUndo loads saved rollback when no preferred entries are available`() = runBlocking {
        val repository = FakeMediaIndexRepository()
        val saved = listOf(
            MediaIndexEntry(sourceId = 7L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren")
        )
        repository.lastUndo = saved

        val result = repository.restoreMetadataBatchUndo(sourceId = 7L) as Result.Success

        assertEquals(1, result.data.restoredCount)
        assertEquals(saved, result.data.rollbackEntries)
        assertEquals(saved.single(), repository.entries.single())
        assertTrue(repository.clearedUndo)
    }

    @Test
    fun `metadata batch operation summaries share TV wording`() {
        val entry = MediaIndexEntry(sourceId = 7L, path = "D:/Anime/Frieren/01.mkv")
        val write = MetadataBatchWriteResult(
            updatedEntries = listOf(entry),
            rollbackEntries = listOf(entry),
        )

        assertEquals(
            "已将 Bangumi 批量元数据应用到 1 个索引条目，跳过 2 个冲突。",
            write.appliedStatus(conflictCount = 2),
        )
        assertEquals(
            "已接受复核的 Bangumi 匹配，更新 1 个索引条目。",
            write.reviewAcceptedStatus(),
        )
        assertEquals(
            "已从上一次 Bangumi 批量更改中恢复 2 个索引条目。",
            MetadataBatchUndoResult(
                rollbackEntries = listOf(entry, entry.copy(path = "D:/Anime/Frieren/02.mkv")),
                restoredCount = 2,
            ).restoredStatus(),
        )
        assertEquals(
            "请先运行批量预览；当前没有可直接应用的高置信匹配。",
            noMetadataBatchPreviewStatus(),
        )
        assertEquals(
            "没有可撤销的 Bangumi 批量更改。",
            noMetadataBatchUndoStatus(),
        )
    }

    private class FakeMediaIndexRepository : MediaIndexRepository {
        val entries = mutableListOf<MediaIndexEntry>()
        var lastUndo = emptyList<MediaIndexEntry>()
        var clearedUndo = false

        override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> {
            this.entries.removeAll { it.sourceId == sourceId }
            this.entries += entries.map { it.copy(sourceId = sourceId) }
            return Result.success(Unit)
        }

        override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> {
            val normalized = entry.copy(sourceId = sourceId)
            entries.removeAll { it.sourceId == sourceId && it.path == normalized.path }
            entries += normalized
            return Result.success(Unit)
        }

        override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> =
            Result.success(entries.filter { it.sourceId == sourceId })

        override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> =
            Result.success(emptyList())

        override suspend fun clearIndex(sourceId: Long): Result<Unit> {
            entries.removeAll { it.sourceId == sourceId }
            return Result.success(Unit)
        }

        override suspend fun saveLastBatchUndo(
            sourceId: Long,
            entries: List<MediaIndexEntry>,
        ): Result<Unit> {
            lastUndo = entries.map { it.copy(sourceId = sourceId) }
            return Result.success(Unit)
        }

        override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> =
            Result.success(lastUndo)

        override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> {
            clearedUndo = true
            lastUndo = emptyList()
            return Result.success(Unit)
        }
    }

    private fun result(): ScraperResult =
        ScraperResult(
            animeId = "431767",
            title = "Frieren",
            titleCn = "葬送的芙莉莲",
            matchedTitle = "葬送的芙莉莲",
            confidence = 0.95f,
            source = ScraperSource.BANGUMI,
        )
}
