# Android TV Player Controls

This document is the user-approved interaction contract for MiruPlay playback controls. Keep the Compose player, remote bindings, tests, and real-device verification aligned with it.

## Status

Implemented:

- With controls hidden, DPAD left and right seek.
- With controls visible, the playback timeline is the default focus target and owns DPAD left and right seek, including repeat events.
- Timeline down enters the functional action row.
- Functional controls and open option panels own DPAD navigation; they must never trigger global seek.

Implemented in the current worktree, pending integrated device verification:

- Dedicated media remote keys.
- Player information and diagnostic side panel.
- Refined subtitle and audio parameter panels.
- A more transparent playback chrome.

## Playback Layout

The visual hierarchy is:

```text
Top:     Back, title, source
Center:  Previous episode, seek backward, play/pause, seek forward, next episode
Bottom:  Current time, focusable timeline, total time
         Picture, speed, subtitles, audio
```

The center and bottom controls should use the existing restrained TV visual style. The chrome must become more transparent than its current black overlays, while preserving focus contrast and readable text over bright video.

## Focus Contract

- When chrome appears, focus the timeline.
- Timeline left and right seek. Timeline down focuses the first functional action. Timeline up enters the center transport controls.
- Center transport controls use left and right for adjacent transport buttons and down to return to the timeline.
- Functional actions use left and right for adjacent actions and up to return to the timeline.
- Opening a parameter panel preserves the invoking action as the return target. Back closes the panel and restores that action; a further Back follows existing chrome behavior.
- Never globally consume visible DPAD left or right at the player root.

## Dedicated Remote Keys

Dedicated keys are accelerators, not required navigation paths. On-screen controls remain available for every action.

| Android key | User-visible behavior |
| --- | --- |
| MEDIA_REWIND | Seek backward with seek feedback; repeat continues seeking. |
| MEDIA_FAST_FORWARD | Seek forward with seek feedback; repeat continues seeking. |
| MEDIA_PREVIOUS | Select the previous logical episode when available. |
| MEDIA_NEXT | Select the next logical episode when available. |
| CAPTIONS | Show controls and open the subtitle panel when selectable tracks exist. |
| MENU | Show controls and focus the existing Picture action. Do not add a duplicate options menu. |
| INFO | Toggle the player information and diagnostics side panel. |
| SETTINGS | Remain unbound by default because it is system-owned and may not reach the app. |

Media previous always selects the previous logical episode. At the first logical episode it is unavailable; it never inherits Media3's restart-current-item threshold behavior.

## Information Panel

INFO opens a right-side, read-only side panel while video remains visible. When the panel opens, hide the ordinary control chrome to avoid competing overlays. INFO again or Back closes it.

The panel has two tabs:

- Information: anime title, season and episode, current version/source, file/container/duration, video resolution/HDR/codec, selected audio, and selected subtitle.
- Playback and diagnostics: active backend, decoder/output host, position/duration, buffering state, playback speed, dropped-frame or equivalent metrics when available, and current sanitized error/diagnostic state.

Use unavailable placeholders for absent metrics. Never show credentials, raw signed URLs, auth headers, or private source paths.

## Subtitle and Audio Panels

Subtitle and audio selection are bottom horizontal parameter panels, not side drawers. They are optimized for immediate DPAD left/right switching.

```text
Subtitles                                      3 available
[Off] [Chinese Simplified - ASS] [Chinese Traditional - ASS] [English - SRT]
```

- CAPTIONS opens the subtitle panel and focuses the selected subtitle; when none is selected, focus Off.
- Left and right choose entries. Confirm applies immediately.
- Each entry shows language/title and a compact format plus embedded/external qualifier when available.
- Long track lists scroll horizontally while preserving the focused item onscreen.
- Back or a second CAPTIONS closes the panel and restores focus to the Subtitles action.
- The Audio panel mirrors this interaction.
- Picture and speed may use the same bottom parameter area, but only show their own controls.

## Implementation Boundary

Map buttons and dedicated remote keys into one shared player command path. The UI surfaces dispatch commands; the ViewModel owns UI consequences and logical episode selection; player-core owns backend transport execution. DPAD focus navigation is not a command and stays in Compose focus handling.

Do not route embedded mpv or ijkplayer through the standard Exo MediaSession. Background media-session controls are supported only when the session represents the active authoritative standard Exo backend.

## Ijkplayer Backend

The experimental ijkplayer backend is implemented through `player-ijkplayer-android` and the existing generic native video host path.

Supported and verified:

- Local SDR H.264/AAC playback on HK1 using the packaged `arm64-v8a` library.
- Native surface lifecycle, play/pause, seek, position/duration, playback speed, completion/error state, and embedded audio-track mapping.
- Media3 `DataSource` bridging through ijkplayer `IAndroidIO`, preserving MiruPlay URI/header/WebDAV policy instead of using ijkplayer native HTTP directly.
- Requested-versus-active backend reporting and TV/WebAPI/WebUI setting parity.

Explicit capability gates:

- Sources with external subtitle tracks fall back to standard Exo because upstream ijkplayer has no external subtitle-source API or libass renderer.
- HDR, HDR10+, Dolby Vision, and unknown HDR fall back to standard Exo until target-device behavior is independently validated.
- Embedded timed-text subtitles are not exposed as rendered anime-grade subtitles.
- The backend is experimental and is never selected by default.

The pinned upstream provenance, hashes, notices, and public-release rebuild requirement are documented in `third_party/ijkplayer/README.md`.

## Verification

Unit and Compose tests must cover hidden seek, focused-timeline seek, visible-control navigation, open-panel navigation, remote key repeat behavior, and key-up non-actions. Real-device checks must cover focus visibility, direct media keys when available, INFO panel redaction, subtitle/audio selection, and no seek from functional panels.
