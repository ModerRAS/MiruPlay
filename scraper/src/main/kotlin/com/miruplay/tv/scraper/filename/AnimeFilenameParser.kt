package com.miruplay.tv.scraper.filename

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

typealias ParsedAnimeFilename = FilenameParseResult

@Singleton
class AnimeFilenameParser @Inject constructor(
    @ApplicationContext context: Context,
) : FilenameMetadataParser, AutoCloseable {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionOptionsDelegate = lazy { OrtSession.SessionOptions() }
    private val sessionDelegate = lazy {
        environment.createSession(readAsset(MODEL_ASSET), sessionOptionsDelegate.value)
    }
    private val vocabDelegate = lazy { readVocab() }
    private val labelsDelegate = lazy { readLabels() }
    private val tokenizer by lazy { AnimeFilenameTokenizer(vocabDelegate.value) }
    private val session by sessionDelegate
    private val id2Label by labelsDelegate

    override fun parse(filename: String, maxLength: Int): ParsedAnimeFilename {
        val tokens = tokenizer.tokenize(filename)
        if (tokens.isEmpty()) return ParsedAnimeFilename()

        val available = minOf(tokens.size, maxLength - 2)
        if (available <= 0) return ParsedAnimeFilename()

        val inputIds = LongArray(maxLength) { tokenizer.padTokenId.toLong() }
        val attentionMask = LongArray(maxLength)
        inputIds[0] = tokenizer.clsTokenId.toLong()
        attentionMask[0] = 1L

        for (index in 0 until available) {
            inputIds[index + 1] = tokenizer.tokenToId(tokens[index]).toLong()
            attentionMask[index + 1] = 1L
        }

        val sepIndex = available + 1
        if (sepIndex < maxLength) {
            inputIds[sepIndex] = tokenizer.sepTokenId.toLong()
            attentionMask[sepIndex] = 1L
        }

        OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), longArrayOf(1L, maxLength.toLong())).use { idsTensor ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), longArrayOf(1L, maxLength.toLong())).use { maskTensor ->
                val inputs = mapOf(
                    "input_ids" to idsTensor,
                    "attention_mask" to maskTensor,
                )
                session.run(inputs).use { output ->
                    val labelIds = decodeLabelIds(output[0].value, available)
                    val labels = labelIds.map { id2Label[it] ?: "O" }
                    return postprocess(tokens.take(available), labels)
                }
            }
        }
    }

    override fun close() {
        if (sessionDelegate.isInitialized()) sessionDelegate.value.close()
        if (sessionOptionsDelegate.isInitialized()) sessionOptionsDelegate.value.close()
    }

    private fun decodeLabelIds(value: Any?, tokenCount: Int): List<Int> {
        val batch = (value as? Array<*>)?.firstOrNull() as? Array<*> ?: return List(tokenCount) { 0 }
        return (0 until tokenCount).map { tokenIndex ->
            val logits = batch.getOrNull(tokenIndex + 1) as? FloatArray ?: return@map 0
            logits.indices.maxByOrNull { logits[it] } ?: 0
        }
    }

    private fun postprocess(tokens: List<String>, labels: List<String>): ParsedAnimeFilename {
        val entities = mutableListOf<Pair<String, String>>()
        var currentEntity: String? = null
        val currentTokens = mutableListOf<String>()

        fun flush() {
            val entity = currentEntity ?: return
            entities += entity to currentTokens.joinToString(separator = "")
            currentEntity = null
            currentTokens.clear()
        }

        tokens.zip(labels).forEach { (token, label) ->
            when {
                label.startsWith("B-") -> {
                    flush()
                    currentEntity = label.removePrefix("B-")
                    currentTokens += token
                }
                label.startsWith("I-") && currentEntity == label.removePrefix("I-") -> {
                    currentTokens += token
                }
                label.startsWith("I-") -> {
                    flush()
                    currentEntity = label.removePrefix("I-")
                    currentTokens += token
                }
                else -> flush()
            }
        }
        flush()

        val title = entities
            .filter { it.first == "TITLE" }
            .map { it.second.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .ifBlank { null }

        var season: Int? = null
        var episode: Int? = null
        var group: String? = null
        var resolution: String? = null
        var source: String? = null
        var special: String? = null

        entities.forEach { (type, text) ->
            when (type) {
                "SEASON" -> extractNumber(text)?.let { season = it }
                "EPISODE" -> if (episode == null) episode = extractNumber(text)
                "GROUP" -> if (group == null) group = text.trimDecorations().ifBlank { null }
                "RESOLUTION" -> resolution = text.trimDecorations().ifBlank { null }
                "SOURCE" -> source = text.trimDecorations().ifBlank { null }
                "SPECIAL" -> special = text.trimDecorations().ifBlank { null }
            }
        }

        return ParsedAnimeFilename(
            title = title,
            season = season,
            episode = episode,
            group = group,
            resolution = resolution,
            source = source,
            special = special,
        )
    }

    private fun extractNumber(text: String): Int? {
        numberRegex.find(text)?.value?.toIntOrNull()?.let { return it }
        chineseNumbers.forEach { (char, value) ->
            if (text.contains(char)) return value
        }
        return null
    }

    private fun readVocab(): Map<String, Int> =
        json.decodeFromString<Map<String, Int>>(readAssetText(VOCAB_ASSET))

    private fun readLabels(): Map<Int, String> {
        val config = json.parseToJsonElement(readAssetText(CONFIG_ASSET)).jsonObject
        val id2Label = config["id2label"]?.jsonObject ?: return defaultLabels
        return id2Label.mapNotNull { (id, labelElement) ->
            val label = labelElement.jsonPrimitive.contentOrNull ?: return@mapNotNull null
            id.toIntOrNull()?.let { it to label }
        }.toMap().ifEmpty { defaultLabels }
    }

    private fun readAsset(path: String): ByteArray =
        appContext.assets.open(path).use { it.readBytes() }

    private fun readAssetText(path: String): String =
        appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun String.trimDecorations(): String =
        trim().trim('[', ']', '(', ')', '【', '】', '《', '》')

    private class AnimeFilenameTokenizer(private val vocab: Map<String, Int>) {
        val padTokenId: Int = vocab.getValue("[PAD]")
        val unkTokenId: Int = vocab.getValue("[UNK]")
        val clsTokenId: Int = vocab.getValue("[CLS]")
        val sepTokenId: Int = vocab.getValue("[SEP]")

        fun tokenize(text: String): List<String> {
            if (text.isBlank()) return emptyList()

            val placeholders = mutableListOf<String>()
            var processed = protect(text, bracketRegex, placeholders)
            processed = protect(processed, compositeFormatRegex, placeholders)
            processed = protect(processed, formatRegex, placeholders)

            val result = mutableListOf<String>()
            splitSeparators(processed).forEach { part ->
                when {
                    part.isEmpty() -> Unit
                    part.length == 1 && part[0] in separators -> result += part
                    part.indexOf(placeholderMarker) >= 0 -> appendPlaceholderPart(part, placeholders, result)
                    else -> result += splitFragment(part)
                }
            }
            return result
        }

        fun tokenToId(token: String): Int = vocab[token] ?: unkTokenId

        private fun protect(text: String, regex: Regex, placeholders: MutableList<String>): String {
            val builder = StringBuilder()
            var lastEnd = 0
            regex.findAll(text).forEach { match ->
                builder.append(text, lastEnd, match.range.first)
                val index = placeholders.size
                placeholders += match.value
                builder.append(placeholderMarker).append(index).append(placeholderMarker)
                lastEnd = match.range.last + 1
            }
            builder.append(text, lastEnd, text.length)
            return builder.toString()
        }

        private fun splitSeparators(text: String): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            text.forEach { char ->
                if (char in separators) {
                    if (current.isNotEmpty()) {
                        parts += current.toString()
                        current.clear()
                    }
                    parts += char.toString()
                } else {
                    current.append(char)
                }
            }
            if (current.isNotEmpty()) parts += current.toString()
            return parts
        }

        private fun appendPlaceholderPart(part: String, placeholders: List<String>, result: MutableList<String>) {
            var lastEnd = 0
            placeholderRegex.findAll(part).forEach { match ->
                if (match.range.first > lastEnd) {
                    result += splitFragment(part.substring(lastEnd, match.range.first))
                }
                val index = match.groupValues[1].toIntOrNull()
                val placeholder = index?.let { placeholders.getOrNull(it) }
                if (placeholder != null) result += placeholder
                lastEnd = match.range.last + 1
            }
            if (lastEnd < part.length) {
                result += splitFragment(part.substring(lastEnd))
            }
        }

        private fun splitFragment(fragment: String): List<String> {
            val tokens = mutableListOf<String>()
            var index = 0
            while (index < fragment.length) {
                val char = fragment[index]
                when {
                    char.isCjkOrKana() -> {
                        tokens += char.toString()
                        index += 1
                    }
                    char.isAsciiLetter() -> {
                        val start = index
                        while (index < fragment.length && fragment[index].isAsciiLetter()) index += 1
                        tokens += fragment.substring(start, index)
                    }
                    char.isDigit() -> {
                        val start = index
                        while (index < fragment.length && fragment[index].isDigit()) index += 1
                        tokens += fragment.substring(start, index)
                    }
                    else -> {
                        tokens += char.toString()
                        index += 1
                    }
                }
            }
            return tokens
        }

        private fun Char.isAsciiLetter(): Boolean = code in 0..127 && isLetter()

        private fun Char.isCjkOrKana(): Boolean =
            code in 0x4E00..0x9FFF ||
                code in 0x3400..0x4DBF ||
                code in 0x3040..0x309F ||
                code in 0x30A0..0x30FF
    }

    private companion object {
        private const val MAX_LENGTH = 64
        private const val MODEL_ASSET = "anime_parser/anime_filename_parser.onnx"
        private const val VOCAB_ASSET = "anime_parser/vocab.json"
        private const val CONFIG_ASSET = "anime_parser/config.json"
        private const val placeholderMarker = '\u0000'

        private val numberRegex = Regex("""(\d+)""")
        private val chineseNumbers = mapOf(
            '一' to 1,
            '二' to 2,
            '三' to 3,
            '四' to 4,
            '五' to 5,
            '六' to 6,
            '七' to 7,
            '八' to 8,
            '九' to 9,
            '十' to 10,
        )
        private val defaultLabels = mapOf(
            0 to "O",
            1 to "B-TITLE",
            2 to "I-TITLE",
            3 to "B-SEASON",
            4 to "I-SEASON",
            5 to "B-EPISODE",
            6 to "I-EPISODE",
            7 to "B-SPECIAL",
            8 to "I-SPECIAL",
            9 to "B-GROUP",
            10 to "I-GROUP",
            11 to "B-RESOLUTION",
            12 to "I-RESOLUTION",
            13 to "B-SOURCE",
            14 to "I-SOURCE",
        )

        private val separators = setOf(' ', '-', '_', '|', '～', '~', '.')
        private val placeholderRegex = Regex("$placeholderMarker(\\d+)$placeholderMarker")
        private val bracketRegex = Regex("""\[[^\]]*\]|\([^)]*\)|【[^】]*】|《[^》]*》""")
        private val compositeFormatRegex = Regex("""[Ss]\d+[Ee]\d+""")
        private val formatRegex = Regex(
            listOf(
                """\d{3,4}[pP]""",
                """\d{3,4}[xX×]\d{3,4}""",
                """\d[Kk]""",
                """[xX]26[45]""",
                """HEVC""",
                """AVC""",
                """AV1""",
                """[hH]\.?26[45]""",
                """FLAC""",
                """AAC""",
                """MP3""",
                """DTS""",
                """Opus""",
                """Seasons?\s*\d+""",
                """第[一二三四五六七八九十\d]+季""",
                """\d+[sn][dt]\s+Season""",
                """[Ss]\d+""",
                """[Ee][Pp]?\d+""",
                """#\d+""",
                """第\d+[话話]""",
                """\d+[Vv]\d*""",
                """CH[ST]""",
                """简[体體]""",
                """繁[体體]""",
                """JP""",
                """GB""",
                """BIG5""",
                """简日双语""",
                """WEB[-_]?DL""",
                """BDRip""",
                """DVDRip""",
                """TVRip""",
                """Baha""",
                """Netflix""",
                """AMZN""",
                """CR""",
                """WebRip""",
                """\d+:\d+""",
            ).joinToString("|"),
        )
    }
}
