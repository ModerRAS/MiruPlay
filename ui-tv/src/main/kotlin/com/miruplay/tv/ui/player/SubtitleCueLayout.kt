@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import android.text.Layout
import android.text.SpannableStringBuilder
import androidx.media3.common.text.Cue

/** Maximum number of simultaneous ordinary dialogue cues retained per group. */
internal const val MAX_STACKED_SUBTITLE_CUES = 4

private enum class SubtitleRegion {
    START,
    MIDDLE,
    END,
}

private data class DialogueGroupKey(
    val vertical: SubtitleRegion,
    val horizontal: SubtitleRegion,
    val textAlignment: Layout.Alignment?,
)

/**
 * Merge default-position text cues into measured blocks without disturbing signs,
 * effects, bitmap subtitles, or cues carrying explicit ASS coordinates.
 */
internal fun restackSubtitleCues(
    cues: List<Cue>,
    maxStackedCues: Int = MAX_STACKED_SUBTITLE_CUES,
): List<Cue> {
    if (cues.isEmpty()) return emptyList()

    val effectiveMax = maxStackedCues.coerceAtLeast(1)
    val groups = linkedMapOf<DialogueGroupKey, MutableList<Cue>>()
    val cueGroups = arrayOfNulls<DialogueGroupKey>(cues.size)
    cues.forEachIndexed { index, cue ->
        cue.dialogueGroupKey()?.let { key ->
            cueGroups[index] = key
            groups.getOrPut(key) { mutableListOf() }.add(cue)
        }
    }

    if (groups.isEmpty()) return cues

    val emittedGroups = mutableSetOf<DialogueGroupKey>()
    return buildList(cues.size) {
        cues.forEachIndexed { index, cue ->
            val key = cueGroups[index]
            if (key == null) {
                add(cue)
            } else if (emittedGroups.add(key)) {
                add(buildMergedCue(key, groups.getValue(key), effectiveMax))
            }
        }
    }
}

private fun Cue.dialogueGroupKey(): DialogueGroupKey? {
    if (text == null || bitmap != null || size != Cue.DIMEN_UNSET) return null
    val vertical = defaultRegion(line, lineAnchor, lineType, default = SubtitleRegion.END) ?: return null
    val horizontal = defaultRegion(
        position,
        positionAnchor,
        Cue.LINE_TYPE_FRACTION,
        default = SubtitleRegion.MIDDLE,
    ) ?: return null
    return DialogueGroupKey(vertical, horizontal, textAlignment)
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

private fun buildMergedCue(
    key: DialogueGroupKey,
    cues: List<Cue>,
    maxStackedCues: Int,
): Cue {
    val retained = cues
        .takeLast(maxStackedCues)
        .asReversed()
        .distinctBy { it.text.toString() }
        .asReversed()
    val first = retained.first()
    val mergedText = SpannableStringBuilder().apply {
        retained.forEachIndexed { index, cue ->
            if (index > 0) append('\n')
            append(cue.text)
        }
    }
    return first.buildUpon()
        .setText(mergedText)
        .setSize(Cue.DIMEN_UNSET)
        .apply {
            if (key.vertical == SubtitleRegion.END) {
                setLine(-1f, Cue.LINE_TYPE_NUMBER)
                setLineAnchor(Cue.ANCHOR_TYPE_END)
            }
        }
        .build()
}
