package com.miruplay.tv.scanner

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.Episode
import com.miruplay.tv.repository.MediaExtraKind
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaScrapeStatus
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.localMetadataOverrideKey
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MLIP_DATABASE_PATH = "library.db"
private val SUPPORTED_MLIP_SCHEMA_VERSIONS = 1..3
private const val MLIP_METADATA_SOURCE = "MLIP"

@Singleton
class MlipLibraryIndexImporter @Inject constructor(
    private val indexRepository: MediaIndexRepository,
    private val metadataRepository: MetadataRepository,
) {
    suspend fun importLibrary(
        source: MediaSourceInfo,
        mediaSource: MediaSource,
        posterCacheDirectory: File? = null,
    ): Result<MlipImportResult> = withContext(Dispatchers.IO) {
        if (source.type != MediaSourceType.WEBDAV) {
            return@withContext Result.failure(
                AppError.LibraryIndexError.InvalidSchema("MLIP is only supported for WebDAV sources"),
            )
        }
        val databaseFile = when (val copied = copyLibraryDatabase(mediaSource)) {
            is Result.Success -> copied.data
            is Result.Error -> return@withContext copied
        }
        val snapshot = try {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                when (val validation = validateMlipDatabase(database)) {
                    is Result.Success -> Unit
                    is Result.Error -> return@withContext validation
                }
                readSnapshot(database, source.id)
            }
        } catch (error: InvalidMlipSchemaException) {
            return@withContext Result.failure(AppError.LibraryIndexError.InvalidSchema(error.reason))
        } catch (error: Exception) {
            return@withContext Result.failure(AppError.LibraryIndexError.ReadFailed(error.message ?: error.toString()))
        } finally {
            databaseFile.delete()
        }

        val previousEntries = when (val queried = indexRepository.queryIndex(source.id, "")) {
            is Result.Success -> queried.data
            is Result.Error -> return@withContext Result.failure(queried.error)
        }
        val mediaFiles = snapshot.mediaFiles
        val entries = mediaFiles.map { it.indexEntry } + snapshot.extras
        when (val rebuilt = indexRepository.rebuildIndex(source.id, entries)) {
            is Result.Success -> Unit
            is Result.Error -> return@withContext Result.failure(rebuilt.error)
        }
        val incomingAnimeIds = snapshot.series.mapTo(mutableSetOf()) { it.anime.id }
        previousEntries.mapNotNull(MediaIndexEntry::mlipAnimeIdOrNull)
            .filterNot(incomingAnimeIds::contains)
            .distinct()
            .forEach { animeId ->
                when (val invalidated = metadataRepository.invalidateCache(animeId)) {
                    is Result.Success -> Unit
                    is Result.Error -> return@withContext Result.failure(invalidated.error)
                }
            }

        var artworkCachedCount = 0
        for (series in snapshot.series) {
            val seriesFiles = mediaFiles.filter { it.seriesId == series.id }
            val cachedAnime = metadataRepository.getCachedMetadata(series.anime.id).getOrNull()
            val cachedEpisodeList = metadataRepository.getCachedEpisodes(series.anime.id).getOrNull().orEmpty()
            val cachedEpisodesById = cachedEpisodeList.associateBy(Episode::id)
            val cachedEpisodesByNumber = cachedEpisodeList
                .groupBy { it.seasonNumber to it.episodeNumber }
                .mapNotNull { (key, matches) -> matches.singleOrNull()?.let { key to it } }
                .toMap()
            val episodes = seriesFiles.map { file ->
                val incoming = file.episode
                val cached = cachedEpisodesById[incoming.id]
                    ?: cachedEpisodesByNumber[incoming.seasonNumber to incoming.episodeNumber]
                incoming.copy(
                    id = cached?.id ?: incoming.id,
                    watchedPosition = cached?.watchedPosition ?: incoming.watchedPosition,
                    lastWatchedTimestamp = cached?.lastWatchedTimestamp ?: incoming.lastWatchedTimestamp,
                    playCount = cached?.playCount ?: incoming.playCount,
                    thumbnailPath = cached?.thumbnailPath ?: incoming.thumbnailPath,
                    bangumiEpisodeId = cached?.bangumiEpisodeId,
                    bangumiCollectionType = cached?.bangumiCollectionType,
                )
            }
            when (val cached = metadataRepository.cacheEpisodes(series.anime.id, episodes)) {
                is Result.Success -> Unit
                is Result.Error -> return@withContext Result.failure(cached.error)
            }
            val posterLocalPath = series.posterPath
                ?.let { poster -> cacheArtwork(mediaSource, poster, posterCacheDirectory, source.id, series.uuid) }
                ?.also { artworkCachedCount += 1 }
            when (val cached = metadataRepository.cacheMetadata(
                series.anime.copy(
                    episodeCount = episodes.size,
                    posterLocalPath = posterLocalPath ?: series.anime.posterLocalPath,
                    bangumiCollectionType = cachedAnime?.bangumiCollectionType,
                    bangumiEpStatus = cachedAnime?.bangumiEpStatus ?: series.anime.bangumiEpStatus,
                ),
            )) {
                is Result.Success -> Unit
                is Result.Error -> return@withContext Result.failure(cached.error)
            }
        }
        val result = MlipImportResult(
            seriesCount = snapshot.series.size,
            episodeCount = snapshot.mediaFiles.map { it.episodeId }.distinct().size,
            mediaFileCount = snapshot.mediaFiles.size,
            skippedFileCount = snapshot.skippedFiles,
            artworkCachedCount = artworkCachedCount,
            nonIntegerEpisodeCount = snapshot.nonIntegerEpisodes,
            extraCount = snapshot.extras.size,
        )
        MiruLog.i(
            TAG,
            "MLIP library index imported",
            mapOf(
                "source_id" to source.id.toString(),
                "series_count" to result.seriesCount.toString(),
                "episode_count" to result.episodeCount.toString(),
                "media_file_count" to result.mediaFileCount.toString(),
                "skipped_file_count" to result.skippedFileCount.toString(),
                "non_integer_episode_count" to result.nonIntegerEpisodeCount.toString(),
                "extra_count" to result.extraCount.toString(),
            ),
        )
        Result.success(result)
    }

    private suspend fun copyLibraryDatabase(mediaSource: MediaSource): Result<File> {
        val stream = when (val opened = mediaSource.openStream(MLIP_DATABASE_PATH)) {
            is Result.Success -> opened.data
            is Result.Error -> {
                val error = opened.error
                return if (error is AppError.MediaSourceError.NotFound) {
                    Result.failure(AppError.LibraryIndexError.Missing(MLIP_DATABASE_PATH))
                } else {
                    Result.failure(AppError.LibraryIndexError.ReadFailed(error.toUserMessage()))
                }
            }
        }
        return try {
            val temp = File.createTempFile("miruplay-mlip-", ".db")
            stream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            Result.success(temp)
        } catch (error: Exception) {
            Result.failure(AppError.LibraryIndexError.ReadFailed(error.message ?: error.toString()))
        }
    }

    private fun validateMlipDatabase(database: SQLiteDatabase): Result<Unit> {
        val userVersion = database.singleInt("PRAGMA user_version") ?: 0
        if (userVersion !in SUPPORTED_MLIP_SCHEMA_VERSIONS) {
            return Result.failure(AppError.LibraryIndexError.UnsupportedVersion(userVersion))
        }
        val tables = database.tableNames()
        val expectedTables = requiredTables +
            (if (userVersion >= 2) v2RequiredTables else emptySet()) +
            (if (userVersion >= 3) v3RequiredTables else emptySet())
        val missing = expectedTables.filterNot { it in tables }
        if (missing.isNotEmpty()) {
            return Result.failure(AppError.LibraryIndexError.InvalidSchema("Missing tables: ${missing.joinToString()}"))
        }
        val protocol = database.singleString("SELECT value FROM meta WHERE key = ?", "protocol")
        val schema = database.singleString("SELECT value FROM meta WHERE key = ?", "schema")
        if (!protocol.equals("MLIP", ignoreCase = true)) {
            return Result.failure(AppError.LibraryIndexError.InvalidSchema("meta.protocol is not MLIP"))
        }
        if (schema != userVersion.toString()) {
            return Result.failure(AppError.LibraryIndexError.InvalidSchema("meta.schema does not match user_version $userVersion"))
        }
        if (userVersion >= 3 && database.singleInt(
                "SELECT enabled FROM capability WHERE name = ?",
                "extra",
            ) != 1
        ) {
            return Result.failure(AppError.LibraryIndexError.InvalidSchema("MLIP v3 requires capability.extra = 1"))
        }
        return Result.success(Unit)
    }

    private fun readSnapshot(database: SQLiteDatabase, sourceId: Long): MlipSnapshot {
        val genresBySeriesId = database.readGenresBySeriesId()
        val externalIdsBySeriesId = database.readExternalIdsBySeriesId()
        val releaseDatesBySeriesId = database.readReleaseDatesBySeriesId()
        val posterBySeriesId = database.readPosterBySeriesId()
        val externalSubtitlePathsByMediaFileId = database.readExternalSubtitlePathsByMediaFileId()
        val seriesById = database.readSeries(
            sourceId,
            genresBySeriesId,
            externalIdsBySeriesId,
            releaseDatesBySeriesId,
            posterBySeriesId,
        )
        val mediaFiles = mutableListOf<MlipMediaFile>()
        var skippedFiles = 0
        var nonIntegerEpisodes = 0
        database.rawQuery(
            """
            SELECT
                media_file.id AS media_file_id,
                media_file.path AS media_path,
                media_file.size AS media_size,
                media_file.modified_time AS media_modified_time,
                episode.id AS episode_id,
                episode.uuid AS episode_uuid,
                episode.series_id AS series_id,
                episode.season AS season,
                episode.episode AS episode_number,
                episode.sort_order AS sort_order,
                episode.title AS episode_title,
                episode.runtime AS runtime
            FROM media_file
            INNER JOIN episode ON episode.id = media_file.episode_id
            ORDER BY series_id ASC, season ASC, sort_order ASC, media_path ASC
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val mediaFileId = cursor.long("media_file_id")
                val rawPath = cursor.string("media_path")
                val indexPath = normalizeMlipMediaPath(rawPath)
                    ?: throw InvalidMlipSchemaException("Unsafe media path: $rawPath")
                val episodeNumber = cursor.double("episode_number").toIntegerEpisodeNumber()
                if (!MediaFileConventions.isVideoName(indexPath) || episodeNumber == null) {
                    skippedFiles += 1
                    if (episodeNumber == null) nonIntegerEpisodes += 1
                    continue
                }
                val seriesId = cursor.long("series_id")
                val series = seriesById[seriesId]
                if (series == null) {
                    skippedFiles += 1
                    continue
                }
                val season = cursor.int("season").coerceAtLeast(1)
                val episodeTitle = cursor.stringOrNull("episode_title").orEmpty()
                val episodeId = cursor.long("episode_id")
                val mediaSize = cursor.longOrNull("media_size") ?: 0L
                val modifiedTime = cursor.longOrNull("media_modified_time").toMlipEpochMillis()
                val runtimeMs = (cursor.longOrNull("runtime") ?: 0L).coerceAtLeast(0L) * 1000L
                val episode = Episode(
                    id = "$sourceId:$indexPath",
                    animeId = series.anime.id,
                    seasonNumber = season,
                    episodeNumber = episodeNumber,
                    title = episodeTitle,
                    filePath = indexPath,
                    fileName = MediaPathConventions.fileName(indexPath),
                    duration = runtimeMs,
                )
                val indexEntry = MediaIndexEntry(
                    sourceId = sourceId,
                    path = indexPath,
                    externalSubtitlePaths = externalSubtitlePathsByMediaFileId[mediaFileId].orEmpty(),
                    animeName = series.anime.displayTitleForIndex(),
                    episodeTitle = episodeTitle,
                    seasonNumber = season,
                    episodeNumber = episodeNumber,
                    metadataSource = MLIP_METADATA_SOURCE,
                    metadataId = series.anime.id,
                    metadataTitle = series.anime.displayTitleForIndex(),
                    scrapeStatus = MediaScrapeStatus.SCRAPED,
                    scrapeMessage = "Imported from MLIP library.db",
                    scrapedAt = System.currentTimeMillis(),
                    isDirectory = false,
                    fileSize = mediaSize,
                    lastModified = modifiedTime,
                )
                mediaFiles += MlipMediaFile(
                    episodeId = episodeId,
                    seriesId = seriesId,
                    indexEntry = indexEntry,
                    episode = episode,
                )
            }
        }
        return MlipSnapshot(
            series = seriesById.values.toList(),
            mediaFiles = mediaFiles,
            extras = if ((database.singleInt("PRAGMA user_version") ?: 0) >= 3) {
                database.readExtras(sourceId, seriesById)
            } else {
                emptyList()
            },
            skippedFiles = skippedFiles,
            nonIntegerEpisodes = nonIntegerEpisodes,
        )
    }

    private fun SQLiteDatabase.readExtras(
        sourceId: Long,
        seriesById: Map<Long, MlipSeries>,
    ): List<MediaIndexEntry> {
        if ("media_extra" !in tableNames()) return emptyList()
        val result = mutableListOf<MediaIndexEntry>()
        rawQuery(
            """
            SELECT series_id, extra_kind, ordinal, sort_order, title, path, size, modified_time, runtime
            FROM media_extra
            ORDER BY series_id, extra_kind, sort_order, path
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val series = seriesById[cursor.long("series_id")]
                    ?: throw InvalidMlipSchemaException("Extra references an unknown series")
                val rawKind = cursor.int("extra_kind")
                val kind = MediaExtraKind.fromValue(rawKind)
                if (kind == MediaExtraKind.UNKNOWN) {
                    throw InvalidMlipSchemaException("Unknown extra_kind: $rawKind")
                }
                val rawPath = cursor.string("path")
                val indexPath = normalizeMlipMediaPath(rawPath)
                    ?: throw InvalidMlipSchemaException("Unsafe extra path: $rawPath")
                if (!MediaFileConventions.isVideoName(indexPath)) {
                    throw InvalidMlipSchemaException("Extra path is not a video: $rawPath")
                }
                result += MediaIndexEntry(
                    sourceId = sourceId,
                    path = indexPath,
                    animeName = series.anime.displayTitleForIndex(),
                    episodeTitle = cursor.string("title"),
                    metadataSource = MLIP_METADATA_SOURCE,
                    metadataId = series.anime.id,
                    metadataTitle = series.anime.displayTitleForIndex(),
                    scrapeStatus = MediaScrapeStatus.SCRAPED,
                    scrapeMessage = "Imported from MLIP library.db",
                    scrapedAt = System.currentTimeMillis(),
                    fileSize = cursor.longOrNull("size") ?: 0L,
                    lastModified = cursor.longOrNull("modified_time").toMlipEpochMillis(),
                    extraKind = kind,
                    extraOrdinal = cursor.int("ordinal"),
                    extraSortOrder = cursor.int("sort_order"),
                    duration = (cursor.longOrNull("runtime") ?: 0L).coerceAtLeast(0L) * 1000L,
                )
            }
        }
        return result
    }

    private fun SQLiteDatabase.readExternalSubtitlePathsByMediaFileId(): Map<Long, List<String>> {
        if ("media_subtitle" !in tableNames()) return emptyMap()
        val result = linkedMapOf<Long, MutableList<String>>()
        rawQuery(
            "SELECT media_file_id, path FROM media_subtitle ORDER BY media_file_id, sort_order, path",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val rawPath = cursor.string("path")
                val normalized = normalizeMlipMediaPath(rawPath)
                    ?: throw InvalidMlipSchemaException("Unsafe subtitle path: $rawPath")
                result.getOrPut(cursor.long("media_file_id")) { mutableListOf() }.add(normalized)
            }
        }
        return result.mapValues { (_, paths) -> paths.distinct() }
    }

    private fun SQLiteDatabase.readSeries(
        sourceId: Long,
        genresBySeriesId: Map<Long, List<String>>,
        externalIdsBySeriesId: Map<Long, MlipExternalIds>,
        releaseDatesBySeriesId: Map<Long, String>,
        posterBySeriesId: Map<Long, String>,
    ): Map<Long, MlipSeries> {
        val result = linkedMapOf<Long, MlipSeries>()
        rawQuery(
            """
            SELECT id, uuid, title, original_title, summary, year
            FROM series
            ORDER BY title ASC
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.long("id")
                val uuid = cursor.string("uuid")
                val title = cursor.string("title").ifBlank { "Unknown" }
                val originalTitle = cursor.stringOrNull("original_title")?.takeIf { it.isNotBlank() }
                val externalIds = externalIdsBySeriesId[id] ?: MlipExternalIds()
                val animeId = mlipAnimeId(sourceId, uuid)
                val anime = Anime(
                    id = animeId,
                    title = originalTitle ?: title,
                    titleCn = title.takeIf { originalTitle != null && it != originalTitle },
                    summary = cursor.stringOrNull("summary").orEmpty(),
                    genres = genresBySeriesId[id].orEmpty(),
                    airDate = releaseDatesBySeriesId[id]
                        ?: cursor.intOrNull("year")?.takeIf { it > 0 }?.toString(),
                    bangumiId = externalIds.bangumiId,
                    tmdbId = externalIds.tmdbId,
                )
                result[id] = MlipSeries(
                    id = id,
                    uuid = uuid,
                    anime = anime,
                    posterPath = posterBySeriesId[id],
                )
            }
        }
        return result
    }

    private fun SQLiteDatabase.readReleaseDatesBySeriesId(): Map<Long, String> {
        if ("series_release_date" !in tableNames()) return emptyMap()
        val result = mutableMapOf<Long, String>()
        rawQuery(
            "SELECT series_id, air_date FROM series_release_date",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val airDate = cursor.string("air_date").trim()
                if (airDate.isNotEmpty()) result[cursor.long("series_id")] = airDate
            }
        }
        return result
    }

    private fun SQLiteDatabase.readGenresBySeriesId(): Map<Long, List<String>> {
        val result = mutableMapOf<Long, MutableList<String>>()
        rawQuery(
            """
            SELECT series_genre.series_id AS series_id, genre.name AS genre_name
            FROM series_genre
            INNER JOIN genre ON genre.id = series_genre.genre_id
            ORDER BY genre.name ASC
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.getOrPut(cursor.long("series_id")) { mutableListOf() } += cursor.string("genre_name")
            }
        }
        return result
    }

    private fun SQLiteDatabase.readExternalIdsBySeriesId(): Map<Long, MlipExternalIds> {
        val result = mutableMapOf<Long, MlipExternalIds>()
        rawQuery(
            "SELECT series_id, provider, value FROM series_external_id",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val seriesId = cursor.long("series_id")
                val current = result[seriesId] ?: MlipExternalIds()
                val value = cursor.string("value").toIntOrNull()
                result[seriesId] = when (cursor.int("provider")) {
                    1 -> current.copy(bangumiId = value)
                    2 -> current.copy(tmdbId = value)
                    else -> current
                }
            }
        }
        return result
    }

    private fun SQLiteDatabase.readPosterBySeriesId(): Map<Long, String> {
        val result = linkedMapOf<Long, String>()
        rawQuery(
            """
            SELECT series_id, path
            FROM series_artwork
            WHERE artwork_kind = 1
            ORDER BY id ASC
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val seriesId = cursor.long("series_id")
                val path = normalizeMlipArtworkPath(cursor.string("path")) ?: continue
                result.putIfAbsent(seriesId, path)
            }
        }
        return result
    }

    private suspend fun cacheArtwork(
        mediaSource: MediaSource,
        artworkPath: String,
        posterCacheDirectory: File?,
        sourceId: Long,
        seriesUuid: String,
    ): String? {
        val cacheRoot = posterCacheDirectory ?: return null
        val outputDir = File(cacheRoot, "mlip/$sourceId").apply { mkdirs() }
        val extension = artworkPath.substringBefore('?').substringAfterLast('.', "jpg")
            .lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
            ?: "jpg"
        val output = File(outputDir, "${stableHash("$seriesUuid:$artworkPath")}.$extension")
        if (output.length() > 0L) return output.absolutePath
        val stream = mediaSource.openStream(artworkPath).getOrNull() ?: return null
        return runCatching {
            val temp = File(output.parentFile, "${output.name}.tmp")
            stream.use { input -> temp.outputStream().use { outputStream -> input.copyTo(outputStream) } }
            if (!temp.renameTo(output)) {
                temp.copyTo(output, overwrite = true)
                temp.delete()
            }
            output.absolutePath
        }.getOrNull()
    }

    private fun Anime.displayTitleForIndex(): String = titleCn?.takeIf { it.isNotBlank() } ?: title

    private fun Double.toIntegerEpisodeNumber(): Int? {
        if (!isFinite()) return null
        val intValue = toInt()
        return if (this == intValue.toDouble()) intValue else null
    }

    private fun mlipAnimeId(sourceId: Long, seriesUuid: String): String = "mlip:$sourceId:$seriesUuid"

    private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        private const val TAG = "MlipLibraryIndexImporter"
    }
}

data class MlipImportResult(
    val seriesCount: Int,
    val episodeCount: Int,
    val mediaFileCount: Int,
    val skippedFileCount: Int,
    val artworkCachedCount: Int,
    val nonIntegerEpisodeCount: Int,
    val extraCount: Int,
)

internal fun normalizeMlipMediaPath(path: String): String? {
    val trimmed = path.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }
    val normalized = normalizeMlipRelativePath(trimmed) ?: return null
    return "/${normalized.trimStart('/')}"
}

internal fun normalizeMlipArtworkPath(path: String): String? {
    val trimmed = path.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }
    return normalizeMlipRelativePath(path)?.let { "/${it.trimStart('/')}" }
}

private fun normalizeMlipRelativePath(path: String): String? {
    val segments = path.replace('\\', '/')
        .trim()
        .trimStart('/')
        .split('/')
        .filter { it.isNotBlank() }
    if (segments.isEmpty()) return null
    if (segments.any { it == "." || it == ".." || "://" in it }) return null
    return segments.joinToString("/")
}

private val v2RequiredTables = setOf("series_release_date", "media_subtitle")
private val v3RequiredTables = setOf("media_extra")

private val requiredTables = setOf(
    "meta",
    "series",
    "episode",
    "media_file",
    "series_artwork",
    "episode_artwork",
    "genre",
    "series_genre",
    "series_external_id",
    "episode_external_id",
    "capability",
)

private data class MlipSnapshot(
    val series: List<MlipSeries>,
    val mediaFiles: List<MlipMediaFile>,
    val extras: List<MediaIndexEntry>,
    val skippedFiles: Int,
    val nonIntegerEpisodes: Int,
)

private data class MlipSeries(
    val id: Long,
    val uuid: String,
    val anime: Anime,
    val posterPath: String?,
)

private data class MlipMediaFile(
    val episodeId: Long,
    val seriesId: Long,
    val indexEntry: MediaIndexEntry,
    val episode: Episode,
)

private fun MediaIndexEntry.mlipAnimeIdOrNull(): String? =
    metadataId
        ?.takeIf { metadataSource.equals(MLIP_METADATA_SOURCE, ignoreCase = true) && it.startsWith("mlip:") }
        ?: localMetadataOverrideKey()?.takeIf { it.startsWith("mlip:") }

private data class MlipExternalIds(
    val bangumiId: Int? = null,
    val tmdbId: Int? = null,
)

private class InvalidMlipSchemaException(val reason: String) : RuntimeException(reason)

private fun SQLiteDatabase.tableNames(): Set<String> = rawQuery(
    "SELECT name FROM sqlite_master WHERE type = 'table'",
    emptyArray(),
).use { cursor ->
    buildSet {
        while (cursor.moveToNext()) add(cursor.getString(0))
    }
}

private fun SQLiteDatabase.singleInt(sql: String, vararg args: String): Int? = rawQuery(sql, args).use { cursor ->
    if (cursor.moveToFirst()) cursor.getInt(0) else null
}

private fun SQLiteDatabase.singleString(sql: String, vararg args: String): String? = rawQuery(sql, args).use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
}

private fun Cursor.index(name: String): Int = getColumnIndexOrThrow(name)
private fun Cursor.string(name: String): String = getString(index(name)).orEmpty()
private fun Cursor.stringOrNull(name: String): String? = if (isNull(index(name))) null else getString(index(name))
private fun Cursor.long(name: String): Long = getLong(index(name))
private fun Cursor.longOrNull(name: String): Long? = if (isNull(index(name))) null else getLong(index(name))
private fun Cursor.int(name: String): Int = getInt(index(name))
private fun Cursor.intOrNull(name: String): Int? = if (isNull(index(name))) null else getInt(index(name))
private fun Cursor.double(name: String): Double = getDouble(index(name))

private fun Long?.toMlipEpochMillis(): Long {
    val value = this?.takeIf { it > 0L } ?: return 0L
    return if (value < 100_000_000_000L) value * 1000L else value
}
