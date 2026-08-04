# Media3 Subtitle Layout Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make dense bilingual Media3 subtitles readable without overlap, clipping, cue loss, or stale PlayerView callbacks.

**Architecture:** Transform cues at a `ForwardingPlayer` boundary so Media3's built-in `PlayerView` is the sole subtitle writer. Restack only contiguous ordinary dialogue into a measured rich-text block, preserve authored special cues, and drive bottom padding from player-control visibility.

**Tech Stack:** Kotlin 2.0, AndroidX Media3 1.8.0, Compose AndroidView, JUnit 4, Robolectric 4.12.1, MockK.

## Global Constraints

- Work on `fix/subtitle-bilingual-overlap` and update existing PR #59 against `master`.
- Preserve unrelated `.gitignore`, `.worktree/`, `output/`, zlib, WebDAV, and player-core work.
- Do not change mpv/libass subtitle behavior.
- Do not cap or silently drop unique ordinary dialogue cues.
- Preserve clearly positioned, bitmap, vertical, sheared, windowed, and conflicting layered cues.
- Use the built-in `PlayerView` subtitle layer; do not add an external overlay.
- Validate LV999 S01E02 at `02:32` and `03:27` on HK1 through NanoKVM before publishing.

---

### Task 1: Restack Ordinary Dialogue Without Data Loss

**Files:**
- Modify: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/SubtitleCueLayout.kt`
- Modify: `ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/SubtitleCueLayoutTest.kt`
- Modify: `ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/SubtitleCueLv999FixtureTest.kt`

**Interfaces:**
- Consumes: `List<Cue>` in Media3 display order.
- Produces: `internal fun restackSubtitleCues(cues: List<Cue>): List<Cue>` with no maximum-cue parameter.

- [ ] **Step 1: Write failing no-loss and bottom-safe tests**

Add tests equivalent to:

```kotlin
@Test
fun `duplicates beyond four do not hide earlier unique dialogue`() {
    val result = restackSubtitleCues(
        listOf(cue("A"), cue("A"), cue("B"), cue("B"), cue("C"), cue("D"), cue("E")),
    )

    assertEquals("A\nB\nC\nD\nE", result.single().text.toString())
}

@Test
fun `bottom dialogue leaves line unset for SubtitleView safe padding`() {
    val result = restackSubtitleCues(listOf(cue("JP"), cue("CN"))).single()

    assertEquals(Cue.DIMEN_UNSET, result.line, 0f)
    assertEquals(Cue.TYPE_UNSET, result.lineType)
    assertEquals(Cue.TYPE_UNSET, result.lineAnchor)
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
.\gradlew.bat :ui-tv:testDebugUnitTest --tests "com.miruplay.tv.ui.player.SubtitleCueLayoutTest" --console=plain
```

Expected failures: the old implementation drops `A`, keeps only four cues, and sets bottom line `-1` with numeric line type.

- [ ] **Step 3: Write failing style and preservation tests**

Create JP and CN cues with text sizes `0.06f` and `0.045f`, both using `Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING`. Assert the merged cue uses base `0.06f` and the CN range has one `RelativeSizeSpan` with `sizeChange == 0.75f`. Retain the existing bold span assertion.

Add identity assertions for bitmap, non-default positioned, vertical, sheared, and windowed cues. Add a same-text pair with different `ForegroundColorSpan` colors and assert the pair is returned unchanged rather than converted into duplicate lines.

- [ ] **Step 4: Run the tests and verify RED**

Run the same focused command. Expected failures: later cue-level text sizes are lost and special/conflicting cues are currently grouped too broadly.

- [ ] **Step 5: Implement contiguous conservative dialogue runs**

Replace global grouping and `takeLast` with a single ordered scan:

```kotlin
internal fun restackSubtitleCues(cues: List<Cue>): List<Cue> = buildList(cues.size) {
    var index = 0
    while (index < cues.size) {
        val key = cues[index].dialogueRunKey()
        if (key == null) {
            add(cues[index++])
            continue
        }
        val end = cues.indexOfFirstAfter(index) { it.dialogueRunKey() != key }
        addAll(mergeDialogueRun(cues.subList(index, end)))
        index = end
    }
}
```

Use a local loop for `indexOfFirstAfter`; do not add a general collection abstraction. `dialogueRunKey` must reject blank text, bitmap, explicit size, window color, vertical type, non-zero shear, and non-default position. Include both text alignments in the key.

Deduplicate by a span-aware visual signature that excludes only `zIndex`. If equal text has unequal visual signatures, return the original run unchanged. Merge all remaining unique cues. For compatible specified text sizes, set the largest base size and apply `RelativeSizeSpan(cue.textSize / baseSize)` to each appended range. Preserve original text spans through `SpannableStringBuilder.append`.

For bottom runs, call all three builder operations:

```kotlin
setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
setLineAnchor(Cue.TYPE_UNSET)
setZIndex(run.maxOf(Cue::zIndex))
```

- [ ] **Step 6: Update the LV999 parser fixture**

Use Layer 5/6 ordinary JP/CN events and distinct JP/CN style font sizes. Assert every unique line survives, font ratios remain, and the positioned sign object is preserved.

- [ ] **Step 7: Run focused tests and verify GREEN**

```powershell
.\gradlew.bat :ui-tv:testDebugUnitTest --tests "com.miruplay.tv.ui.player.SubtitleCueLayoutTest" --tests "com.miruplay.tv.ui.player.SubtitleCueLv999FixtureTest" --console=plain
```

Expected: both test classes pass with zero failures.

- [ ] **Step 8: Commit Task 1 files only**

```powershell
git add ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/SubtitleCueLayout.kt ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/SubtitleCueLayoutTest.kt ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/SubtitleCueLv999FixtureTest.kt
git commit -m "fix(player): preserve dense subtitle dialogue layout"
```

---

### Task 2: Make PlayerView the Single Subtitle Writer

**Files:**
- Create: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/SubtitleTransformingPlayer.kt`
- Create: `ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/SubtitleTransformingPlayerTest.kt`
- Create: `ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/PlayerViewSubtitleLifecycleTest.kt`
- Modify: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/PlayerScreen.kt`

**Interfaces:**
- Consumes: the real `Player` from `PlayerViewModel` and `restackSubtitleCues`.
- Produces: `SubtitleTransformingPlayer`, `releasePlayerView`, and `subtitleBottomPaddingFraction` for the standard PlayerView host.

- [ ] **Step 1: Write failing transformation tests**

Add `SubtitleTransformingPlayerTest` with a relaxed MockK delegate whose `currentCues` contains duplicate/overlapping dialogue. Assert:

```kotlin
val transformed = SubtitleTransformingPlayer(delegate).currentCues
assertEquals("JP\nCN", transformed.cues.single().text.toString())
assertEquals(123_456L, transformed.presentationTimeUs)
```

Capture the listener passed to the delegate and invoke both `onCues(List<Cue>)` and `onCues(CueGroup)`. The external listener must receive transformed cues in both cases. Model listener registration as a mutable set and assert `removeListener(original)` removes the equivalent wrapped listener.

- [ ] **Step 2: Run the transformation test and verify RED**

```powershell
.\gradlew.bat :ui-tv:testDebugUnitTest --tests "com.miruplay.tv.ui.player.SubtitleTransformingPlayerTest" --console=plain
```

Expected failure: `SubtitleTransformingPlayer` does not exist.

- [ ] **Step 3: Implement the transforming player**

Implement:

```kotlin
internal class SubtitleTransformingPlayer(
    delegate: Player,
    private val transform: (List<Cue>) -> List<Cue> = ::restackSubtitleCues,
) : ForwardingPlayer(delegate) {
    override fun getCurrentCues(): CueGroup = super.getCurrentCues().transformed()
    override fun addListener(listener: Player.Listener) =
        super.addListener(TransformingListener(listener, transform))
    override fun removeListener(listener: Player.Listener) =
        super.removeListener(TransformingListener(listener, transform))
}
```

`TransformingListener` uses Kotlin interface delegation for all non-cue callbacks, overrides both cue overloads, and implements stable `equals`/`hashCode` based on the original listener. Rebuild `CueGroup` with transformed cues and the original `presentationTimeUs`.

- [ ] **Step 4: Run the transformation test and verify GREEN**

Run the focused command from Step 2. Expected: zero failures.

- [ ] **Step 5: Write failing lifecycle and safe-area tests**

Add a Robolectric test for `releasePlayerView` that creates two `PlayerView` instances, releases the old one, and asserts the old view's player is null while the returned current reference is still the replacement.

Add behavior assertions:

```kotlin
assertEquals(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION, subtitleBottomPaddingFraction(false), 0f)
assertEquals(0.20f, subtitleBottomPaddingFraction(true), 0f)
```

- [ ] **Step 6: Run lifecycle tests and verify RED**

```powershell
.\gradlew.bat :ui-tv:testDebugUnitTest --tests "com.miruplay.tv.ui.player.PlayerViewSubtitleLifecycleTest" --console=plain
```

Expected failure: lifecycle and padding functions do not exist.

- [ ] **Step 7: Integrate the wrapper and release path**

In `PlayerScreen`, remove the standalone `DisposableEffect(player)` cue listener and every `subtitleView.post` write. Create:

```kotlin
val displayPlayer = remember(player) { player?.let(::SubtitleTransformingPlayer) }
```

Assign `displayPlayer` only to the standard `PlayerView`. Keep the real player for GL/VLC paths. Add `AndroidView(onRelease = ...)` and implement:

```kotlin
internal fun releasePlayerView(view: PlayerView, current: PlayerView?): PlayerView? {
    view.player = null
    return current.takeUnless { it === view }
}

internal fun subtitleBottomPaddingFraction(controlsVisible: Boolean): Float =
    if (controlsVisible) 0.20f else SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
```

Apply the padding fraction and caption background preference in both `factory` and `update`. In `onRelease`, assign `playerViewRef = releasePlayerView(view, playerViewRef)`.

- [ ] **Step 8: Run Task 2 and all subtitle tests**

```powershell
.\gradlew.bat :ui-tv:testDebugUnitTest --tests "com.miruplay.tv.ui.player.SubtitleTransformingPlayerTest" --tests "com.miruplay.tv.ui.player.PlayerViewSubtitleLifecycleTest" --tests "com.miruplay.tv.ui.player.SubtitleCueLayoutTest" --tests "com.miruplay.tv.ui.player.SubtitleCueLv999FixtureTest" --console=plain
```

Expected: all selected tests pass with zero failures.

- [ ] **Step 9: Commit Task 2 files only**

```powershell
git add ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/SubtitleTransformingPlayer.kt ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/PlayerScreen.kt ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/SubtitleTransformingPlayerTest.kt ui-tv/src/test/kotlin/com/miruplay/tv/ui/player/PlayerViewSubtitleLifecycleTest.kt
git commit -m "fix(player): serialize Media3 subtitle rendering"
```

---

### Task 3: Verify, Review, and Publish PR #59

**Files:**
- Verify only: all intended subtitle and plan files.
- Do not stage: `.gitignore`, `.worktree/`, `output/`, or unrelated generated artifacts.

**Interfaces:**
- Consumes: Tasks 1 and 2 commits plus local LV999 media.
- Produces: green local checks, NanoKVM screenshots, and an updated remote PR #59.

- [ ] **Step 1: Run local verification**

```powershell
.\gradlew.bat :ui-tv:testDebugUnitTest --console=plain
.\gradlew.bat test --no-build-cache --console=plain
.\gradlew.bat lint --no-build-cache --console=plain
.\gradlew.bat :app:assembleDebug --no-build-cache --console=plain
```

Every command must exit 0. If lint reports a pre-existing branch failure, inspect and fix only a verified in-scope issue before continuing.

- [ ] **Step 2: Install and launch the local LV999 fixture**

```powershell
adb connect 192.168.63.237:5555
adb -s 192.168.63.237:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.63.237:5555 shell am force-stop com.miruplay.tv
adb -s 192.168.63.237:5555 shell am start -n com.miruplay.tv/.MainActivity --es test_playback_uri file:///sdcard/Movies/MiruPlaySubtitleTest/LV999-S01E02-ASSx2.mkv --es test_playback_media_source_id lv999-subtitle-test --es test_playback_start_position_ms 152000
```

Do not clear app data or uninstall the app.

- [ ] **Step 3: Capture LV999 acceptance evidence**

Use ADB seek/launch at `152000` ms and `207000` ms. Capture NanoKVM video-only screenshots with credentials supplied through environment variables:

```powershell
node C:\Users\ModerRAS\.agents\skills\zidoo-nanokvm-control\scripts\nanokvm-capture.js --url $env:NANOKVM_URL --username $env:NANOKVM_USERNAME --password $env:NANOKVM_PASSWORD --video-only --output C:\WorkSpace\Android\MiruPlay\adb-artifacts\nanokvm-lv999-e02-0232-after.png
```

Capture controls-hidden and controls-visible states. Repeat at `207000` ms for the Layer 5/6 dense cue. Verify all unique JP/CN lines are visible, do not overlap, and stay above the viewport or visible controls.

- [ ] **Step 4: Request final code review**

Give the reviewer the design, plan, branch diff from `4fc2fb6c`, focused test evidence, and NanoKVM acceptance paths. Fix every Critical or Important finding and rerun its covering checks.

- [ ] **Step 5: Inspect and push only intended commits**

```powershell
git status --short
git diff --check origin/master...HEAD
git push -u origin fix/subtitle-bilingual-overlap
```

- [ ] **Step 6: Confirm existing PR is updated**

```powershell
gh pr view 59 --json number,state,url,headRefOid,statusCheckRollup
```

Expected: PR #59 remains open, points at the pushed commit, and reports the new checks. Update its body with the root cause, implementation, automated checks, and LV999/NanoKVM evidence if the current body omits them.
