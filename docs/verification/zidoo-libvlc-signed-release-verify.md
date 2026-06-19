# Zidoo libVLC Signed Release Verification

## Scope

This document records repository-local evidence for the Zidoo Z9X 8K signed-release libVLC recovery work.

## Code changes tied to the recovery

- Preserve WebUI source fields so local drama/DV sources keep `contentMode` and `disableOnlineMetadata`:
  - `web-control-core/src/main/kotlin/com/miruplay/tv/webcontrol/WebControlModels.kt`
  - `web-control-core/src/main/kotlin/com/miruplay/tv/webcontrol/WebControlSourceRequests.kt`
- Require a valid output-callback `Surface` before treating the host as attach-ready:
  - `player-core/src/main/kotlin/com/miruplay/tv/player/HybridPlaybackController.kt`
  - `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/LibVlcImageReaderOutputView.kt`
  - `ui-tv/src/main/kotlin/com/miruplay/tv/ui/player/LibVlcTextureVideoHostView.kt`
- Default signed release libVLC vout to `GL_SURFACE` and skip startup probe:
  - `app/src/main/kotlin/com/miruplay/tv/MainActivity.kt`

## Signed releases observed

Repository tags fetched locally include:

- `v0.4.492`
- `v0.4.493`
- `v0.4.494`

## Device-side outcomes

### `OUTPUT_CALLBACKS` finding

On signed `v0.4.493`, libVLC `OUTPUT_CALLBACKS` is not viable on Zidoo:

- libVLC creates `LibVLC` and `MediaPlayer`
- the native output-callback bridge attach returns an empty handle
- playback falls into repeated `bindExistingVlcHost` loops and the player stays black at `00:00`

This is why signed releases now default to `GL_SURFACE`.

### HDR10 success

On signed `v0.4.494` clean install:

- backend: `EXPERIMENTAL_LIBVLC`
- vout: default release `GL_SURFACE`
- sample: local HDR10 `雨霖铃` episode
- API result: `Playing`, advancing `positionMs`, non-zero `durationMs`
- signal/rule result: `currentSignalKind=HDR10`, `currentRuleKey=HDR10`

Evidence:

- `docs/verification/evidence/v0494-hdr10-debug-config.json`
- `docs/verification/evidence/v0494-hdr10-status.json`
- `docs/verification/evidence/v0494-hdr10-playback.png`

### Dolby Vision success

On signed `v0.4.494` clean install:

- backend: `EXPERIMENTAL_LIBVLC`
- vout: default release `GL_SURFACE`
- sample: `Bloom.Life.S01E01.2026.2160p.WEB-DL.DV.H.265.DDP5.1.mp4`
- API result: `Playing`, advancing `positionMs`, non-zero `durationMs`
- signal/rule result: `currentSignalKind=DOLBY_VISION`, `currentRuleKey=DOLBY_VISION`
- overlay evidence shows `Dolby Vision`

Evidence:

- `docs/verification/evidence/v0494-dolby-vision-debug-config.json`
- `docs/verification/evidence/v0494-dolby-vision-status.json`
- `docs/verification/evidence/v0494-dolby-vision-library.json`
- `docs/verification/evidence/v0494-dolby-vision-playback.png`

## SDR mapping basis

The active libVLC path is not just detecting HDR/DV; it applies the HDR/DV rule plumbing that targets SDR-style output in code:

- HDR10/HDR10+/Dolby Vision default rule sets target `targetSdrNits = 120` in `core/model/src/main/kotlin/com/miruplay/tv/model/ToneMappingModels.kt`
- libVLC option mapping requests BT.709 primaries and BT.1886 transfer when tone mapping is applied in `player-core/src/main/kotlin/com/miruplay/tv/player/HybridPlaybackController.kt`

The device-side evidence above confirms those HDR10/DV rule keys are active during successful signed-release playback on Zidoo.

## Reusable skill

A repository-local skill for repeating this signed-release validation now lives at `skills/zidoo-signed-release-libvlc/SKILL.md`.
