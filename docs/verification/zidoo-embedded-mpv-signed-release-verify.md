# Zidoo Embedded mpv Signed Release Verification

## Scope

This document records repository-local evidence for the MiruPlay embedded `libmpv` signed-release verification work on the Zidoo Z9X 8K.

## Code changes tied to the recovery

- Restore dynamic HDR/SDR release verification on the embedded `libmpv` path:
  - `player-core/src/main/kotlin/com/miruplay/tv/player/EmbeddedMpvSessionOptions.kt`
  - `player-core/src/test/kotlin/com/miruplay/tv/player/EmbeddedMpvSessionOptionsTest.kt`
- Avoid redundant embedded `mpv` runtime option reapply churn:
  - `player-mpv-android/src/main/kotlin/is/xyz/mpv/MiruMpvSurfaceView.kt`
- Wait for host readiness before deferring embedded `mpv` load:
  - `player-core/src/main/kotlin/com/miruplay/tv/player/ExoPlaybackController.kt`
  - `player-core/src/test/kotlin/com/miruplay/tv/player/EmbeddedMpvHostReadyTest.kt`
- Defer `loadfile` until `surfaceCreated()` has really attached the playback surface:
  - `player-mpv-android/src/main/kotlin/is/xyz/mpv/BaseMPVView.kt`
  - `player-mpv-android/src/main/kotlin/is/xyz/mpv/MiruMpvSurfaceView.kt`
  - `player-mpv-android/src/test/kotlin/is/xyz/mpv/BaseMPVViewTest.kt`

## Signed releases observed

Repository tags fetched locally include:

- `v0.6.505`
- `v0.6.507`
- `v0.6.508`
- `v0.6.509`

Install proof for the final clean verification run is recorded at:

- `docs/verification/evidence/v0609-install-proof.json`

GitHub release provenance for the final fix:

- tag: `v0.6.509`
- commit: `17d011592c5b9296a8f87d87793b756bec31819d`
- commit subject: `fix: defer embedded mpv load until surface attach`
- APK digest: `sha256:cf21a9748a54b5c4fad3fc85e55fa2c8067abb1332d0ae447dc9468e0ba061de`

## Device-side outcomes

### `v0.6.508` split result

On signed `v0.6.508`, the embedded `mpv` path was still split on Zidoo:

- `SDR` recovered and could play with advancing `positionMs`
- `HDR` regressed to black screen with `state=Playing`, `durationMs=0`, and no visible video

This regression motivated the final surface-attach gating fix in `17d01159`.

### HDR success on `v0.6.509`

On signed `v0.6.509` clean install:

- backend: `EXPERIMENTAL_MPV_EMBEDDED`
- signal: forced `HDR10`
- sample: local `雨霖铃.S01E08.2026.2160p.HQ.WEB-DL.HDR.60fps.H265.10bit.AAC.mp4`
- API result: `Playing`, advancing `positionMs`, non-zero `durationMs`
- seek result: after seek to `300000`, playback advanced through `321284 -> 326284`
- visual result: NanoKVM shows live video after the seek probe

Evidence:

- `docs/verification/evidence/v0609-hdr-debug-config.json`
- `docs/verification/evidence/v0609-hdr-status.json`
- `docs/verification/evidence/v0609-hdr-playback.png`

### SDR success on `v0.6.509`

After a clean uninstall/reinstall of the same signed `v0.6.509` build and a fresh WebUI/source rebuild:

- backend: `EXPERIMENTAL_MPV_EMBEDDED`
- signal: forced `SDR`
- sample: local `主角/S01E01.2026.2160p.WEB-DL.H265.DDP5.1.mkv`
- API result: `Playing`, advancing `positionMs`, non-zero `durationMs`
- seek result: after seek to `300000`, playback advanced through `300240 -> 304560`
- visual result: NanoKVM shows live video on the clean SDR-only run

Evidence:

- `docs/verification/evidence/v0609-sdr-debug-config.json`
- `docs/verification/evidence/v0609-sdr-status.json`
- `docs/verification/evidence/v0609-sdr-playback.png`

## Final conclusion

The signed embedded-`mpv` Zidoo recovery is complete on `v0.6.509`:

- `HDR` and `SDR` both pass on signed GitHub release APKs
- both paths require more than `state=Playing`; the final accepted evidence includes advancing `positionMs`, non-zero `durationMs`, and visible NanoKVM video
- the last blocking race was surface lifecycle timing, not tone-mapping math

## Reusable skill

A repository-local skill for repeating this signed-release validation now lives at `skills/zidoo-signed-release-embedded-mpv/SKILL.md`.
