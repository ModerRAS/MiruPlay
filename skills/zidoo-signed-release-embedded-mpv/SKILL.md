---
name: zidoo-signed-release-embedded-mpv
summary: Verify signed MiruPlay releases with embedded libmpv on the Zidoo Z9X 8K.
when_to_use: Use when reproducing or validating signed-release embedded mpv playback on the Zidoo box, especially for HDR and SDR samples, WebUI token bootstrap, local source rebuilds, and final clean completion audits.
---

# Zidoo Signed Release Embedded mpv

## Preconditions

- Use signed GitHub releases only.
- Verify install with `Apps/getApps`, not the flaky installer response.
- Enable MiruPlay `WebUI` from the on-device settings page and decode the QR token from NanoKVM capture.
- For final conclusions, prefer uninstall/reinstall so the verification run is clean.

## Procedure

1. Uninstall `com.miruplay.tv` when a clean repro is required.
2. Install the signed APK through Zidoo Samba + `ZidooFileControl/openFile`.
3. Launch MiruPlay, navigate `Up -> Right -> Ok` into settings, enable `WebUI`, and decode the QR token.
4. Add narrow local sources with:
   - `contentMode=DRAMA`
   - `disableOnlineMetadata=true`
5. Scan the exact source you plan to test.
6. For embedded `mpv` verification, set:
   - `defaultBackend=EXPERIMENTAL_MPV_EMBEDDED`
   - `requestedBackend=EXPERIMENTAL_MPV_EMBEDDED`
   - `forcedSignalKind=HDR10` for HDR probes, `SDR` for SDR probes
7. Play the target episode and confirm:
   - `playback-status.state == Playing`
   - `positionMs > 0`
   - `durationMs > 0`
   - seek to `300000` continues to advance afterwards
   - `currentSignalKind` / `currentRuleKey` match the sample
8. Capture NanoKVM evidence of the live video.
9. If you changed install state during the run, redo token bootstrap and source rebuild before the final clean verdict.

## Known Findings

- `v0.6.508` was a split build: SDR recovered, HDR regressed to black.
- `v0.6.509` is the first clean signed build where both HDR and SDR pass again.
- The last blocker was surface lifecycle timing: `loadfile` must wait for actual `surfaceCreated()` / surface attach, not just a superficially valid `Surface` object.
- On Windows Git Bash, avoid passing `/sdcard/...` through shell CLI args for WebUI local-source creation because it can be rewritten to `C:/Program Files/Git/...`.

## Verification

- Signed `v0.6.509` install proof exists in `docs/verification/evidence/v0609-install-proof.json`.
- HDR signed-release evidence exists in `docs/verification/evidence/v0609-hdr-*.json` and `docs/verification/evidence/v0609-hdr-playback.png`.
- SDR signed-release evidence exists in `docs/verification/evidence/v0609-sdr-*.json` and `docs/verification/evidence/v0609-sdr-playback.png`.
- Workflow summary exists in `docs/workflows/zidoo-signed-release-embedded-mpv-verify.md`.
