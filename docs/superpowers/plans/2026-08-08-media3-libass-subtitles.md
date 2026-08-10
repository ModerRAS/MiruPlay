# Media3 libass Subtitle Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render ASS/SSA through libass in the normal Media3 player while preserving authored styling, effects, timing, positioning, and Matroska fonts.

**Architecture:** Media3 continues to own playback and selects a custom raw ASS `BaseRenderer`. A per-player session converts raw samples into libass events, receives Matroska font attachments from a `MatroskaExtractor` subclass, and renders at video presentation timestamps into a transparent media-overlay `SurfaceView`.

**Tech Stack:** Kotlin 2.0, AndroidX Media3 1.8.0, Android NDK 27, C++17/JNI, libass symbols exported by packaged `libmpv.so`, JUnit 4, Robolectric 4.12.

## Global Constraints

- Start from `origin/master` commit `c7e95e12cda778e1692a30bfbc80c46f4681d0d2` in `codex/fix-libass-subtitles`.
- Preserve the main checkout's `codex/feat-provider-file-access` branch and untracked `output/` directory.
- Do not implement crowd-based font shrinking or discard simultaneous cues.
- Respect ASS-authored borders and font sizes; add a black default outline only to non-ASS Media3 text.
- Keep SRT, WebVTT, TTML, bitmap subtitles, and non-ASS paths on Media3.
- Preserve `inflateSubtitleSampleIfNeeded` for zlib-compressed Matroska ASS samples.
- Use the packaged `libmpv.so`; do not add another libass binary or Maven dependency.
- Support both packaged ABIs: `arm64-v8a` and `armeabi-v7a`.
- Do not perform a second full-file Matroska scan or use a fixed prefix as the primary attachment strategy.
- Do not commit, push, tag, or publish unless the user explicitly asks.
- Device validation copies only LV999 S01E01 to HK1 and launches it directly; do not run the media index.

---

### Task 1: Define and Build the Native libass Bridge

**Files:**
- Modify: `player-mpv-android/build.gradle.kts`
- Create: `player-mpv-android/src/main/cpp/CMakeLists.txt`
- Create: `player-mpv-android/src/main/cpp/miruplay_libass.cpp`
- Create: `player-mpv-android/src/main/kotlin/is/xyz/mpv/subtitle/NativeAssFont.kt`
- Create: `player-mpv-android/src/main/kotlin/is/xyz/mpv/subtitle/NativeAssRenderer.kt`
- Create: `player-mpv-android/src/test/kotlin/is/xyz/mpv/subtitle/NativeAssRendererContractTest.kt`

**Interfaces:**
- Produces: `data class NativeAssFont(val name: String, val data: ByteArray)`.
- Produces: `NativeAssRenderer.isAvailable(): Boolean`.
- Produces: `NativeAssRenderer.create(document: ByteArray, fonts: List<NativeAssFont>): NativeAssRenderer?`.
- Produces instance methods `addEvent`, `flushEvents`, `render`, `clearSurface`, and `close`.

- [ ] **Step 1: Write the failing Kotlin contract tests**

Create tests that use an injected fake `NativeCalls` implementation rather than loading JNI on the host JVM. Assert that:

```kotlin
@Test
fun `create rejects unavailable native symbols`() {
    val calls = FakeNativeCalls(available = false)
    assertNull(NativeAssRenderer.create(header, emptyList(), calls))
}

@Test
fun `close releases one native handle exactly once`() {
    val calls = FakeNativeCalls(available = true, createHandle = 42L)
    val renderer = requireNotNull(NativeAssRenderer.create(header, emptyList(), calls))
    renderer.close()
    renderer.close()
    assertEquals(listOf(42L), calls.releasedHandles)
}

@Test
fun `renderer forwards every font without rewriting bytes`() {
    val font = NativeAssFont("signs.otf", byteArrayOf(0, 1, 2, -1))
    NativeAssRenderer.create(header, listOf(font), calls)
    assertArrayEquals(font.data, calls.createdFonts.single().data)
}
```

- [ ] **Step 2: Run the tests and verify RED**

```powershell
$env:ANDROID_HOME='C:\Users\ModerRAS\AppData\Local\Android\Sdk'
.\gradlew.bat :player-mpv-android:testDebugUnitTest --tests "is.xyz.mpv.subtitle.NativeAssRendererContractTest" --console=plain
```

Expected: compilation fails because `NativeAssFont` and `NativeAssRenderer` do not exist.

- [ ] **Step 3: Implement the Kotlin ownership wrapper**

Use a small `NativeCalls` interface with a production JNI implementation and an internal test overload of `create`. Synchronize `close`, store handle `0` after release, reject blank documents and unavailable symbols, and avoid finalizers.

- [ ] **Step 4: Implement the C++17 JNI bridge**

Resolve these symbols from `dlopen("libmpv.so", RTLD_NOW or RTLD_LOCAL)`:

```text
ass_library_init            ass_library_done
ass_renderer_init           ass_renderer_done
ass_read_memory             ass_free_track
ass_add_font                ass_set_fonts
ass_set_frame_size          ass_set_storage_size
ass_set_aspect_ratio        ass_set_use_margins
ass_set_font_scale          ass_set_line_spacing
ass_set_check_readorder     ass_process_data
ass_flush_events            ass_render_frame
```

Keep opaque declarations local to the C++ file. Define `ASS_Image` with the
libass ABI fields and composite every linked image into a locked
`WINDOW_FORMAT_RGBA_8888` `ANativeWindow_Buffer`. Convert libass inverse alpha
with `sourceAlpha = 255 - (color & 0xff)` and multiply it by glyph coverage.
Clear and post when a previously visible frame becomes empty. Guard each native
session with a mutex.

- [ ] **Step 5: Wire CMake for both application ABIs**

Set `externalNativeBuild.cmake.path`, C++17, and link `android`, `log`, and `dl`.
Add `-Wl,-z,max-page-size=16384`. Do not link against `libmpv.so` at build time;
runtime symbol resolution is intentional.

- [ ] **Step 6: Run contract tests and build the native AAR**

```powershell
.\gradlew.bat :player-mpv-android:testDebugUnitTest :player-mpv-android:assembleDebug --console=plain
```

Expected: tests pass and both ABI directories in the AAR contain
`libmiruplay_libass.so`.

- [ ] **Step 7: Verify packaged libmpv symbols and 16 KiB alignment**

Use NDK `llvm-readelf.exe -Ws` on both packaged `libmpv.so` files and
`llvm-readelf.exe -lW` on both built bridge files. Every required `ass_*` symbol
must be exported and each bridge `LOAD` segment alignment must be at least
`0x4000`.

---

### Task 2: Decode Raw Media3 ASS Samples Without Losing Semantics

**Files:**
- Create: `player-core/src/main/kotlin/com/miruplay/tv/player/LibassSubtitleSample.kt`
- Create: `player-core/src/test/kotlin/com/miruplay/tv/player/LibassSubtitleSampleTest.kt`
- Reuse: `player-core/src/main/kotlin/com/miruplay/tv/player/ZlibSubtitleExtractor.kt`

**Interfaces:**
- Produces sealed `LibassPayload.Document(bytes)` and `LibassPayload.Event(dialogueLine)`.
- Produces `decodeLibassPayload(sample: ByteArray, sampleTimeUs: Long): LibassPayload?`.
- Produces `assHeaderFrom(format: Format): ByteArray?`.

- [ ] **Step 1: Write failing Media3-prefix tests**

Use a sample shaped exactly like Media3 raw Matroska SSA output:

```text
Dialogue: 0:00:00:00,0:00:02:50,17,4,Sign,Actor,0000,0000,0000,,{\pos(300,200)\fs34\bord3\c&H33AAFF&}LV999, sign
```

At `sampleTimeUs = 7_070_000`, assert the output is:

```text
Dialogue: 4,0:00:07.07,0:00:09.57,Sign,Actor,0000,0000,0000,,{\pos(300,200)\fs34\bord3\c&H33AAFF&}LV999, sign
```

The read-order field `17` is removed, while the layer, commas in text,
override tags, UTF-8 bytes, and 2.50-second duration are preserved.

- [ ] **Step 2: Add failing document, malformed, and zlib tests**

Assert a full `[Script Info]` plus `[Events]` sample returns `Document` unchanged.
Assert malformed prefixes return null. Compress an LV999-style event body with
`Deflater`, prepend the Media3 dialogue prefix, run it through the existing byte
protection helper, and assert decoding produces the same absolute event as the
plain sample.

- [ ] **Step 3: Run the tests and verify RED**

```powershell
.\gradlew.bat :player-core:testDebugUnitTest --tests "com.miruplay.tv.player.LibassSubtitleSampleTest" --console=plain
```

Expected: compilation fails because the payload decoder does not exist.

- [ ] **Step 4: Implement the smallest decoder**

First call `inflateSubtitleSampleIfNeeded`. Detect full documents from ASS
section headers. For Matroska samples, split after `Dialogue:` with `limit = 11`,
parse relative start/end accepting both `.` and `:` before centiseconds, compute
duration, and format absolute times with `Locale.US`. Reject negative duration,
fewer than eleven fields, invalid UTF-8 replacement at structural fields, and
samples above the existing inflated-byte cap.

- [ ] **Step 5: Verify GREEN**

Run the focused command from Step 3. Expected: all payload tests pass.

---

### Task 3: Add the Per-Player Session and Selected ASS Renderer

**Files:**
- Create: `player-core/src/main/kotlin/com/miruplay/tv/player/LibassSubtitleSession.kt`
- Create: `player-core/src/main/kotlin/com/miruplay/tv/player/NativeAssTextRenderer.kt`
- Create: `player-core/src/main/kotlin/com/miruplay/tv/player/LibassSubtitleRegistry.kt`
- Create: `player-core/src/main/kotlin/com/miruplay/tv/player/LibassTextRenderers.kt`
- Modify: `player-core/src/main/kotlin/com/miruplay/tv/player/DspRenderersFactory.kt`
- Modify: `player-core/src/main/kotlin/com/miruplay/tv/player/ExperimentalRenderersFactory.kt`
- Create: `player-core/src/test/kotlin/com/miruplay/tv/player/LibassSubtitleSessionTest.kt`
- Create: `player-core/src/test/kotlin/com/miruplay/tv/player/NativeAssTextRendererTest.kt`

**Interfaces:**
- Produces `LibassSubtitleSession.beginMedia(): Long`, `setHeader`, `acceptPayload`, `addFont`, `onSeek`, `activate`, `deactivate`, `bindSurface`, `unbindSurface`, and `close`.
- Produces `LibassSubtitleRegistry.register(player, session)`, `sessionFor(player)`, and `release(player)`.
- `LibassSubtitleSession` implements `VideoFrameMetadataListener`.

- [ ] **Step 1: Write failing session-generation tests**

With a fake native-renderer factory and fake render dispatcher, assert:

```kotlin
val oldGeneration = session.beginMedia()
session.setHeader(oldGeneration, header)
val newGeneration = session.beginMedia()
session.acceptPayload(oldGeneration, event)
assertTrue(fakeRenderer.events.isEmpty())
session.acceptPayload(newGeneration, event)
assertEquals(listOf(event.dialogueLine), fakeRenderer.events)
```

Also assert seek flushes events, a late font recreates the renderer and replays
only current-generation events, duplicate fonts are ignored by name and bytes,
and close is idempotent.

- [ ] **Step 2: Write failing timestamp-coalescing tests**

Queue PTS values 1, 2, and 3 before the fake render dispatcher runs. Assert only
the latest value is rendered, decoded video dimensions are passed as storage
size, and no draw occurs without an active ASS track and valid surface.

- [ ] **Step 3: Write failing renderer capability tests**

Assert `NativeAssTextRenderer.supportsFormat` returns handled only for
`MimeTypes.TEXT_SSA` when native preflight is true, unsupported subtype for
other text, unsupported type for video, and unsupported subtype for SSA when
preflight is false. Exercise `onPositionReset` and `onDisabled` through an
internal renderer-state helper so tests do not mock `BaseRenderer` internals.

- [ ] **Step 4: Run focused tests and verify RED**

```powershell
.\gradlew.bat :player-core:testDebugUnitTest --tests "com.miruplay.tv.player.LibassSubtitleSessionTest" --tests "com.miruplay.tv.player.NativeAssTextRendererTest" --console=plain
```

- [ ] **Step 5: Implement session ownership and rendering**

Use a lazily started `HandlerThread` for native drawing. Store the latest PTS in
an atomic and allow at most one queued draw. Keep document/header, fonts, and
current events behind a lock; do not invoke JNI while holding the registry
lock. Recreate the native renderer when the document or font set changes.

- [ ] **Step 6: Implement `NativeAssTextRenderer`**

Extend `BaseRenderer(C.TRACK_TYPE_TEXT)`. On stream change install ASS codec
private data from `Format.initializationData`. Drain `DecoderInputBuffer` data,
call `decodeLibassPayload`, and forward only the current generation. Reset end
state and flush on seek. Deactivate and clear on disable. Return `isReady = true`
and `isEnded` only after the input end marker.

- [ ] **Step 7: Put native ASS before legacy Media3 text**

Add a shared helper used by both renderer factories:

```kotlin
out += NativeAssTextRenderer(session)
out += TextRenderer(output, outputLooper).apply {
    experimentalSetLegacyDecodingEnabled(true)
}
```

Do not duplicate renderer policy between standard and experimental factories.

- [ ] **Step 8: Verify GREEN and the existing audio renderer tests**

```powershell
.\gradlew.bat :player-core:testDebugUnitTest --tests "com.miruplay.tv.player.LibassSubtitleSessionTest" --tests "com.miruplay.tv.player.NativeAssTextRendererTest" --tests "com.miruplay.tv.player.DspRenderersFactoryTest" --console=plain
```

If the named existing DSP test class is absent, run the complete
`:player-core:testDebugUnitTest` task rather than creating an empty substitute.

---

### Task 4: Extract Matroska Font Attachments in the Playback Pass

**Files:**
- Create: `player-core/src/main/kotlin/com/miruplay/tv/player/LibassMatroskaExtractor.kt`
- Modify: `player-core/src/main/kotlin/com/miruplay/tv/player/ZlibSubtitleExtractor.kt`
- Create: `player-core/src/test/kotlin/com/miruplay/tv/player/LibassMatroskaExtractorTest.kt`
- Modify: `player-core/src/test/kotlin/com/miruplay/tv/player/ZlibSubtitleSampleTest.kt`

**Interfaces:**
- Produces `LibassMatroskaExtractor(onFont: (NativeAssFont) -> Unit)`.
- `ZlibSubtitleExtractorsFactory` accepts an optional session/native availability and replaces only Media3 `MatroskaExtractor` instances when native ASS is available.

- [ ] **Step 1: Write failing attachment collector tests**

Build small EBML fixtures containing `Attachments` (`0x1941A469`),
`AttachedFile` (`0x61A7`), `FileName` (`0x466E`), `FileMimeType` (`0x4660`),
and `FileData` (`0x465C`). Assert `.ttf`, `.otf`, `.ttc`, and `.otc` fonts are
published with exact bytes regardless of whether MIME or filename supplies the
font hint. Assert an image attachment is ignored.

- [ ] **Step 2: Add failing abuse-limit and ordering tests**

Assert data arriving before name/MIME is retained until `AttachedFile` ends,
duplicate end callbacks do not republish, oversized `FileData` is skipped via
`ExtractorInput.skipFully`, and count/aggregate limits stop collection without
stopping normal extraction.

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
.\gradlew.bat :player-core:testDebugUnitTest --tests "com.miruplay.tv.player.LibassMatroskaExtractorTest" --console=plain
```

- [ ] **Step 4: Implement the `MatroskaExtractor` subclass**

Override `getElementType`, `isLevel1Element`, `startMasterElement`,
`stringElement`, `binaryElement`, and `endMasterElement` only for attachment
IDs, delegating every other ID to `super`. Use `EbmlProcessor` element-type
constants, per-font maximum 32 MiB, aggregate maximum 128 MiB, and maximum 64
font files. Reset partial attachment state on seek/release.

- [ ] **Step 5: Enable raw Matroska text conditionally**

When `NativeAssRenderer.isAvailable()` is true, configure
`MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA` and replace the default
Matroska extractor with `LibassMatroskaExtractor`. When false, retain the exact
existing subtitle-transcoding factory so Media3 remains the fallback. Preserve
the zlib parser factory for the fallback path.

- [ ] **Step 6: Verify GREEN and zlib regression coverage**

```powershell
.\gradlew.bat :player-core:testDebugUnitTest --tests "com.miruplay.tv.player.LibassMatroskaExtractorTest" --tests "com.miruplay.tv.player.ZlibSubtitleSampleTest" --tests "com.miruplay.tv.player.ZlibSubtitleDataSourceTest" --console=plain
```

---

### Task 5: Wire Sessions to ExoPlayer and Playback Lifecycle

**Files:**
- Modify: `player-core/src/main/kotlin/com/miruplay/tv/player/DiModule.kt`
- Modify: `player-core/src/main/kotlin/com/miruplay/tv/player/ExoPlaybackController.kt`
- Modify: `player-core/src/main/kotlin/com/miruplay/tv/player/PlaybackController.kt`
- Modify: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/PlayerViewModel.kt`
- Create: `player-core/src/test/kotlin/com/miruplay/tv/player/LibassPlayerLifecycleTest.kt`

**Interfaces:**
- `PlaybackController.getLibassSubtitleSession(): LibassSubtitleSession?` returns null for non-Exo backends.
- `PlayerViewModel.getLibassSubtitleSession()` delegates to the controller.

- [ ] **Step 1: Write failing lifecycle tests**

Assert provider construction gives each standard/experimental ExoPlayer a
different session, while that player's renderer factory and extractor factory
receive the same session. Assert playback start calls `beginMedia` before
`setMediaItem`, inactive players are cleared, and release unregisters and closes
both sessions.

- [ ] **Step 2: Run tests and verify RED**

```powershell
.\gradlew.bat :player-core:testDebugUnitTest --tests "com.miruplay.tv.player.LibassPlayerLifecycleTest" --console=plain
```

- [ ] **Step 3: Wire each provider**

Create one session inside each Exo provider method. Pass it to the renderer
factory and `ZlibSubtitleExtractorsFactory`, build the player, register the
pair, and install the session as `VideoFrameMetadataListener` only when native
preflight succeeds.

- [ ] **Step 4: Reset and release by active player**

Immediately before building a new `MediaItem`, resolve the selected player's
session and call `beginMedia`. On controller release, clear the video metadata
listener, unregister, and close the session before releasing that player.

- [ ] **Step 5: Verify GREEN and track-selection regressions**

```powershell
.\gradlew.bat :player-core:testDebugUnitTest --console=plain
```

Expected: all player-core tests pass; existing manual subtitle selection and
preferred-language tests remain unchanged because Media3 still selects tracks.

---

### Task 6: Install the Transparent Overlay and Readable Non-ASS Outline

**Files:**
- Create: `player-core/src/main/kotlin/com/miruplay/tv/player/LibassSubtitleSurfaceView.kt`
- Modify: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/PlayerScreen.kt`
- Modify: `ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/PlayerSubtitleStyleTest.kt`
- Create: `ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/LibassSubtitleOverlayLifecycleTest.kt`

**Interfaces:**
- Produces `LibassSubtitleSurfaceView.bind(session)` and `unbind()`.
- Produces PlayerView helpers that install one overlay under `exo_content_frame` and detach it on release.

- [ ] **Step 1: Write failing non-ASS outline tests**

Assert `subtitleCaptionStyle` converts `EDGE_TYPE_NONE` to
`EDGE_TYPE_OUTLINE` with `Color.BLACK`, preserving foreground, background,
window, and typeface. Assert an explicit system caption edge type/color is not
overridden. Assert transparent background changes only background color.

- [ ] **Step 2: Write failing overlay lifecycle tests**

Inflate the real `player_view_surface` layout with Robolectric. Install twice
and assert `exo_content_frame` contains exactly one
`LibassSubtitleSurfaceView`. Release the view and assert the session has no
bound surface and the ordinary `SubtitleView` still exists.

- [ ] **Step 3: Run tests and verify RED**

```powershell
.\gradlew.bat :ui-tv:testDebugUnitTest --tests "com.miruplay.tv.ui.player.PlayerSubtitleStyleTest" --tests "com.miruplay.tv.ui.player.LibassSubtitleOverlayLifecycleTest" --console=plain
```

- [ ] **Step 4: Implement the surface view**

Set `holder.setFormat(PixelFormat.TRANSLUCENT)`,
`setZOrderMediaOverlay(true)`, transparent background, and non-focusable/non-
clickable flags. Forward `surfaceCreated`, `surfaceChanged`, and
`surfaceDestroyed` to the session. `unbind` must clear the visible surface and
remove callbacks idempotently.

- [ ] **Step 5: Install it in `PlayerView`**

Add the overlay as the last child of `androidx.media3.ui.R.id.exo_content_frame`
in the standard PlayerView factory, bind the current Exo session during update,
and unbind it before `releasePlayerView`. Do not put the overlay in a Compose
card or separate window. Do not hide or replace Media3's `SubtitleView`.

- [ ] **Step 6: Apply the black fallback outline**

When the resolved Android caption style has no edge, construct a style with
`CaptionStyleCompat.EDGE_TYPE_OUTLINE` and `Color.BLACK`. ASS does not use this
style because the native renderer receives the selected ASS stream.

- [ ] **Step 7: Verify GREEN and all ui-tv subtitle tests**

```powershell
.\gradlew.bat :ui-tv:testDebugUnitTest --tests "com.miruplay.tv.ui.player.PlayerSubtitleStyleTest" --tests "com.miruplay.tv.ui.player.LibassSubtitleOverlayLifecycleTest" --tests "com.miruplay.tv.ui.player.SubtitleCueLayoutTest" --tests "com.miruplay.tv.ui.player.SubtitleCueLv999FixtureTest" --tests "com.miruplay.tv.ui.player.SubtitleTransformingPlayerTest" --console=plain
```

---

### Task 7: Full Verification and HK1 LV999 Acceptance

**Files:**
- Verify all intended files only.
- Store transient APKs, logs, and screenshots under the existing ignored `output/` directory in the isolated worktree.

**Interfaces:**
- Consumes the complete Tasks 1-6 implementation.
- Produces automated verification plus real HK1 HDMI evidence.

- [ ] **Step 1: Run formatting/diff checks and focused modules**

```powershell
git diff --check
.\gradlew.bat :player-mpv-android:testDebugUnitTest :player-core:testDebugUnitTest :ui-tv:testDebugUnitTest --console=plain
```

- [ ] **Step 2: Run the repository verification gates**

```powershell
.\gradlew.bat test --no-build-cache --console=plain
.\gradlew.bat lint --no-build-cache --console=plain
.\gradlew.bat :app:assembleDebug --no-build-cache --console=plain
```

Every command must exit 0. Do not describe a command as passed from partial or
stale output.

- [ ] **Step 3: Read and follow the `miruplay-adb-debug` skill**

Use direct ADB if the helper misreports HK1. Confirm device identity before
installing. Do not uninstall the app or clear its data.

- [ ] **Step 4: Copy only LV999 S01E01**

Resolve one episode from `S:\动漫\LV999 no Murabito\Season 1`, create only
`/sdcard/Movies/MiruPlaySubtitleTest`, and push that one file as
`LV999-S01E01-libass.mkv`. Do not invoke MiruPlay's scanner or index APIs.

- [ ] **Step 5: Install and launch directly at the dense sign scene**

```powershell
adb connect 192.168.63.237:5555
adb -s 192.168.63.237:5555 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 192.168.63.237:5555 shell am force-stop com.miruplay.tv
adb -s 192.168.63.237:5555 shell am start -n com.miruplay.tv/.MainActivity --es test_playback_uri file:///sdcard/Movies/MiruPlaySubtitleTest/LV999-S01E01-libass.mkv --es test_playback_media_source_id lv999-libass-test --es test_playback_start_position_ms 7070
```

- [ ] **Step 6: Verify native activation and clean playback logs**

Filter logcat for `MiruLibass`, `NativeAssTextRenderer`, `SsaParser`, fatal JNI,
and playback errors. Confirm native preflight, selected ASS activation, font
attachment count, and rendered frames. There must be no repeated SSA parser
warning on the selected ASS track and no native crash.

- [ ] **Step 7: Capture HDMI evidence at both acceptance scenes**

Through NanoKVM at `http://192.168.63.219`, capture the video at about
`00:07.07` and `00:42.56`. At `00:07.07`, verify all twelve positioned signs
retain visibly distinct authored sizes rather than all becoming size 70. At
`00:42.56`, verify bilingual dialogue and moving signs remain readable and in
motion. Check controls hidden and visible, and confirm the overlay is above the
video without hiding player controls.

- [ ] **Step 8: Inspect final scope**

```powershell
git status --short
git diff --stat origin/master...HEAD
git diff --check
```

The only changes must be the libass subtitle implementation, its tests, and the
two design/plan documents. Leave all work unstaged and uncommitted unless the
user requests publication.
