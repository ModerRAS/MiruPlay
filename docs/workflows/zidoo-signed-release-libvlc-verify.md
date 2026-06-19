# Zidoo Signed Release libVLC Verify

Use this workflow when validating MiruPlay libVLC playback on the Zidoo Z9X 8K with signed GitHub releases.

## Preconditions

- Install only signed GitHub release APKs.
- Use `Apps/getApps` on `:9529` as install proof.
- Use Zidoo Samba + `ZidooFileControl/openFile` for reliable install.
- Use MiruPlay WebUI QR to obtain the current `9978` token; do not hardcode or persist tokens.

## Install

1. Uninstall old `com.miruplay.tv` when a clean repro is needed:
   - `GET /ZidooControlCenter/Apps/uninstallApp?packageName=com.miruplay.tv`
2. Copy signed APK to `Z:/Download/`.
3. Trigger install:
   - `GET /ZidooFileControl/openFile?path=/storage/emulated/0/Download/<apk>`
4. Confirm installer with `Right`, then `Ok`.
5. Verify version with:
   - `GET /ZidooControlCenter/Apps/getApps`

## WebUI bootstrap

1. Launch MiruPlay.
2. Navigate `Up -> Right -> Ok` to enter `设置`.
3. Open `WebUI`, then `Right -> Ok` to enable it.
4. Capture the QR area from NanoKVM and decode the token through `miruplay-webui-api.py --qr-image ...`.

## Source setup

For local drama/HDR/DV validation, create local sources with both fields preserved:

- `contentMode=DRAMA`
- `disableOnlineMetadata=true`

If either field is missing, the source may fall back to `ANIME` and scanning can block in Bangumi enrichment.

## Playback config

For current Zidoo signed-release validation, prefer:

- backend: `EXPERIMENTAL_LIBVLC`
- vout: `GL_SURFACE`
- `skipLibVlcStartupProbe=true`

Avoid `OUTPUT_CALLBACKS` on this device; libVLC rejects the native output-callback bridge attach.

## Verification checklist

- HDR10 sample enters `Playing` and advances position.
- Dolby Vision sample enters `Playing` and advances position.
- `playback-debug-config` reports:
  - `activeBackend=EXPERIMENTAL_LIBVLC`
  - `libVlcVoutMode=GL_SURFACE`
  - `currentSignalKind=HDR10` or `DOLBY_VISION`
  - matching `currentRuleKey`
- NanoKVM screenshot shows live video, not `00:00` black frame.

## Current known-good evidence

See `docs/verification/zidoo-libvlc-signed-release-verify.md` and files under `docs/verification/evidence/`.
