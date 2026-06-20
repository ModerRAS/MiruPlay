# Zidoo Signed Release Embedded mpv Verify

Use this workflow when validating MiruPlay embedded `libmpv` playback on the Zidoo Z9X 8K with signed GitHub releases.

## Preconditions

- Install only signed GitHub release APKs.
- Use `Apps/getApps` on `:9529` as install proof.
- Use Zidoo Samba + `ZidooFileControl/openFile` for reliable install.
- Use the MiruPlay `WebUI` QR to bootstrap the current `9978` token; do not hardcode or persist tokens.
- For clean conclusions, prefer uninstall/reinstall before the final serial verification run.

## Install

1. Uninstall old `com.miruplay.tv` when a clean repro is needed:
   - `GET /ZidooControlCenter/Apps/uninstallApp?packageName=com.miruplay.tv`
2. Copy signed APK to `Z:/Download/app-release.apk`.
3. Trigger install:
   - `GET /ZidooFileControl/openFile?path=/storage/emulated/0/Download/app-release.apk`
4. Confirm installer with `Right`, then `Ok`.
5. Verify version with:
   - `GET /ZidooControlCenter/Apps/getApps`

## WebUI bootstrap

1. Launch MiruPlay.
2. Navigate `Up -> Right -> Ok` to enter `设置`.
3. `WebUI` is the default settings section on the left menu.
4. Press `Ok` on `开启 WebUI`.
5. Capture the QR area from NanoKVM and decode the token through `miruplay-webui-api.py --qr-image ...`.

## Source setup

For local drama/HDR/SDR validation, create local sources with both fields preserved:

- `contentMode=DRAMA`
- `disableOnlineMetadata=true`

If the app was freshly uninstalled, remember to rebuild the narrow local sources and re-scan them before playback verification.

## Playback config

For current Zidoo signed-release validation, prefer:

- backend: `EXPERIMENTAL_MPV_EMBEDDED`
- `forcedSignalKind=HDR10` only for HDR probes
- `forcedSignalKind=SDR` only for SDR probes

Keep the final completion audit serial and clean:

1. fresh install proof
2. HDR-only pass
3. if needed, clean reinstall and rebuild token/sources
4. SDR-only pass

Do not count contaminated mixed HDR/SDR runs as final evidence.

## Verification checklist

- HDR sample enters `Playing`, advances `positionMs`, has non-zero `durationMs`, and shows live video.
- SDR sample enters `Playing`, advances `positionMs`, has non-zero `durationMs`, and shows live video.
- A seek probe near `300000` continues to advance afterwards.
- `playback-debug-config` reports:
  - `activeBackend=EXPERIMENTAL_MPV_EMBEDDED`
  - `currentSignalKind=HDR10` or `SDR`
  - matching `currentRuleKey`
- NanoKVM screenshot shows real video, not a black frame.

## Current known-good evidence

See `docs/verification/zidoo-embedded-mpv-signed-release-verify.md` and files under `docs/verification/evidence/`.
