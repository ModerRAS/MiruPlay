package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
enum class AudioDspPhaseMode(val storageValue: String) {
    MINIMUM("minimum"),
    LINEAR("linear");

    companion object {
        fun fromStorageValue(value: String?): AudioDspPhaseMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: MINIMUM
    }
}

@Serializable
enum class AudioDspOutputMode(val storageValue: String) {
    AUTO_PRESERVE("auto_preserve"),
    STEREO_DOWNMIX("stereo_downmix"),
    HRTF_BINAURAL("hrtf_binaural");

    companion object {
        fun fromStorageValue(value: String?): AudioDspOutputMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: AUTO_PRESERVE
    }
}

@Serializable
enum class AudioDspFirQuality(val storageValue: String, val taps: Int) {
    LOW("low", 1024),
    MEDIUM("medium", 2048),
    HIGH("high", 4096);

    companion object {
        fun fromStorageValue(value: String?): AudioDspFirQuality =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}

@Serializable
enum class AudioDspFilterType(val storageValue: String) {
    PEAKING("peaking"),
    LOW_SHELF("low_shelf"),
    HIGH_SHELF("high_shelf"),
    LOW_PASS("low_pass"),
    HIGH_PASS("high_pass"),
    NOTCH("notch"),
    BAND_PASS("band_pass");

    companion object {
        fun fromStorageValue(value: String?): AudioDspFilterType =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: PEAKING
    }
}

@Serializable
enum class AudioDspChannelTarget(val storageValue: String) {
    ALL("all"),
    FRONT("front"),
    CENTER_LFE("center_lfe"),
    SURROUND("surround"),
    SURROUND_5_1("surround_5_1"),
    SURROUND_7_1("surround_7_1"),
    LEFT("left"),
    RIGHT("right"),
    CENTER("center"),
    LFE("lfe"),
    LEFT_SURROUND("left_surround"),
    RIGHT_SURROUND("right_surround");

    companion object {
        fun fromStorageValue(value: String?): AudioDspChannelTarget =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: ALL
    }
}

@Serializable
data class AudioDspBand(
    val type: AudioDspFilterType = AudioDspFilterType.PEAKING,
    val frequencyHz: Float = 1_000f,
    val gainDb: Float = 0f,
    val q: Float = 1f,
    val enabled: Boolean = true,
) {
    fun normalized(): AudioDspBand = copy(
        frequencyHz = frequencyHz.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ),
        gainDb = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB),
        q = q.coerceIn(MIN_Q, MAX_Q),
    )

    companion object {
        const val MIN_FREQUENCY_HZ = 10f
        const val MAX_FREQUENCY_HZ = 24_000f
        const val MIN_GAIN_DB = -24f
        const val MAX_GAIN_DB = 24f
        const val MIN_Q = 0.1f
        const val MAX_Q = 100f
    }
}

@Serializable
data class AudioDspChannelRule(
    val target: AudioDspChannelTarget = AudioDspChannelTarget.ALL,
    val bands: List<AudioDspBand> = emptyList(),
    val outputGainDb: Float = 0f,
) {
    fun normalized(): AudioDspChannelRule = copy(
        bands = bands.take(MAX_BANDS_PER_RULE).map(AudioDspBand::normalized),
        outputGainDb = outputGainDb.coerceIn(AudioDspBand.MIN_GAIN_DB, AudioDspBand.MAX_GAIN_DB),
    )

    companion object {
        const val MAX_BANDS_PER_RULE = 256
    }
}

@Serializable
data class AudioDspLimiter(
    val enabled: Boolean = false,
    val ceilingDb: Float = -1f,
    val releaseMs: Float = 100f,
) {
    fun normalized(): AudioDspLimiter = copy(
        ceilingDb = ceilingDb.coerceIn(-24f, 0f),
        releaseMs = releaseMs.coerceIn(1f, 2_000f),
    )
}

@Serializable
data class AudioDspPreset(
    val id: String,
    val name: String,
    val preampDb: Float = 0f,
    val phaseMode: AudioDspPhaseMode = AudioDspPhaseMode.MINIMUM,
    val firQuality: AudioDspFirQuality = AudioDspFirQuality.MEDIUM,
    val outputMode: AudioDspOutputMode = AudioDspOutputMode.AUTO_PRESERVE,
    val rules: List<AudioDspChannelRule> = emptyList(),
    val limiter: AudioDspLimiter = AudioDspLimiter(),
    val autoHeadroom: Boolean = true,
) {
    fun normalized(): AudioDspPreset = copy(
        id = id.trim().ifEmpty { AudioDspConfig.DEFAULT_PRESET_ID },
        name = name.trim().ifEmpty { "Neutral" },
        preampDb = preampDb.coerceIn(-24f, 12f),
        rules = rules.map(AudioDspChannelRule::normalized),
        limiter = limiter.normalized(),
    )
}

@Serializable
data class AudioDspConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val enabled: Boolean = false,
    val selectedPresetId: String = DEFAULT_PRESET_ID,
    val presets: List<AudioDspPreset> = listOf(neutralPreset()),
) {
    fun normalized(): AudioDspConfig {
        val normalizedPresets = presets
            .map(AudioDspPreset::normalized)
            .distinctBy(AudioDspPreset::id)
            .ifEmpty { listOf(neutralPreset()) }
        val selected = normalizedPresets.firstOrNull { it.id == selectedPresetId }?.id
            ?: normalizedPresets.first().id
        return copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            selectedPresetId = selected,
            presets = normalizedPresets,
        )
    }

    fun validationErrors(): List<String> = buildList {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) add("schemaVersion must be $CURRENT_SCHEMA_VERSION")
        if (presets.isEmpty()) add("presets must not be empty")
        if (presets.isNotEmpty() && presets.none { it.id == selectedPresetId }) {
            add("selectedPresetId must reference an existing preset")
        }
        val ids = mutableSetOf<String>()
        presets.forEachIndexed { presetIndex, preset ->
            val id = preset.id.trim()
            if (id.isBlank()) add("presets[$presetIndex].id must not be blank")
            if (!ids.add(id)) add("presets[$presetIndex].id is duplicate")
            if (preset.preampDb !in -24f..12f) add("presets[$presetIndex].preampDb is out of range")
            if (!preset.preampDb.isFinite()) add("presets[$presetIndex].preampDb must be finite")
            if (preset.limiter.ceilingDb !in -24f..0f || !preset.limiter.ceilingDb.isFinite()) {
                add("presets[$presetIndex].limiter.ceilingDb is out of range")
            }
            if (preset.limiter.releaseMs !in 1f..2_000f || !preset.limiter.releaseMs.isFinite()) {
                add("presets[$presetIndex].limiter.releaseMs is out of range")
            }
            preset.rules.forEachIndexed { ruleIndex, rule ->
                if (rule.bands.size > AudioDspChannelRule.MAX_BANDS_PER_RULE) {
                    add("presets[$presetIndex].rules[$ruleIndex] has too many bands")
                }
                if (rule.outputGainDb !in AudioDspBand.MIN_GAIN_DB..AudioDspBand.MAX_GAIN_DB || !rule.outputGainDb.isFinite()) {
                    add("presets[$presetIndex].rules[$ruleIndex].outputGainDb is out of range")
                }
                rule.bands.forEachIndexed { bandIndex, band ->
                    if (band.frequencyHz !in AudioDspBand.MIN_FREQUENCY_HZ..AudioDspBand.MAX_FREQUENCY_HZ || !band.frequencyHz.isFinite()) {
                        add("presets[$presetIndex].rules[$ruleIndex].bands[$bandIndex].frequencyHz is out of range")
                    }
                    if (band.gainDb !in AudioDspBand.MIN_GAIN_DB..AudioDspBand.MAX_GAIN_DB || !band.gainDb.isFinite()) {
                        add("presets[$presetIndex].rules[$ruleIndex].bands[$bandIndex].gainDb is out of range")
                    }
                    if (band.q !in AudioDspBand.MIN_Q..AudioDspBand.MAX_Q || !band.q.isFinite()) {
                        add("presets[$presetIndex].rules[$ruleIndex].bands[$bandIndex].q is out of range")
                    }
                }
            }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_PRESET_ID = "neutral"

        fun neutral(): AudioDspConfig = AudioDspConfig()

        private fun neutralPreset(): AudioDspPreset = AudioDspPreset(
            id = DEFAULT_PRESET_ID,
            name = "Neutral",
        )
    }
}
