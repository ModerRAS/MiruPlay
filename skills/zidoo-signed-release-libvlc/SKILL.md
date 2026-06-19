---
name: zidoo-signed-release-libvlc
summary: Verify signed MiruPlay releases with libVLC on the Zidoo Z9X 8K.
when_to_use: Use when reproducing or validating signed-release playback on the Zidoo box, especially for HDR10 and Dolby Vision samples, WebUI token bootstrap, local source setup, and libVLC backend checks.
---

# Zidoo Signed Release libVLC

## Preconditions

- Use signed GitHub releases only.
- Verify install with `Apps/getApps`, not the flaky `:18888` installer response.
- Enable MiruPlay WebUI from the on-device settings page and decode the QR token from NanoKVM capture.

## Procedure

1. Uninstall `com.miruplay.tv` when a clean repro is required.
2. Install the signed APK through Zidoo Samba + `ZidooFileControl/openFile`.
3. Launch MiruPlay, navigate `Up -> Right -> Ok` into settings, enable `WebUI`, and decode the QR token.
4. Add local sources with:
   - `contentMode=DRAMA`
   - `disableOnlineMetadata=true`
5. Scan the exact source you plan to test.
6. For libVLC verification, set:
   - `defaultBackend=EXPERIMENTAL_LIBVLC`
   - `requestedBackend=EXPERIMENTAL_LIBVLC`
   - release default `libVlcVoutMode=GL_SURFACE`
   - `skipLibVlcStartupProbe=true`
7. Play the target episode and confirm:
   - `playback-status.state == Playing`
   - `positionMs > 0`
   - `durationMs > 0`
   - `currentSignalKind` / `currentRuleKey` match the sample (`HDR10` or `DOLBY_VISION`)
8. Capture NanoKVM evidence of the live video and overlay.

## Known Findings

- `GL_SURFACE` is the verified working signed-release libVLC path on Zidoo.
- `OUTPUT_CALLBACKS` is still not a primary Zidoo path; the native output-callback bridge can return an empty handle. Player code now falls back to `GL_SURFACE` when that happens, but validation should still treat `GL_SURFACE` as the intended steady state.
- If WebUI source creation drops `contentMode` or `disableOnlineMetadata`, drama/DV sources may fall back to `ANIME` and stall in Bangumi enrichment before playback validation.

## Verification

- HDR10 signed-release evidence exists in `docs/verification/evidence/v0494-hdr10-*`.
- Dolby Vision signed-release evidence exists in `docs/verification/evidence/v0494-dolby-vision-*`.
- Workflow summary exists in `docs/workflows/zidoo-signed-release-libvlc-verify.md`.
