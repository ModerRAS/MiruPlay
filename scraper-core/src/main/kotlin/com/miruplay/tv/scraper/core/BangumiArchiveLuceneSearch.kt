package com.miruplay.tv.scraper.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.cjk.CJKBigramFilter
import org.apache.lucene.analysis.cjk.CJKWidthFilter
import org.apache.lucene.analysis.icu.ICUFoldingFilter
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.PhraseQuery
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal class BangumiArchiveLuceneSearch(
    private val subjectFileProvider: () -> File,
    private val normalizeQuery: (String) -> String,
) {
    private val mapper = BangumiArchiveDocumentMapper(normalizeQuery)
    private val index = BangumiArchiveLuceneIndex(subjectFileProvider, mapper)

    fun search(query: String, limit: Int): List<ArchiveHit> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()

        val subjectId = trimmedQuery.takeIf { it.all(Char::isDigit) }?.toIntOrNull()
        if (subjectId != null) {
            return index.findById(subjectId)?.let { subject ->
                listOf(
                    ArchiveHit(
                        subject = subject,
                        matchedTitle = mapper.matchedTitle(subject, trimmedQuery),
                        confidence = 1.0f,
                        luceneScore = Float.MAX_VALUE,
                    )
                )
            }.orEmpty()
        }

        val normalizedQuery = normalizeArchiveIndexedText(trimmedQuery, normalizeQuery)
        if (normalizedQuery.isBlank()) return emptyList()
        val seasonlessQuery = normalizedQuery.toSeasonlessArchiveText()
        val requestedSeason = extractArchiveSeasonNumber(trimmedQuery) ?: extractArchiveSeasonNumber(normalizedQuery)

        return index.search(buildQuery(normalizedQuery, seasonlessQuery), limit) { document, score ->
            val subject = mapper.toSubject(document)
            val titleMatch = mapper.matchSubject(subject, trimmedQuery)
            ArchiveHit(
                subject = subject,
                matchedTitle = titleMatch.title,
                confidence = titleMatch.confidence.coerceIn(0f, 1f),
                luceneScore = score,
            )
        }.adjustSeasonalConfidence(requestedSeason)
    }

    fun findById(subjectId: String): BangumiArchiveSubject? =
        subjectId.toIntOrNull()?.let(index::findById)

    private fun buildQuery(
        normalizedQuery: String,
        seasonlessQuery: String,
    ): Query {
        val builder = BooleanQuery.Builder()
        builder.add(BoostQuery(TermQuery(Term(BangumiArchiveLuceneFields.ALL_TITLES_EXACT, normalizedQuery)), 9.0f), BooleanClause.Occur.SHOULD)
        if (normalizedQuery.length >= 2) {
            builder.add(BoostQuery(PrefixQuery(Term(BangumiArchiveLuceneFields.ALL_TITLES_EXACT, normalizedQuery)), 7.0f), BooleanClause.Occur.SHOULD)
        }
        if (seasonlessQuery.isNotBlank()) {
            builder.add(BoostQuery(TermQuery(Term(BangumiArchiveLuceneFields.ALL_TITLES_SEASONLESS, seasonlessQuery)), 5.5f), BooleanClause.Occur.SHOULD)
            if (seasonlessQuery.length >= 2) {
                builder.add(
                    BoostQuery(PrefixQuery(Term(BangumiArchiveLuceneFields.ALL_TITLES_SEASONLESS, seasonlessQuery)), 4.0f),
                    BooleanClause.Occur.SHOULD,
                )
            }
        }

        addTextFieldClauses(builder, BangumiArchiveLuceneFields.TITLE_CN, normalizedQuery, 4.4f, 0.75f)
        addTextFieldClauses(builder, BangumiArchiveLuceneFields.TITLE, normalizedQuery, 4.0f, 0.7f)
        addTextFieldClauses(builder, BangumiArchiveLuceneFields.ALIASES, normalizedQuery, 4.8f, 0.8f)
        addTextFieldClauses(builder, BangumiArchiveLuceneFields.ALL_TITLES, normalizedQuery, 3.0f, 0.45f)

        return builder.build().takeIf { it.clauses().isNotEmpty() }
            ?: TermQuery(Term(BangumiArchiveLuceneFields.ALL_TITLES_EXACT, normalizedQuery))
    }

    private fun addTextFieldClauses(
        builder: BooleanQuery.Builder,
        fieldName: String,
        normalizedQuery: String,
        phraseBoost: Float,
        termBoost: Float,
    ) {
        val tokens = index.queryTokens(fieldName, normalizedQuery)
        if (tokens.isEmpty()) return
        val fieldTerms = BooleanQuery.Builder()
        tokens.distinct().forEach { token ->
            fieldTerms.add(BoostQuery(TermQuery(Term(fieldName, token)), termBoost), BooleanClause.Occur.SHOULD)
        }
        builder.add(BoostQuery(fieldTerms.build(), phraseBoost * 0.65f), BooleanClause.Occur.SHOULD)
        if (tokens.size >= 2) {
            val phraseQuery = PhraseQuery.Builder().apply {
                tokens.forEachIndexed { position, token ->
                    add(Term(fieldName, token), position)
                }
            }.build()
            builder.add(BoostQuery(phraseQuery, phraseBoost), BooleanClause.Occur.SHOULD)
        }
    }
}

private fun List<ArchiveHit>.adjustSeasonalConfidence(requestedSeason: Int?): List<ArchiveHit> {
    val season = requestedSeason ?: return this
    val hasExplicitSeasonHit = any { hit ->
        hit.subject.hasSeason(season) && hit.confidence >= 0.9f
    }
    if (!hasExplicitSeasonHit) return this

    return map { hit ->
        when {
            hit.subject.hasSeason(season) -> hit
            hit.subject.hasAnySeason() -> hit.copy(confidence = minOf(hit.confidence, 0.48f))
            else -> hit.copy(confidence = minOf(hit.confidence, 0.58f))
        }
    }
}

private fun BangumiArchiveSubject.hasSeason(season: Int): Boolean =
    archiveTitleVariants().any { extractArchiveSeasonNumber(it) == season }

private fun BangumiArchiveSubject.hasAnySeason(): Boolean =
    archiveTitleVariants().any { extractArchiveSeasonNumber(it) != null }

private fun BangumiArchiveSubject.archiveTitleVariants(): List<String> =
    buildList {
        add(name)
        nameCn?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(aliases)
    }.map(String::trim).filter(String::isNotBlank).distinct()

internal data class ArchiveHit(
    val subject: BangumiArchiveSubject,
    val matchedTitle: String,
    val confidence: Float,
    val luceneScore: Float,
)

private class BangumiArchiveLuceneIndex(
    private val subjectFileProvider: () -> File,
    private val mapper: BangumiArchiveDocumentMapper,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Any()

    fun findById(subjectId: Int): BangumiArchiveSubject? = synchronized(lock) {
        val subjectFile = ensureIndexCurrent() ?: return null
        withSearcher(subjectFile.parentFile.toPath().resolve(LUCENE_DIRECTORY_NAME)) { searcher ->
            searcher.search(TermQuery(Term(BangumiArchiveLuceneFields.ID, subjectId.toString())), 1)
                .scoreDocs
                .firstOrNull()
                ?.let { searcher.doc(it.doc) }
                ?.let(mapper::toSubject)
        }
    }

    fun search(
        query: Query,
        limit: Int,
        mapHit: (Document, Float) -> ArchiveHit,
    ): List<ArchiveHit> = synchronized(lock) {
        val subjectFile = ensureIndexCurrent() ?: return emptyList()
        withSearcher(subjectFile.parentFile.toPath().resolve(LUCENE_DIRECTORY_NAME)) { searcher ->
            val hits = trySearch(searcher, query, limit.coerceAtLeast(1))
            hits.scoreDocs
                .map { hit -> mapHit(searcher.doc(hit.doc), hit.score) }
                .sortedWith(
                    compareByDescending<ArchiveHit> { it.luceneScore }
                        .thenBy { it.subject.rank ?: Int.MAX_VALUE }
                        .thenByDescending { it.subject.score ?: 0f }
                        .thenByDescending { it.subject.date.orEmpty() }
                )
                .take(limit.coerceAtLeast(1))
        } ?: emptyList()
    }

    fun queryTokens(fieldName: String, query: String): List<String> =
        createAnalyzer().use { analyzer ->
            analyzer.tokenStream(fieldName, query).useTokens()
        }

    private fun ensureIndexCurrent(): File? {
        val subjectFile = subjectFileProvider()
        if (!subjectFile.isFile) return null

        val luceneDirectory = File(subjectFile.parentFile, LUCENE_DIRECTORY_NAME)
        val metadataFile = File(luceneDirectory, INDEX_METADATA_FILE_NAME)
        val targetMetadata = IndexMetadata(
            subjectFilePath = subjectFile.absolutePath,
            subjectFileLastModified = subjectFile.lastModified(),
            subjectFileLength = subjectFile.length(),
            schemaVersion = SCHEMA_VERSION,
        )

        val currentMetadata = metadataFile.takeIf { it.isFile }?.readText()?.let { text ->
            runCatching { json.decodeFromString<IndexMetadata>(text) }.getOrNull()
        }

        val needsRebuild = currentMetadata != targetMetadata || !luceneDirectory.isDirectory || !hasLuceneIndexFiles(luceneDirectory.toPath())
        if (needsRebuild) {
            rebuildIndex(subjectFile, luceneDirectory, metadataFile, targetMetadata)
        }

        if (!canOpenIndex(luceneDirectory.toPath())) {
            rebuildIndex(subjectFile, luceneDirectory, metadataFile, targetMetadata)
            if (!canOpenIndex(luceneDirectory.toPath())) return null
        }
        return subjectFile
    }

    private fun rebuildIndex(
        subjectFile: File,
        luceneDirectory: File,
        metadataFile: File,
        metadata: IndexMetadata,
    ) {
        resetLuceneDirectory(luceneDirectory)
        luceneDirectory.mkdirs()
        writeIndex(subjectFile, luceneDirectory.toPath())
        metadataFile.writeText(json.encodeToString(metadata))
    }

    private fun writeIndex(subjectFile: File, luceneDirectory: Path) {
        createAnalyzer().use { analyzer ->
            FSDirectory.open(luceneDirectory).use { directory ->
                IndexWriter(directory, IndexWriterConfig(analyzer).apply {
                    openMode = IndexWriterConfig.OpenMode.CREATE
                }).use { writer ->
                    subjectFile.useLines { lines ->
                        lines.mapNotNull(mapper::parseSubject)
                            .forEach { subject ->
                                writer.addDocument(mapper.toDocument(subject))
                            }
                    }
                    writer.commit()
                }
            }
        }
    }

    private fun resetLuceneDirectory(luceneDirectory: File) {
        if (luceneDirectory.exists() && luceneDirectory.isFile) {
            luceneDirectory.delete()
        }
        if (luceneDirectory.isDirectory) {
            luceneDirectory.deleteRecursively()
        }
    }

    private fun hasLuceneIndexFiles(directory: Path): Boolean =
        runCatching {
            directory.toFile().listFiles().orEmpty().any { file ->
                file.isFile && file.name != INDEX_METADATA_FILE_NAME
            }
        }.getOrDefault(false)

    private fun canOpenIndex(directory: Path): Boolean =
        withSearcher(directory) { true } ?: false

    private fun <T> withSearcher(
        directory: Path,
        block: (IndexSearcher) -> T,
    ): T? {
        if (!directory.exists() || !directory.isDirectory()) return null
        return try {
            FSDirectory.open(directory).use { fsDirectory ->
                DirectoryReader.open(fsDirectory).use { reader ->
                    block(IndexSearcher(reader))
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun trySearch(
        searcher: IndexSearcher,
        query: Query,
        limit: Int,
    ): TopDocs =
        try {
            searcher.search(query, limit)
        } catch (_: IOException) {
            searcher.search(query, limit)
        }

    private fun createAnalyzer(): Analyzer =
        PerFieldAnalyzerWrapper(
            BangumiArchiveAnalyzer(),
            mapOf(
                BangumiArchiveLuceneFields.ALL_TITLES_EXACT to KeywordAnalyzer(),
                BangumiArchiveLuceneFields.ALL_TITLES_SEASONLESS to KeywordAnalyzer(),
            ),
        )

    @Serializable
    private data class IndexMetadata(
        val subjectFilePath: String,
        val subjectFileLastModified: Long,
        val subjectFileLength: Long,
        val schemaVersion: Int,
    )

    private companion object {
        private const val SCHEMA_VERSION = 2
        private const val LUCENE_DIRECTORY_NAME = "lucene-v1"
        private const val INDEX_METADATA_FILE_NAME = "index-metadata.json"
    }
}

private class BangumiArchiveAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer = ICUTokenizer()
        var stream: TokenStream = tokenizer
        stream = CJKWidthFilter(stream)
        stream = LowerCaseFilter(stream)
        stream = ICUFoldingFilter(stream)
        stream = CJKBigramFilter(
            stream,
            CJKBigramFilter.HAN or CJKBigramFilter.HIRAGANA or CJKBigramFilter.KATAKANA or CJKBigramFilter.HANGUL,
            true,
        )
        return TokenStreamComponents(tokenizer, stream)
    }
}

private fun TokenStream.useTokens(): List<String> {
    val terms = mutableListOf<String>()
    addAttribute(CharTermAttribute::class.java)
    reset()
    while (incrementToken()) {
        val term = getAttribute(CharTermAttribute::class.java).toString()
        if (term.isNotBlank()) {
            terms += term
        }
    }
    end()
    close()
    return terms
}
