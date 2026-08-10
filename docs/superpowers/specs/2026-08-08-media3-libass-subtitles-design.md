# Media3 libass Subtitle Rendering Design

## Context

MiruPlay's normal player uses Media3 1.8.0. Media3's SSA parser converts ASS
events into Android `Cue` objects and discards ASS semantics it cannot express.
That includes important inline styling and effects such as `\fs`, authored
outlines, transforms, movement, rotations, clipping, drawing commands, and
karaoke timing.

The LV999 S01E01 scene at about `00:07.07` exposes the failure clearly. Twelve
positioned signs use authored sizes including 34, 38, 50, and 66, but the
Media3 cue path renders them at the style default of 70. The resulting text is
both too large and overlapping. Reducing all crowded cues would mask the lost
ASS data rather than fix the compatibility bug.

The APK already packages `libmpv.so` for `arm64-v8a` and `armeabi-v7a`. Both
binaries export the complete libass API needed by this design, so MiruPlay does
not need to package a second libass build.

## Goals

- Render selected ASS and SSA tracks with libass rather than Media3 `Cue`
  conversion.
- Preserve authored font sizes, borders, colors, positions, transforms,
  movement, rotations, clipping, drawings, layers, and karaoke timing.
- Load Matroska font attachments through the same playback read instead of a
  fixed-size prefix scan or a second full-file scan.
- Keep Media3 responsible for video, audio, buffering, seeking, timeline, and
  track selection.
- Keep SRT, WebVTT, TTML, bitmap subtitles, and other non-ASS formats on the
  existing Media3 path.
- Give non-ASS text a black outline when the Android caption style does not
  already define an edge.
- Preserve the existing zlib-compressed ASS repair path.
- Fail back to Media3's SSA decoder when the native libass bridge is not
  available on the device.
- Verify the real dense LV999 case on HK1 through the HDMI/NanoKVM view.

## Non-Goals

- Do not add a subtitle size preference or a crowding heuristic.
- Do not override valid ASS styles with a forced global border or font size.
- Do not switch normal playback to the mpv video/audio backend.
- Do not run the MiruPlay media index for device validation.
- Do not redesign subtitle menus, persistence, WebAPI, or WebUI settings.
- Do not scan an entire Matroska file a second time just to find attachments.

## Approaches Considered

### 1. Media3 video/audio plus a libass overlay (selected)

Media3 keeps ownership of playback and track selection. A native ASS text
renderer consumes only the selected raw ASS stream, and libass draws to a
transparent surface above the video. This preserves existing player behavior
and gives libass the original ASS data.

The cost is a JNI bridge, a custom Media3 text renderer, and explicit surface
lifecycle handling. Those pieces have narrow interfaces and can be tested
independently.

### 2. Route any media containing ASS through embedded mpv

The existing mpv backend already handles ASS and Matroska attachments. This is
less subtitle code, but it changes the video/audio decoder, HDR behavior,
controls, session ownership, WebDAV behavior, and fallback rules for an issue
that should be isolated to subtitles. It is therefore too broad.

### 3. Extend Media3's SSA parser

Adding individual tags such as `\fs` would repair selected examples, but a
`Cue` model still cannot represent the full ASS renderer model. Reimplementing
libass semantics in Android spans would be a second incomplete ASS engine and
is rejected.

## Architecture

### Native bridge

`player-mpv-android` builds a small `miruplay_libass` JNI library. It opens the
already packaged `libmpv.so` with `dlopen` and resolves libass symbols with
`dlsym`. The bridge owns opaque libass library, renderer, and track objects.

The Kotlin-facing native renderer supports these operations:

- check whether all required libass symbols are available;
- create or replace a track from an ASS document/header;
- add Matroska font attachment bytes;
- append an absolute-time `Dialogue:` event;
- flush events on seek or track change;
- configure frame and storage sizes;
- render the current frame directly into an Android `Surface`;
- clear a transparent surface and release all native resources.

Rendering directly into an `ANativeWindow` avoids copying a full 1080p bitmap
through JNI for every frame. The native compositor walks every `ASS_Image` in
the linked list, so it has no fixed image-count or coverage-buffer limit.
Native entry points serialize access to each session because sample ingestion
and rendering run on different threads.

ASS colors and alpha are used exactly as authored. `ass_set_frame_size` uses
the overlay surface dimensions. `ass_set_storage_size` uses the decoded video
dimensions so anamorphic and authored coordinate behavior remain correct.

### Raw ASS renderer

Both MiruPlay Media3 renderer factories add `NativeAssTextRenderer` before the
stock `TextRenderer`.

`NativeAssTextRenderer` reports `FORMAT_HANDLED` only for `MimeTypes.TEXT_SSA`
and only when the JNI bridge has passed its symbol preflight. It therefore
receives exactly the selected ASS stream chosen by Media3; it does not need a
parallel track-id mapping. For other subtitle formats, and when native libass
is unavailable, the stock `TextRenderer` remains responsible.

Matroska extraction uses `MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA`.
Media3 still prefixes each raw ASS block with relative start/end fields derived
from Matroska `BlockDuration`. The native renderer converts that sample into an
absolute `Dialogue:` line using `DecoderInputBuffer.timeUs`, preserving the
remaining event fields and override tags byte-for-byte. Before conversion it
runs the existing `inflateSubtitleSampleIfNeeded` repair.

Standalone `.ass` and `.ssa` subtitle samples contain a full ASS document.
They replace the native track directly rather than being treated as a single
Matroska event.

The stock `TextRenderer` enables Media3's legacy subtitle decoding because raw
Matroska mode applies to all Matroska text formats, not only ASS. SRT and
WebVTT therefore continue to work without passing through libass.

### Matroska font attachments

`LibassMatroskaExtractor` subclasses Media3's `MatroskaExtractor`. It adds the
standard Matroska Attachments element IDs to the existing EBML processor and
collects font `FileName`, `FileMimeType`, and `FileData` fields as the normal
extractor reaches them.

This has three advantages over a separate scanner:

- attachments are found wherever they occur in the segment;
- local, content, HTTP, and authenticated WebDAV sources keep using the
  existing Media3 `DataSource` path;
- video bytes are not read twice.

Only recognized font MIME types or `.ttf`, `.otf`, `.ttc`, and `.otc` names are
accepted. Per-file size, aggregate size, and attachment-count limits reject
malformed containers without risking unbounded allocation. Fonts arriving
after the ASS header cause the native renderer to be recreated with the new
font set and the currently buffered events replayed.

### Session and timing

Each ExoPlayer instance has one `LibassSubtitleSession`, registered in a weak
player-to-session registry. The same session is passed to its extractor and
text renderer. Starting a new media item increments the session generation and
clears documents, events, fonts, and stale render work.

The session implements `VideoFrameMetadataListener`. Each video presentation
timestamp is posted to a dedicated subtitle render thread. Pending timestamps
are coalesced so a slow subtitle draw cannot block the decoder thread or build
an unbounded queue. Pausing naturally freezes subtitle animation because no
new presentation timestamp is rendered. Seeking flushes libass events, and the
selected sample stream repopulates them from the new position.

### Overlay lifecycle

The normal `PlayerView` receives a `LibassSubtitleSurfaceView` as the last child
of `exo_content_frame`. It is transparent, non-focusable, and uses
`setZOrderMediaOverlay(true)` so it sits above the video `SurfaceView` on HK1.
Being inside the content frame keeps its bounds aligned with Media3's fitted
video rectangle.

Binding and unbinding are tied to `AndroidView` creation/update/release. A
released player view detaches its overlay and clears the surface. Media3's
ordinary `SubtitleView` remains available for every non-ASS track.

## Data Flow

1. `ExoPlaybackController` starts a media item and resets the active player's
   libass session generation.
2. `LibassMatroskaExtractor` emits raw subtitle samples and publishes font
   attachments to that session during normal extraction.
3. Media3 selects one text track.
4. ASS selects `NativeAssTextRenderer`; other formats select `TextRenderer`.
5. The ASS renderer installs codec private data or a standalone document,
   inflates protected zlib samples when needed, and appends absolute events.
6. Video frame metadata supplies the presentation timestamp and video storage
   dimensions.
7. The session renders the latest timestamp into the bound transparent surface.
8. A seek, track switch, media switch, surface release, or player release
   clears the appropriate generation and native resources.

## Readable Non-ASS Edges

ASS uses its authored border and shadow fields. For non-ASS text, MiruPlay
continues to respect an enabled Android caption style. If the resolved caption
style has no edge, MiruPlay supplies `EDGE_TYPE_OUTLINE` with a black edge while
preserving foreground, background, window, and typeface values. Transparent
subtitle-background preference affects only the background field.

## Error Handling

- A missing `libmpv.so` or missing libass symbol makes the native ASS renderer
  report unsupported before track selection, allowing Media3's SSA renderer to
  handle the track.
- A malformed ASS sample is logged and skipped without stopping audio/video.
- A malformed or oversized font attachment is skipped with a bounded warning.
- A stale extractor, renderer, or render callback cannot mutate a newer media
  generation.
- Surface creation/destruction is idempotent; native drawing is skipped while
  no valid surface exists.
- JNI/native failures clear the overlay and leave playback running.

## Testing

Automated tests cover:

- conversion of Media3-prefixed ASS samples into absolute-time events;
- preservation of commas, override tags, drawing data, and UTF-8 text;
- zlib-compressed LV999-style samples;
- standalone ASS/SSA document detection;
- raw ASS renderer format support and lifecycle transitions;
- Matroska attachment element collection, font filtering, and limits;
- session generation, stale-event rejection, seek clearing, and timestamp
  coalescing;
- PlayerView overlay attach/release behavior;
- black default outline for non-ASS subtitles while preserving explicit Android
  caption edges;
- native ABI build and libass symbol preflight.

Verification runs focused module tests, the full unit suite, lint, and a debug
APK build. Device acceptance copies only LV999 S01E01 from `S:` to HK1. It does
not run the media index. Playback is opened directly and observed through
NanoKVM at approximately `00:07.07` and `00:42.56`. The first scene must retain
its distinct authored sizes and positions without large overlaps; the second
must retain bilingual dialogue and moving signs. HDMI evidence, not an ADB-only
screenshot, is the final visual gate.
