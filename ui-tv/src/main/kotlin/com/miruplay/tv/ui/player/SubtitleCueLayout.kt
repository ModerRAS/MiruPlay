@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import androidx.media3.common.text.Cue

/**
 * Maximum number of simultaneous subtitle cues we stack on screen. Beyond this the
 * oldest cues are dropped so the stack never overflows the top of the screen —
 * which is what previously caused every overlapping line to be clipped to half
 * its height.
 */
internal const val MAX_STACKED_SUBTITLE_CUES = 4

/**
 * Restack simultaneous subtitle cues so they layout vertically from the bottom of
 * the screen instead of overlapping at the same ASS position.
 *
 * Bilingual (e.g. CN+JP) ASS subtitles usually position every dialogue line at the
 * bottom-centre default position; media3's SubtitleView then draws them on top of
 * each other. Here we clear each cue's horizontal position/size (so nothing drifts
 * off the left/right edges or overflows the screen) and assign explicit, strictly
 * increasing line numbers counting up from the bottom, so cues stack without
 * overlap. We also cap the number of stacked cues to keep them on screen.
 *
 * Embedded styling (colours, typeface, font size) is preserved — only positioning
 * is normalised — so ASS-styled subtitles keep their look.
 */
internal fun restackSubtitleCues(
    cues: List<Cue>,
    maxStackedCues: Int = MAX_STACKED_SUBTITLE_CUES,
): List<Cue> {
    if (cues.isEmpty()) return emptyList()
    val effectiveMax = maxStackedCues.coerceAtLeast(1)
    val capped = if (cues.size > effectiveMax) cues.takeLast(effectiveMax) else cues
    return capped.mapIndexed { index, cue ->
        cue.buildUpon()
            // Count up from the bottom: first cue one line above the bottom, next
            // cue two lines up, ... so simultaneous cues stack without overlapping.
            .setLine(-(index + 1).toFloat(), Cue.LINE_TYPE_NUMBER)
            .setLineAnchor(Cue.ANCHOR_TYPE_END)
            // Clear any embedded horizontal position/size so cues centre within the
            // view and never render past the screen edges (overflow fix).
            .setPosition(Cue.DIMEN_UNSET)
            .setPositionAnchor(Cue.TYPE_UNSET)
            .setSize(Cue.DIMEN_UNSET)
            .build()
    }
}
