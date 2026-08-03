# Media3 Subtitle Layout Repair Design

## Context

The normal Media3 player currently lets `PlayerView` render raw cues and then posts a second normalized write from `PlayerScreen`. A newly attached view is seeded with raw `currentCues`, old views are not detached, and a posted callback dereferences a mutable `playerViewRef`. Dense LV999 dialogue can therefore overlap, clip at the bottom, disappear after the fixed four-cue cap, or be overwritten by a stale view callback.

`restackSubtitleCues` also caps before deduplication, moves bottom dialogue to numeric line `-1`, and clones only the first cue. That drops unique dialogue and loses later per-cue font size and ordering information.

## Goals

- Make the normal Media3 path render transformed cues through one subtitle writer: the built-in `PlayerView` subtitle layer.
- Merge simultaneous ordinary horizontal dialogue into one measured text block so wrapping and line height cannot collide.
- Never discard unique dialogue because more than four cues are active.
- Preserve text spans and relative JP/CN font sizes inside a merged block.
- Keep a safe bottom margin, and raise ordinary dialogue above the player controls while they are visible.
- Detach replaced `PlayerView` instances and prevent old callbacks from touching a replacement view.
- Preserve clearly positioned signs, bitmap cues, vertical text, sheared text, windowed cues, and native mpv/libass behavior.

## Non-Goals

- Do not route normal Media3 playback through mpv.
- Do not normalize mpv ASS cues in Android. libass remains responsible for authored ASS layout.
- Do not modify zlib subtitle extraction or WebDAV playback in this change.
- Do not invent a policy that hides dialogue when the screen is crowded.

## Architecture

### Single Media3 subtitle writer

Add `SubtitleTransformingPlayer`, a `ForwardingPlayer` used only as the player assigned to `PlayerView`. It transforms `currentCues` and both `Player.Listener.onCues` overloads while delegating every other player operation unchanged.

`PlayerView` remains the only object that calls its `SubtitleView.setCues`. Because `getCurrentCues()` is transformed, a view attached mid-cue immediately receives the normalized block. Listener wrappers have stable equality so `PlayerView.setPlayer(null)` removes the exact underlying listener.

The real player owned by `PlayerViewModel` is not replaced. GL, VLC, mpv, playback commands, track selection, and media-session ownership keep using the real player.

### Ordinary dialogue layout

`restackSubtitleCues` scans cues in display order and merges only contiguous runs with the same default region, `textAlignment`, and `multiRowAlignment`. A cue is ordinary dialogue only when it has non-blank text, no bitmap, no explicit width, no window color, no vertical mode, no shear, and a default/unset horizontal and vertical position.

Contiguous processing prevents a positioned sign or effect between two z-index ranges from being reordered. Cues that do not qualify pass through by identity.

Within an eligible run:

- Remove only visually equivalent duplicate lines before merging. Equality ignores `zIndex` but includes text content, spans, alignment, size, position, and other visible cue properties.
- If the same text has conflicting visual styling, preserve that run unchanged because it may be an authored layered effect.
- Keep every remaining unique line. There is no fixed cue cap.
- Append each original `CharSequence` to a `SpannableStringBuilder`, retaining its spans.
- When all lines use the same Media3 text-size type, choose the largest cue size as the merged base size and apply `RelativeSizeSpan` to smaller line ranges.
- If cue size types are incompatible, preserve the run unchanged rather than silently changing typography.

For a bottom dialogue block, clear the explicit line so `SubtitleView` uses its bottom-padding fraction. Other default regions keep their original placement.

Cue-only inspection cannot prove whether an ASS `\\pos` command intentionally chose exactly Media3's default numeric coordinates. Clearly non-default positions are preserved; default-looking explicit coordinates remain an upstream provenance limitation and are not broadened into a parser rewrite in this PR.

### PlayerView lifecycle and safe area

Create the transforming wrapper with `remember(player)` so recomposition does not create listener churn. Assign it only to the standard `PlayerView` host.

Use `AndroidView.onRelease` to set the released view's player to `null`. Clear `playerViewRef` only when it still points to that exact view, so a delayed release of an old host cannot clear the replacement reference.

Apply `SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION` while controls are hidden and a larger player-control fraction while controls are visible. This affects ordinary bottom dialogue because its merged line is unset; explicitly positioned signs remain at their authored coordinates.

## Error Handling

- Empty cue groups transform to an empty list and clear normally through `PlayerView`.
- Non-mergeable cues are preserved rather than approximated.
- A removed listener has no reference to a replacement view. A queued callback can update only the old detached `PlayerView`.
- If NanoKVM or WebDAV access is unavailable, local device media remains the acceptance path; WebDAV transport is not part of this fix.

## Testing

Automated regression tests cover:

- duplicate layers beyond four cues do not hide earlier unique dialogue;
- bottom dialogue retains an unset line for safe padding;
- JP/CN font-size ratios and existing spans survive merging;
- positioned, bitmap, vertical, sheared, windowed, and conflicting layered cues pass through;
- `currentCues` is transformed on attachment and keeps `presentationTimeUs`;
- both cue callback overloads are transformed;
- listener removal uses an equivalent wrapper;
- releasing an old view detaches it without clearing a newer `playerViewRef`.

Device acceptance uses the local LV999 S01E02 file on HK1. Capture NanoKVM evidence near `02:32` for ordinary bilingual dialogue and `03:27` for four simultaneous Layer 5/6 cues, both with controls hidden and visible. All unique dialogue must be readable, measured as one non-overlapping block, and clear of the viewport/control bar.
