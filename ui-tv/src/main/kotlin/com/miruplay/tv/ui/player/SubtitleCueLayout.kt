@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import android.os.Parcel
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import androidx.media3.common.text.Cue

private enum class SubtitleRegion {
    START,
    MIDDLE,
    END,
}

private data class DialogueRunKey(
    val vertical: SubtitleRegion,
    val horizontal: SubtitleRegion,
    val textAlignment: Layout.Alignment?,
    val multiRowAlignment: Layout.Alignment?,
)

private data class CueVisualSignature(
    val text: TextVisualSignature?,
    val textAlignment: Layout.Alignment?,
    val multiRowAlignment: Layout.Alignment?,
    val bitmap: Any?,
    val line: Float,
    val lineType: Int,
    val lineAnchor: Int,
    val position: Float,
    val positionAnchor: Int,
    val size: Float,
    val bitmapHeight: Float,
    val windowColorSet: Boolean,
    val windowColor: Int,
    val textSizeType: Int,
    val textSize: Float,
    val verticalType: Int,
    val shearDegrees: Float,
)

private class TextVisualSignature(
    private val contents: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is TextVisualSignature && contents.contentEquals(other.contents)

    override fun hashCode(): Int = contents.contentHashCode()
}

/**
 * Merge contiguous default-position dialogue into measured blocks without disturbing
 * signs, effects, bitmap subtitles, or cues carrying explicit ASS coordinates.
 */
internal fun restackSubtitleCues(cues: List<Cue>): List<Cue> = buildList(cues.size) {
    var index = 0
    while (index < cues.size) {
        val key = cues[index].dialogueRunKey()
        if (key == null) {
            add(cues[index++])
            continue
        }

        var end = index + 1
        while (end < cues.size && cues[end].dialogueRunKey() == key) {
            end++
        }
        addAll(mergeDialogueRun(key, cues.subList(index, end)))
        index = end
    }
}

private fun Cue.dialogueRunKey(): DialogueRunKey? {
    if (
        text.isNullOrBlank() ||
        bitmap != null ||
        size != Cue.DIMEN_UNSET ||
        windowColorSet ||
        verticalType != Cue.TYPE_UNSET ||
        shearDegrees != 0f
    ) {
        return null
    }
    val vertical = defaultRegion(line, lineAnchor, lineType, default = SubtitleRegion.END) ?: return null
    val horizontal = defaultRegion(
        position,
        positionAnchor,
        Cue.LINE_TYPE_FRACTION,
        default = SubtitleRegion.MIDDLE,
    ) ?: return null
    return DialogueRunKey(vertical, horizontal, textAlignment, multiRowAlignment)
}

private fun defaultRegion(
    value: Float,
    anchor: Int,
    type: Int,
    default: SubtitleRegion,
): SubtitleRegion? {
    if (value == Cue.DIMEN_UNSET) return default
    if (type != Cue.LINE_TYPE_FRACTION) return null
    return when {
        value.isCloseTo(0.05f) && anchor == Cue.ANCHOR_TYPE_START -> SubtitleRegion.START
        value.isCloseTo(0.5f) && anchor == Cue.ANCHOR_TYPE_MIDDLE -> SubtitleRegion.MIDDLE
        value.isCloseTo(0.95f) && anchor == Cue.ANCHOR_TYPE_END -> SubtitleRegion.END
        else -> null
    }
}

private fun Float.isCloseTo(expected: Float): Boolean =
    kotlin.math.abs(this - expected) < 0.001f

private fun mergeDialogueRun(
    key: DialogueRunKey,
    cues: List<Cue>,
): List<Cue> {
    val signatures = cues.map(Cue::visualSignature)
    val signaturesByText = mutableMapOf<String, CueVisualSignature>()
    cues.forEachIndexed { index, cue ->
        val previous = signaturesByText.putIfAbsent(cue.text.toString(), signatures[index])
        if (previous != null && previous != signatures[index]) {
            return cues
        }
    }

    val uniqueCues = buildList(cues.size) {
        val emittedSignatures = mutableSetOf<CueVisualSignature>()
        cues.forEachIndexed { index, cue ->
            if (emittedSignatures.add(signatures[index])) add(cue)
        }
    }
    return listOf(buildMergedCue(key, uniqueCues, cues.maxOf(Cue::zIndex)))
}

private fun Cue.visualSignature(): CueVisualSignature = CueVisualSignature(
    text = text?.toVisualSignature(),
    textAlignment = textAlignment,
    multiRowAlignment = multiRowAlignment,
    bitmap = bitmap,
    line = line,
    lineType = lineType,
    lineAnchor = lineAnchor,
    position = position,
    positionAnchor = positionAnchor,
    size = size,
    bitmapHeight = bitmapHeight,
    windowColorSet = windowColorSet,
    windowColor = windowColor,
    textSizeType = textSizeType,
    textSize = textSize,
    verticalType = verticalType,
    shearDegrees = shearDegrees,
)

private fun CharSequence.toVisualSignature(): TextVisualSignature {
    val parcel = Parcel.obtain()
    return try {
        TextUtils.writeToParcel(this, parcel, 0)
        TextVisualSignature(parcel.marshall())
    } finally {
        parcel.recycle()
    }
}

private fun buildMergedCue(
    key: DialogueRunKey,
    cues: List<Cue>,
    maxZIndex: Int,
): Cue {
    val first = cues.first()
    val compatibleTextSizeType = first.textSizeType
    val hasCompatibleTextSizes = compatibleTextSizeType != Cue.TYPE_UNSET &&
        first.textSize != Cue.DIMEN_UNSET &&
        cues.all {
            it.textSizeType == compatibleTextSizeType && it.textSize != Cue.DIMEN_UNSET
        }
    val baseTextSize = if (hasCompatibleTextSizes) cues.maxOf(Cue::textSize) else Cue.DIMEN_UNSET
    val mergedText = SpannableStringBuilder().apply {
        cues.forEachIndexed { index, cue ->
            if (index > 0) append('\n')
            val start = length
            append(cue.text)
            if (hasCompatibleTextSizes) {
                setSpan(
                    RelativeSizeSpan(cue.textSize / baseTextSize),
                    start,
                    length,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
    return first.buildUpon()
        .setText(mergedText)
        .apply {
            if (hasCompatibleTextSizes) {
                setTextSize(baseTextSize, compatibleTextSizeType)
            }
            setZIndex(maxZIndex)
            if (key.vertical == SubtitleRegion.END) {
                setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
                setLineAnchor(Cue.TYPE_UNSET)
            }
        }
        .build()
}
