package `is`.xyz.mpv

/**
 * mpv options that normalise ASS subtitle layout so bilingual (e.g. CN+JP)
 * dialogue lines stack vertically from the bottom of the frame instead of
 * overlapping at the same `\pos` position.
 *
 * This mirrors the ExoPlayer-side cue-restack fix: with `sub-ass-override=force`
 * libass ignores embedded ASS positioning/styles and renders plain, wrapped
 * subtitles stacked by its default collision avoidance — so simultaneous lines
 * no longer overlap, overflow the screen, or clip to half-height.
 *
 * Note: this discards ASS styling (colours/fonts/effects) on the mpv backend,
 * trading fidelity for readability, consistent with normalising layout on the
 * ExoPlayer path. If styled ASS is later desired, this can be made a preference.
 */
internal val mpvSubtitleLayoutNormalisationOptions: List<Pair<String, String>> = listOf(
    "sub-ass-override" to "force",
)
