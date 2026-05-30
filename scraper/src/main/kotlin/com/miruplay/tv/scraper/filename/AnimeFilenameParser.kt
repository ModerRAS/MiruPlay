package com.miruplay.tv.scraper.filename

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult
import com.miruplay.tv.model.sanitizeRecognizedText
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
    private val maxLengthDelegate = lazy { readConfiguredMaxLength() }
    private val tokenizer by lazy { AnimeFilenameTokenizer(vocabDelegate.value) }
    private val session by sessionDelegate
    private val id2Label by labelsDelegate
    private val graphMaxLength by maxLengthDelegate

    override fun parse(filename: String, maxLength: Int): ParsedAnimeFilename {
        val tokens = tokenizer.tokenize(filename)
        if (tokens.isEmpty()) return ParsedAnimeFilename()

        val graphLength = graphMaxLength
        val requestedLength = maxLength.takeIf { it > 0 } ?: graphLength
        val sequenceLength = requestedLength.coerceAtMost(graphLength)
        val available = minOf(tokens.size, sequenceLength - 2)
        if (available <= 0) return ParsedAnimeFilename()

        val inputIds = LongArray(graphLength) { tokenizer.padTokenId.toLong() }
        val attentionMask = LongArray(graphLength)
        inputIds[0] = tokenizer.clsTokenId.toLong()
        attentionMask[0] = 1L

        for (index in 0 until available) {
            inputIds[index + 1] = tokenizer.tokenToId(tokens[index]).toLong()
            attentionMask[index + 1] = 1L
        }

        val sepIndex = available + 1
        if (sepIndex < graphLength) {
            inputIds[sepIndex] = tokenizer.sepTokenId.toLong()
            attentionMask[sepIndex] = 1L
        }

        OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), longArrayOf(1L, graphLength.toLong())).use { idsTensor ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), longArrayOf(1L, graphLength.toLong())).use { maskTensor ->
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
        val emissions = (0 until tokenCount).map { tokenIndex ->
            batch.getOrNull(tokenIndex + 1) as? FloatArray ?: FloatArray(id2Label.keys.maxOrNull()?.plus(1) ?: 1)
        }
        return constrainedBioDecode(emissions)
    }

    private fun constrainedBioDecode(emissions: List<FloatArray>): List<Int> {
        if (emissions.isEmpty()) return emptyList()
        val labelCount = emissions.maxOf { it.size }
        val backpointers = Array(emissions.size) { IntArray(labelCount) }
        var scores = FloatArray(labelCount) { Float.NEGATIVE_INFINITY }

        for (labelId in 0 until labelCount) {
            val label = id2Label[labelId] ?: "O"
            if (!label.startsWith("I-")) {
                scores[labelId] = emissions[0].getOrElse(labelId) { Float.NEGATIVE_INFINITY }
            }
        }

        for (index in 1 until emissions.size) {
            val nextScores = FloatArray(labelCount) { Float.NEGATIVE_INFINITY }
            for (labelId in 0 until labelCount) {
                val label = id2Label[labelId] ?: "O"
                var bestScore = Float.NEGATIVE_INFINITY
                var bestPrevious = 0
                for (previousId in 0 until labelCount) {
                    val previousLabel = id2Label[previousId] ?: "O"
                    if (!isAllowedBioTransition(previousLabel, label)) continue
                    val candidate = scores[previousId]
                    if (candidate > bestScore) {
                        bestScore = candidate
                        bestPrevious = previousId
                    }
                }
                nextScores[labelId] = bestScore + emissions[index].getOrElse(labelId) { Float.NEGATIVE_INFINITY }
                backpointers[index][labelId] = bestPrevious
            }
            scores = nextScores
        }

        val decoded = IntArray(emissions.size)
        decoded[decoded.lastIndex] = scores.indices.maxByOrNull { scores[it] } ?: 0
        for (index in emissions.lastIndex downTo 1) {
            decoded[index - 1] = backpointers[index][decoded[index]]
        }
        return decoded.toList()
    }

    private fun isAllowedBioTransition(previousLabel: String, label: String): Boolean {
        if (!label.startsWith("I-")) return true
        val entity = label.removePrefix("I-")
        return previousLabel == "B-$entity" || previousLabel == "I-$entity"
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
            .map { it.second.normalizeFieldText() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .ifBlank { null }

        var season: Int? = null
        var episode: Int? = null
        var group: String? = null
        var resolution: String? = null
        val sourceCandidates = mutableListOf<String>()
        var special: String? = null

        entities.forEach { (type, text) ->
            when (type) {
                "SEASON" -> extractNumber(text)?.let { season = it }
                "EPISODE" -> if (episode == null) episode = extractNumber(text)
                "GROUP" -> if (group == null) group = text.normalizeFieldText().ifBlank { null }
                "RESOLUTION" -> resolution = text.trimDecorations().ifBlank { null }
                "SOURCE" -> sourceCandidates += text
                "SPECIAL" -> special = text.normalizeFieldText().ifBlank { null }
            }
        }

        return ParsedAnimeFilename(
            title = title,
            season = season,
            episode = episode,
            group = group,
            resolution = resolution,
            source = chooseThinSource(sourceCandidates),
            special = special,
        ).sanitizeRecognizedText()
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

    private fun readConfiguredMaxLength(): Int {
        val config = json.parseToJsonElement(readAssetText(CONFIG_ASSET)).jsonObject
        return config["max_seq_length"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: config["max_position_embeddings"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: MAX_LENGTH
    }

    private fun readAsset(path: String): ByteArray =
        appContext.assets.open(path).use { it.readBytes() }

    private fun readAssetText(path: String): String =
        appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun String.trimDecorations(): String =
        trim().trim('[', ']', '(', ')', '【', '】', '《', '》', '（', '）')

    private fun String.normalizeFieldText(): String =
        trimDecorations().trim(' ', '\t', '-', '_', '.', '/', '\\')

    private fun chooseThinSource(sources: List<String>): String? {
        val cleaned = sources
            .map { it.normalizeFieldText() }
            .filter { it.isNotBlank() }
            .map { normalizeSourceText(it) }
        if (cleaned.isEmpty()) return null
        return cleaned.maxWith(compareBy<String> { thinSourcePriority(it) })
    }

    private fun thinSourcePriority(source: String): Int {
        val normalized = source.lowercase()
            .replace("_", "-")
            .replace(" ", "")
        if (normalized in highPrioritySources) return 90
        if (normalized in languageSources) return 70
        if (normalized in codecSources) return 20
        return if (source.any { it in "&+/, " }) 40 else 30
    }

    private fun normalizeSourceText(text: String): String {
        return text.replace(Regex("""\s+"""), "")
            .replace(Regex("""(?i)WEB[_ ]?DL"""), "WEB-DL")
            .replace(Regex("""(?i)WEB[_ ]?Rip"""), "WebRip")
            .replace(Regex("""(?i)U[_ ]?NEXT"""), "U-NEXT")
            .replace(Regex("""(?i)AT[_ ]?X"""), "AT-X")
            .replace("_", "-")
    }

    private class AnimeFilenameTokenizer(private val vocab: Map<String, Int>) {
        val padTokenId: Int = vocab.getValue("[PAD]")
        val unkTokenId: Int = vocab.getValue("[UNK]")
        val clsTokenId: Int = vocab.getValue("[CLS]")
        val sepTokenId: Int = vocab.getValue("[SEP]")

        fun tokenize(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            return text.map { it.toString() }
        }

        fun tokenToId(token: String): Int = vocab[token] ?: unkTokenId
    }

    private companion object {
        private const val MAX_LENGTH = 128
        private const val MODEL_ASSET = "anime_parser/anime_filename_parser.onnx"
        private const val VOCAB_ASSET = "anime_parser/vocab.json"
        private const val CONFIG_ASSET = "anime_parser/config.json"

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

        private val highPrioritySources = setOf(
            "nf",
            "netflix",
            "amzn",
            "baha",
            "cr",
            "abema",
            "dsnp",
            "u-next",
            "hulu",
            "at-x",
            "web-dl",
            "webdl",
            "webrip",
            "web-rip",
            "bdrip",
            "bluray",
            "bdmv",
            "bd",
            "dvdrip",
            "dvd",
            "tvrip",
            "hdtv",
        )
        private val languageSources = setOf(
            "chs",
            "cht",
            "gb",
            "big5",
            "jpn",
            "jp",
            "jpsc",
            "jptc",
            "繁中",
            "简中",
        )
        private val codecSources = setOf(
            "x264",
            "x265",
            "h.264",
            "h264",
            "h.265",
            "h265",
            "hevc",
            "avc",
            "av1",
            "aac",
            "flac",
            "mp3",
            "dts",
            "opus",
            "10bit",
            "8bit",
            "hi10p",
            "ma10p",
            "srt",
            "srtx2",
            "ass",
            "assx2",
        )
    }
}
