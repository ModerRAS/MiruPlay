package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspPreset

data class RewEqImportResult(
    val preset: AudioDspPreset,
    val importedBandCount: Int,
    val warnings: List<String> = emptyList(),
)

object RewEqParser {
    private const val NUMBER = "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"
    private val filterPattern = Regex(
        """^\s*(?:filter\s*)?(\d+)\s*[:=]?\s*(on|off)\s+([a-z][a-z0-9_-]*)\s+.*?\b(?:fc|freq|frequency)\s*[:=]?\s*($NUMBER)\s*(?:hz)?\b.*?\bgain\s*[:=]?\s*($NUMBER)\s*(?:db)?\b.*?\bq\s*[:=]?\s*($NUMBER)\b.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val preampPattern = Regex(
        """^\s*preamp\s*[:=]?\s*($NUMBER)\s*(?:db)?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val delimitedIndexPattern = Regex("""^\s*(?:filter\s*)?(\d+)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(
        text: String,
        presetId: String = "rew-import",
        presetName: String = "REW import",
        target: AudioDspChannelTarget = AudioDspChannelTarget.ALL,
    ): RewEqImportResult {
        require(text.length <= MAX_TEXT_CHARS) { "REW filter export is too large" }
        require(text.isNotBlank()) { "REW filter export is empty" }

        val warnings = mutableListOf<String>()
        val parsed = mutableListOf<IndexedBand>()
        var preampDb = 0f
        var preampSeen = false
        var table: GenericTable? = null
        var tableRowIndex = 0

        fun addBand(index: Int, band: AudioDspBand, lineNumber: Int) {
            if (parsed.size >= AudioDspChannelRule.MAX_BANDS_PER_RULE) {
                warnings += "line $lineNumber: more than ${AudioDspChannelRule.MAX_BANDS_PER_RULE} filters; remaining rows ignored"
            } else {
                parsed += IndexedBand(index = index, band = band)
            }
        }

        text.lineSequence().forEachIndexed { zeroBasedLine, rawLine ->
            val lineNumber = zeroBasedLine + 1
            val line = rawLine.trim().removeSurrounding("\uFEFF")
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed

            val fields = splitTableFields(line)
            if (fields != null) {
                GenericTable.fromHeader(fields)?.let {
                    table = it
                    tableRowIndex = 0
                    return@forEachIndexed
                }
                table?.let { currentTable ->
                    tableRowIndex += 1
                    val row = currentTable.parse(fields, tableRowIndex)
                    when (row) {
                        null -> Unit
                        is TableRow.Empty -> Unit
                        is TableRow.Invalid -> warnings += "line $lineNumber: ${row.message}"
                        is TableRow.Filter -> {
                            val type = filterType(row.typeCode.uppercase())
                            if (type == null) {
                                warnings += "line $lineNumber: unsupported filter type ${row.typeCode} ignored"
                            } else if (row.frequency !in AudioDspBand.MIN_FREQUENCY_HZ..AudioDspBand.MAX_FREQUENCY_HZ) {
                                warnings += "line $lineNumber: frequency ${row.frequency} Hz is outside supported range"
                            } else if (row.gain !in AudioDspBand.MIN_GAIN_DB..AudioDspBand.MAX_GAIN_DB) {
                                warnings += "line $lineNumber: gain ${row.gain} dB is outside supported range"
                            } else if (row.q !in AudioDspBand.MIN_Q..AudioDspBand.MAX_Q) {
                                warnings += "line $lineNumber: Q ${row.q} is outside supported range"
                            } else {
                                addBand(
                                    index = row.index,
                                    band = AudioDspBand(type, row.frequency.toFloat(), row.gain.toFloat(), row.q.toFloat(), row.enabled),
                                    lineNumber = lineNumber,
                                )
                            }
                        }
                    }
                    return@forEachIndexed
                }
            }

            preampPattern.matchEntire(line)?.let { match ->
                val parsedPreamp = match.groupValues[1].toFloatOrNull()
                if (parsedPreamp == null || !parsedPreamp.isFinite()) {
                    warnings += "line $lineNumber: invalid preamp value"
                } else {
                    if (preampSeen) warnings += "line $lineNumber: duplicate preamp, last value used"
                    if (parsedPreamp !in -24f..12f) {
                        warnings += "line $lineNumber: preamp $parsedPreamp dB is outside supported range and will be clamped"
                    }
                    preampDb = parsedPreamp
                    preampSeen = true
                }
                return@forEachIndexed
            }

            val filterMatch = filterPattern.matchEntire(line)
            val delimited = filterMatch ?: parseDelimited(line)
            if (delimited == null) {
                if (looksLikeFilterRow(line) && !isDelimitedHeader(line)) {
                    warnings += "line $lineNumber: malformed filter row ignored"
                }
                return@forEachIndexed
            }

            val index = delimited.groupValues[1].toInt()
            val enabled = delimited.groupValues[2].equals("on", ignoreCase = true)
            val typeCode = delimited.groupValues[3].uppercase()
            val type = filterType(typeCode)
            if (type == null) {
                warnings += "line $lineNumber: unsupported filter type $typeCode ignored"
                return@forEachIndexed
            }

            val frequency = delimited.groupValues[4].toFloatOrNull()
            val gain = delimited.groupValues[5].toFloatOrNull()
            val q = delimited.groupValues[6].toFloatOrNull()
            if (frequency == null || gain == null || q == null || !frequency.isFinite() || !gain.isFinite() || !q.isFinite()) {
                warnings += "line $lineNumber: non-numeric filter values ignored"
                return@forEachIndexed
            }
            if (frequency !in AudioDspBand.MIN_FREQUENCY_HZ..AudioDspBand.MAX_FREQUENCY_HZ) {
                warnings += "line $lineNumber: frequency $frequency Hz is outside supported range"
                return@forEachIndexed
            }
            if (gain !in AudioDspBand.MIN_GAIN_DB..AudioDspBand.MAX_GAIN_DB) {
                warnings += "line $lineNumber: gain $gain dB is outside supported range"
                return@forEachIndexed
            }
            if (q !in AudioDspBand.MIN_Q..AudioDspBand.MAX_Q) {
                warnings += "line $lineNumber: Q $q is outside supported range"
                return@forEachIndexed
            }
            addBand(
                index = index,
                band = AudioDspBand(type, frequency, gain, q, enabled),
                lineNumber = lineNumber,
            )
        }

        require(parsed.isNotEmpty()) {
            "REW export contains no supported filters"
        }

        val bands = parsed
            .sortedBy(IndexedBand::index)
            .map(IndexedBand::band)
        return result(bands, preampDb, presetId, presetName, target, warnings)
    }

    fun parseReq(
        bytes: ByteArray,
        presetId: String = "rew-import",
        presetName: String = "REW import",
        target: AudioDspChannelTarget = AudioDspChannelTarget.ALL,
    ): RewEqImportResult {
        require(bytes.size <= MAX_REQ_BYTES) { "REW .req file is too large" }
        require(bytes.isNotEmpty()) { "REW .req file is empty" }
        val reader = ReqReader(bytes)
        val records = reader.readFilters()
        val warnings = mutableListOf<String>()
        val parsed = mutableListOf<AudioDspBand>()
        records.forEachIndexed { index, record ->
            val type = filterType(record.typeCode)
            when {
                record.typeCode.equals("NONE", true) -> Unit
                type == null -> warnings += "filter ${index + 1}: unsupported filter type ${record.typeCode} ignored"
                !record.frequency.isFinite() || record.frequency !in AudioDspBand.MIN_FREQUENCY_HZ..AudioDspBand.MAX_FREQUENCY_HZ -> {
                    warnings += "filter ${index + 1}: frequency ${record.frequency} Hz is outside supported range"
                }
                !record.gain.isFinite() || record.gain !in AudioDspBand.MIN_GAIN_DB..AudioDspBand.MAX_GAIN_DB -> {
                    warnings += "filter ${index + 1}: gain ${record.gain} dB is outside supported range"
                }
                !record.q.isFinite() || record.q !in AudioDspBand.MIN_Q..AudioDspBand.MAX_Q -> {
                    warnings += "filter ${index + 1}: Q ${record.q} is outside supported range"
                }
                parsed.size >= AudioDspChannelRule.MAX_BANDS_PER_RULE -> {
                    warnings += "filter ${index + 1}: more than ${AudioDspChannelRule.MAX_BANDS_PER_RULE} filters; remaining rows ignored"
                }
                else -> parsed += AudioDspBand(type, record.frequency.toFloat(), record.gain.toFloat(), record.q.toFloat(), record.enabled)
            }
        }
        require(parsed.isNotEmpty()) { "REW .req file contains no supported filters" }
        return result(parsed, 0f, presetId, presetName, target, warnings)
    }

    private fun result(
        bands: List<AudioDspBand>,
        preampDb: Float,
        presetId: String,
        presetName: String,
        target: AudioDspChannelTarget,
        warnings: List<String>,
    ): RewEqImportResult = RewEqImportResult(
        preset = AudioDspPreset(
            id = presetId.trim().ifBlank { "rew-import" },
            name = presetName.trim().ifBlank { "REW import" },
            preampDb = preampDb.coerceIn(-24f, 12f),
            rules = listOf(AudioDspChannelRule(target = target, bands = bands)),
        ),
        importedBandCount = bands.size,
        warnings = warnings.distinct(),
    )

    private fun parseDelimited(line: String): MatchResult? {
        val delimiter = when {
            line.contains('\t') -> '\t'
            line.count { it == ',' } >= 5 -> ','
            line.count { it == ';' } >= 5 -> ';'
            else -> return null
        }
        val fields = line.split(delimiter).map { it.trim().trim('"', '\'') }
        if (fields.size < 6) return null
        val index = delimitedIndexPattern.matchEntire(fields[0]) ?: return null
        if (!fields[1].equals("on", true) && !fields[1].equals("off", true)) return null
        val numberFields = fields.drop(3).take(3).map { it.substringBefore(' ').trim() }
        if (numberFields.any { it.toFloatOrNull() == null }) return null
        val synthetic = "${index.groupValues[1]}:${fields[1]} ${fields[2]} Fc ${numberFields[0]} Hz Gain ${numberFields[1]} dB Q ${numberFields[2]}"
        return filterPattern.matchEntire(synthetic)
    }

    private fun splitTableFields(line: String): List<String>? {
        val delimiter = when {
            line.contains('\t') -> '\t'
            line.count { it == ',' } >= 4 -> ','
            line.count { it == ';' } >= 4 -> ';'
            else -> return null
        }
        return line.split(delimiter).map { it.trim().trim('"', '\'') }
    }

    private fun looksLikeFilterRow(line: String): Boolean =
        line.startsWith("filter", ignoreCase = true) || line.matches(Regex("""^\s*\d+\s*[,;\t]"""))

    private fun isDelimitedHeader(line: String): Boolean =
        line.contains("frequency", ignoreCase = true) && line.contains("gain", ignoreCase = true)

    private fun filterType(code: String): AudioDspFilterType? = when (code) {
        "PK", "PEAK", "PEAKING" -> AudioDspFilterType.PEAKING
        "LS", "LOWSHELF", "LOW_SHELF" -> AudioDspFilterType.LOW_SHELF
        "HS", "HIGHSHELF", "HIGH_SHELF" -> AudioDspFilterType.HIGH_SHELF
        "LP", "LOWPASS", "LOW_PASS" -> AudioDspFilterType.LOW_PASS
        "HP", "HIGHPASS", "HIGH_PASS" -> AudioDspFilterType.HIGH_PASS
        "NO", "NOTCH" -> AudioDspFilterType.NOTCH
        "BP", "BANDPASS", "BAND_PASS" -> AudioDspFilterType.BAND_PASS
        else -> null
    }

    private data class IndexedBand(val index: Int, val band: AudioDspBand)

    private data class GenericTable(
        val numberIndex: Int?,
        val enabledIndex: Int?,
        val typeIndex: Int,
        val frequencyIndex: Int,
        val gainIndex: Int,
        val qIndex: Int?,
    ) {
        fun parse(fields: List<String>, fallbackIndex: Int): TableRow? {
            val type = fields.getOrNull(typeIndex)?.trim().orEmpty()
            if (type.isBlank() || type.equals("none", true)) return TableRow.Empty
            val index = numberIndex?.let { fields.getOrNull(it)?.toIntOrNull() } ?: fallbackIndex
            val enabled = enabledIndex?.let { fields.getOrNull(it)?.let(::parseBoolean) } ?: true
            val frequency = fields.getOrNull(frequencyIndex)?.toDoubleOrNull()
            val gain = fields.getOrNull(gainIndex)?.toDoubleOrNull()
            val q = qIndex?.let { fields.getOrNull(it)?.toDoubleOrNull() } ?: 1.0
            if (frequency == null || gain == null) return TableRow.Invalid("invalid Generic filter values")
            return TableRow.Filter(index, type, frequency, gain, q, enabled)
        }

        companion object {
            fun fromHeader(fields: List<String>): GenericTable? {
                fun find(predicate: (String) -> Boolean): Int? = fields.indexOfFirst(predicate).takeIf { it >= 0 }
                val type = find { it.replace(Regex("[^A-Za-z]"), "").equals("type", true) } ?: return null
                val frequency = find { it.replace(Regex("[^A-Za-z]"), "").startsWith("frequency", true) } ?: return null
                val gain = find { it.replace(Regex("[^A-Za-z]"), "").startsWith("gain", true) } ?: return null
                return GenericTable(
                    numberIndex = find { it.equals("number", true) || it.equals("filter", true) },
                    enabledIndex = find { it.equals("enabled", true) || it.equals("state", true) },
                    typeIndex = type,
                    frequencyIndex = frequency,
                    gainIndex = gain,
                    qIndex = find { it.equals("q", true) },
                )
            }
        }
    }

    private sealed interface TableRow {
        data object Empty : TableRow
        data class Invalid(val message: String) : TableRow
        data class Filter(
            val index: Int,
            val typeCode: String,
            val frequency: Double,
            val gain: Double,
            val q: Double,
            val enabled: Boolean,
        ) : TableRow
    }

    private fun parseBoolean(value: String): Boolean =
        value.equals("true", true) || value.equals("on", true) || value == "1"

    private data class ReqRecord(
        val enabled: Boolean,
        val frequency: Double,
        val gain: Double,
        val q: Double,
        val typeCode: String,
    )

    private class ReqReader(bytes: ByteArray) {
        private val input = java.io.DataInputStream(java.io.ByteArrayInputStream(bytes))
        private var filterDescriptor: ReqDescriptor? = null

        fun readFilters(): List<ReqRecord> {
            require(input.readUnsignedShort() == 0xACED && input.readUnsignedShort() == 5) {
                "invalid REW .req serialization header"
            }
            require(readStringToken().contains("TMreq Filters File", ignoreCase = true)) {
                "unsupported REW .req file header"
            }
            skipBlockData()
            readStringToken()
            skipBlockData()
            require(input.readUnsignedByte() == TC_ARRAY) { "REW .req filter array is missing" }
            readClassDescriptor()
            val count = input.readInt()
            require(count in 0..MAX_REQ_FILTERS) { "REW .req filter count is invalid" }
            return buildList(count) {
                repeat(count) {
                    require(input.readUnsignedByte() == TC_OBJECT) { "REW .req filter object is invalid" }
                    val descriptor = readClassDescriptor() ?: error("REW .req filter descriptor is missing")
                    val values = descriptor.fields.associate { field -> field.name to readValue(field.typeCode, field.name) }
                    add(
                        ReqRecord(
                            enabled = values["enabled"] as? Boolean ?: false,
                            frequency = values["fc"] as? Double ?: Double.NaN,
                            gain = values["gain"] as? Double ?: Double.NaN,
                            q = values["Q"] as? Double ?: Double.NaN,
                            typeCode = values["filterType"] as? String ?: "NONE",
                        ),
                    )
                }
            }
        }

        private var lastFilterType: String? = null
        private val filterTypeReferences = mutableMapOf<Int, String>()

        private fun readValue(typeCode: Char, fieldName: String): Any? = when (typeCode) {
            'Z' -> input.readUnsignedByte() != 0
            'I' -> input.readInt()
            'D' -> java.lang.Double.longBitsToDouble(input.readLong())
            'B' -> input.readByte()
            'S' -> input.readShort()
            'J' -> input.readLong()
            'F' -> Float.fromBits(input.readInt())
            'C' -> input.readChar()
            'L', '[' -> readObjectValue(fieldName)
            else -> error("unsupported REW .req field type $typeCode")
        }

        private fun readObjectValue(fieldName: String): Any? = when (input.readUnsignedByte()) {
            TC_NULL -> null
            TC_REFERENCE -> {
                val reference = input.readInt()
                if (fieldName == "filterType") {
                    val value = filterTypeReferences[reference] ?: lastFilterType
                    if (value != null) filterTypeReferences[reference] = value
                    value
                } else null
            }
            TC_ENUM -> {
                readClassDescriptor()
                readStringToken().also { if (fieldName == "filterType") lastFilterType = it }
            }
            TC_OBJECT -> {
                val descriptor = readClassDescriptor() ?: return null
                descriptor.fields.forEach { readValue(it.typeCode, it.name) }
                null
            }
            else -> error("unsupported REW .req object token")
        }

        private fun readClassDescriptor(): ReqDescriptor? = when (input.readUnsignedByte()) {
            TC_NULL -> null
            TC_REFERENCE -> { input.readInt(); filterDescriptor }
            TC_CLASSDESC -> {
                val name = input.readUTF()
                input.readLong()
                input.readUnsignedByte()
                val fieldCount = input.readUnsignedShort()
                val fields = buildList(fieldCount) {
                    repeat(fieldCount) {
                        val type = input.readUnsignedByte().toChar()
                        val fieldName = input.readUTF()
                        if (type == 'L' || type == '[') readStringTokenOrReference()
                        add(ReqField(fieldName, type))
                    }
                }
                require(input.readUnsignedByte() == TC_ENDBLOCKDATA) { "invalid REW .req class descriptor" }
                readClassDescriptor()
                ReqDescriptor(name, fields).also { if (name == "roomeqwizard.Filter") filterDescriptor = it }
            }
            else -> error("unsupported REW .req class descriptor")
        }

        private fun readStringToken(): String {
            require(input.readUnsignedByte() == TC_STRING) { "REW .req string is missing" }
            return input.readUTF()
        }

        private fun readStringTokenOrReference() {
            when (input.readUnsignedByte()) {
                TC_STRING -> input.readUTF()
                TC_REFERENCE -> input.readInt()
                else -> error("invalid REW .req field descriptor string")
            }
        }

        private fun skipBlockData() {
            val token = input.readUnsignedByte()
            require(token == TC_BLOCKDATA) { "REW .req block data is missing" }
            input.skipBytes(input.readUnsignedByte())
        }

        private data class ReqDescriptor(val name: String, val fields: List<ReqField>)
        private data class ReqField(val name: String, val typeCode: Char)
    }

    private const val TC_NULL = 0x70
    private const val TC_REFERENCE = 0x71
    private const val TC_CLASSDESC = 0x72
    private const val TC_OBJECT = 0x73
    private const val TC_STRING = 0x74
    private const val TC_ARRAY = 0x75
    private const val TC_BLOCKDATA = 0x77
    private const val TC_ENDBLOCKDATA = 0x78
    private const val TC_ENUM = 0x7E
    private const val MAX_REQ_FILTERS = 1024
    private const val MAX_TEXT_CHARS = 1_000_000
    private const val MAX_REQ_BYTES = 1_500_000
}
