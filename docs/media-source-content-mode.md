# Media Source Content Mode

## Overview

MiruPlay now distinguishes between two library modes:

- `ANIME`
- `DRAMA`

This affects both app-level behavior and per-source metadata:

- On first launch, the user must manually choose whether the app runs in anime mode or drama mode.
- Each media source also stores a manual content type so the source itself is explicitly tagged as `ANIME` or `DRAMA`.
- After the first selection, startup goes directly into the matching home screen.
- Drama mode now has its own library screen and detail screen instead of only a placeholder entry.

`AUTO` or mixed-library behavior is not part of the current release. The data model is intentionally shaped so that more flexible routing can be added later without redesigning the storage contract.

## App Startup Mode

The app-level mode is a startup choice, not a database entity.

- First launch requires the user to select `ANIME` or `DRAMA`
- The selected mode is stored in `preferences`
- The selected mode is read again on the next app launch
- This mode is not persisted in Room and is not part of the media source table

This keeps the global product mode lightweight and avoids introducing a database-backed setting for a value that only controls startup behavior and mode-specific entry flow.

## Settings Semantics

The settings screen does not hot-switch the whole app immediately.

Its mode switch should be understood as:

- save the next startup mode now
- apply that mode on the next launch

In other words, changing the setting updates the persisted preference, but the current running session continues in its existing mode until the app is restarted.

## Current Entry Flow

The root navigation keeps the mode split at the top level instead of scattering checks across every page.

- First launch without a completed choice opens the mode selection screen
- `ANIME` mode opens the existing anime library flow
- `DRAMA` mode opens the dedicated drama library flow
- Drama detail uses its own route and screen
- Playback remains shared once a specific episode is opened

## Drama Online Metadata

Drama detail now supports a lightweight online metadata enrichment path.

- The current implementation uses `TMDB`
- It only enriches the drama detail page
- If the token is missing or the request fails, local browsing and playback stay available

Current enriched fields:

- localized title
- original title
- summary
- poster
- backdrop
- first air date

This release still does not include automatic drama matching UI, batch drama metadata operations, or mixed-source inference.

## TMDB Token

The settings page now includes a separate `TMDB Read Access Token` field.

- It is stored in encrypted preferences
- It is optional
- It is only used for drama detail enrichment
- Clearing it disables drama online detail enrichment only

For debug verification only, the app also accepts launch-time test overrides for the drama TMDB path:

- `test_tmdb_token`
- `test_tmdb_base_url`

These extras are only intended for local validation against a mock service. They are not part of the normal end-user settings flow and should not be documented with any private host or device details.

## Media Source Persistence

`MediaSourceInfo` includes a `contentMode` field describing what kind of library that source belongs to.

The `media_source` Room table stores this as a non-null `content_mode` text column.

- New rows persist the user-selected source type
- Valid stored values are currently only `ANIME` and `DRAMA`
- Repository reads restore the stored value back into `MediaSourceInfo`

Both anime and drama sources use the same `media_source` table. There is no separate table split by content type. The distinction is expressed only by the `content_mode` field.

## WebDAV Anonymous Compatibility

Some WebDAV servers behave like anonymous shares in practice, but still reject completely unauthenticated requests.

MiruPlay now handles that case more gracefully for WebDAV sources:

- if the source already has an explicit username, MiruPlay uses that credential normally
- if the source has no username and the first WebDAV request returns `401`
- MiruPlay retries once with `Basic anonymous:`

This keeps ordinary explicit credential behavior unchanged, while improving compatibility with read-only anonymous WebDAV services that still expect an authorization header.

This fallback is currently applied to:

- directory listing
- file metadata lookup
- file stream open

The retry is internal. The source still remains a normal single-app media source entry and no extra UI toggle is exposed for this behavior.

## Migration

Database migration `5 -> 6` adds the `content_mode` column:

- Column name: `content_mode`
- Type: `TEXT NOT NULL`
- Default value for existing rows: `ANIME`

This means pre-existing sources are migrated forward as anime sources by default. The upgrade preserves backward compatibility without forcing an immediate manual reclassification step during migration.

## Current Constraints

- Only `ANIME` and `DRAMA` are valid runtime values
- Source content type is manual, not inferred automatically
- Existing rows migrate to `ANIME`
- `AUTO` is not exposed
- Mixed-mode library behavior is not implemented in this release

## Future Extension Point

The current architecture intentionally leaves room for a future `AUTO` or mixed-mode strategy, but that strategy is deferred for now.

What is intentionally left open:

- adding additional enum values without changing the overall table shape
- introducing future routing or inference logic above the existing persisted field
- supporting more advanced per-source or per-library mode resolution later

What is intentionally not done in the current release:

- no automatic anime/drama detection
- no combined mixed-library mode in the UI
- no runtime `AUTO` behavior

## Drama Detail Interaction

Drama detail is no longer treated as a placeholder-style page.

Current behavior is aligned more closely with the anime detail flow:

- entering drama detail focuses the primary play / continue button first
- if playback progress exists, the primary button changes from `播放` to `继续观看 <集数>`
- if no resume progress exists but playable episodes are available, the primary button still stays usable and remains the first focus target
- drama detail now exposes a dedicated `刷新信息` action next to the primary play button
- that refresh action re-runs the current TMDB-backed enrichment path for the opened series
- the selected season button has a clearer active state
- switching seasons updates the visible episode list
- switching seasons also updates a short status message for the user
- if the selected season has no playable episodes, the detail page shows a clear empty-state message instead of silently showing nothing
- returning from playback keeps the detail page state coherent enough for immediate resume

This is still intentionally lighter than the anime detail page:

- no Bangumi-style manual matching flow
- no drama-specific manual rescrape UI
- no batch metadata tools on the drama side

The current goal is operational parity for browsing and playback, not total feature identity.

## Drama Library Browsing

Drama home is no longer limited to a few horizontal rows plus a single flat catch-all list.

Current behavior is now closer to the anime library browsing pattern:

- featured series still appear first
- continue watching remains available when progress exists
- recently added remains available
- the lower browsing area is now presented as a lightweight poster-wall style grouped section instead of only one final row

The current grouping is intentionally simple:

- sections are grouped by the first visible title character
- this is lighter than the anime-side configurable poster-wall arrangement
- it improves browsing density and navigation flow without introducing a large new data model just for drama mode

## Documentation Safety Note

This document must stay free of environment-specific private details.

- Do not write ADB addresses
- Do not write device identifiers
- Do not write other private connection or host information

## Verified Runtime Behavior

The current debug build has been manually verified on a real Android device with the launch test hook.

Verified behaviors:

- First launch shows the mode selection screen
- Choosing `DRAMA` enters the drama home screen
- Restarting after the first choice re-enters the previously selected mode
- In `DRAMA` mode, adding a launch test source tagged `DRAMA` makes the drama home screen treat it as a drama source
- In `DRAMA` mode, adding a launch test source tagged `ANIME` does not make it appear as a drama source
- In `ANIME` mode, adding a launch test source tagged `ANIME` makes the anime home screen treat it as an anime source
- In `ANIME` mode, adding a launch test source tagged `DRAMA` does not make it appear as an anime source
- A seeded local drama index entry appears in the drama home screen and can open the drama detail screen
- The drama detail screen can render seeded season and episode data without online metadata
- Triggering playback from drama detail reaches the shared player pipeline and attempts to open the resolved local file path
- WebDAV drama sources without a stored username can still scan successfully when the server accepts `anonymous:` authentication on retry
- Drama detail opens with the primary play action focused first
- Returning from drama playback updates the drama detail primary action to a continue label when progress exists
- Drama detail shows a visible `刷新信息` action and, when no TMDB token or live metadata is available, shows the user-facing fallback message `未获取到电视剧在线信息。`
- Drama home now exposes a lower grouped poster-wall style browsing area rather than ending with only a single flat final row

These checks were validated by combining:

- launch extras
- on-device UI hierarchy dumps
- screenshots
- direct inspection of the `media_source.content_mode` value in the app database
- direct inspection of seeded `index_entry` rows in the app database
- `logcat` evidence from `ExoPlayer` local file open attempts
- direct inspection of the stored WebDAV username value for the verified drama source

For the seeded drama playback check, the device showed a real `ExoPlayer` file-open attempt for the resolved episode path and the detail row state changed from not watched to watched after activation. On this device, that is stronger evidence than relying only on a final player screenshot.

For the verified WebDAV anonymous-compatibility check, the same device stored the validated drama source with a `NULL` username value, while the scan still completed successfully and produced indexed episodes. That proves the retry path matters in practice for this environment, not just in unit tests.

TMDB-backed online enrichment is implemented in code and covered by unit tests, but this device verification set did not include a real configured TMDB token at verification time. Because of that, the runtime evidence above only proves the local drama detail flow and the shared playback path; it does not yet prove a successful live TMDB enrichment response on-device.

After adding a debug-only TMDB base URL override, the same device was also verified against a local mock TMDB service through adb-driven launch extras. In that run, drama detail rendered:

- online series title
- online summary
- original title
- first air date
- online episode title

This proves the on-device drama detail enrichment flow end-to-end without requiring any private real-world TMDB credential to be stored in the repository or written into documentation.

This verification section intentionally avoids recording any private device connection details.
