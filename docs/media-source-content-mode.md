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
- Drama source scanning no longer reuses Bangumi cached matches or runs Bangumi online enrichment during library scan
- During drama scan, index rows stay in a deferred `PENDING` state until TMDB detail refresh or manual match writes back a confirmed result
- If the token is missing or the request fails, local browsing and playback stay available
- Opening drama detail now renders the local series view first and then refreshes TMDB data in the background, so the page does not block behind a full-screen loading blank while online metadata is still loading
- When refresh succeeds, the resolved series summary, per-episode title, TMDB source marker, and TMDB id are written back into local `index_entry` rows
- The resolved drama series header data is also cached separately, so drama home and later detail loads can reuse the saved title, summary, poster/backdrop URL, TMDB id, and first air date without re-querying TMDB immediately
- After a series has already been matched once, later detail loads prefer the stored TMDB id instead of repeating a title-based guess first
- If a previously stored TMDB binding or a fresh TMDB response is obviously inconsistent with the local drama title, MiruPlay now rejects that result instead of overwriting the local series with the wrong show

Current enriched fields:

- localized title
- original title
- summary
- poster
- backdrop
- first air date

This release still does not include automatic mixed-library inference or batch drama metadata operations.

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
- shared player HTTP playback requests

The retry is internal. The source still remains a normal single-app media source entry and no extra UI toggle is exposed for this behavior.

MiruPlay now also normalizes the WebDAV root URL path before issuing requests.

- You can enter a human-readable path with Chinese folder names directly in settings
- You do not need to pre-convert every path segment into percent-encoded form
- MiruPlay will normalize the root path before PROPFIND and playback requests so directory listing and playback still target the correct encoded URL

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

- drama detail now includes a TMDB manual match entry for one-off correction
- there is still no full drama-side batch metadata toolbox
- there is still no anime-style multi-source metadata workflow on the drama side

The current goal is operational parity for browsing and playback, not total feature identity.

## Drama Library Browsing

Drama home is no longer limited to a few horizontal rows plus a single flat catch-all list.

Current behavior is now closer to the anime library browsing pattern:

- featured series still appear first
- drama home now requests a visible initial focus target on entry instead of opening in a no-focus TV state
- continue watching remains available when progress exists
- continue watching now also shows a visible progress indicator instead of only text
- recently added remains available
- when a drama detail refresh has already resolved TMDB data once, drama home reuses the saved series header fields on the next load and after app restart instead of only relying on the current in-memory session
- the lower browsing area now follows the same poster-wall arrangement setting used by anime mode instead of a drama-only grouping rule

When exact episode duration is not available yet, drama mode uses a coarse in-progress visual bucket for resume cards and episode rows.

- this keeps the drama UI from looking unfinished
- it does not block local playback or resume
- the exact watched time is still shown as text beside the visual indicator

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
- Drama home now opens with a visible focused target, so D-pad confirm can enter drama detail immediately without first nudging the focus system
- WebDAV drama sources without a stored username can still scan successfully when the server accepts `anonymous:` authentication on retry
- The same kind of WebDAV drama source can also enter the shared player and start playback without a `401` when playback needs the same `anonymous:` authorization header
- After drama playback starts, the shared player header now prefers `第 X 集` style episode text and keeps the series title on the second line instead of falling back to the raw filename
- Drama detail opens with the primary play action focused first
- Returning from drama playback updates the drama detail primary action to a continue label when progress exists
- Drama detail shows a visible `刷新信息` action and, when no TMDB token is configured, shows the user-facing message `还没配置 TMDB 令牌，暂时只能显示本地信息。`
- Drama detail also shows a visible `手动匹配` action so a wrong or missing TMDB guess can be corrected from the detail page itself
- With debug-only TMDB launch overrides pointing at a local mock service, drama home cards can show online summary, first-air date, and season / episode totals on-device
- In the same mock-verified run, drama detail refresh changes the action text to `刷新中`, keeps the current episode list visible instead of blanking the page, and then reports `电视剧信息已刷新。`
- After that refresh, reopening the same drama detail still shows the remembered TMDB status instead of falling back to a fresh title guess first
- Database inspection confirmed the refreshed source rows were persisted with `metadata_source=TMDB`, a stable `metadata_id`, resolved `metadata_title`, resolved `episode_title`, and `SCRAPED` status
- Drama home now uses the same poster-wall arrangement setting as anime mode instead of a separate drama-only grouping rule
- Drama continue-watching cards and drama episode rows now show a visible in-progress indicator even when the exact runtime is not yet cached
- For drama paths shaped like `剧名/剧名.S01/剧名.S01E02.mp4`, the stored episode number now keeps the filename's explicit `E02` instead of being overwritten by a path-level `1`
- Drama library feature cards and drama detail stats now use TV-series wording such as `集` and `季` instead of anime-specific `话`
- When no poster or backdrop is available yet, drama home cards and drama detail now render title-based placeholder artwork instead of a generic empty image block
- When a stored drama TMDB binding is clearly wrong, or a new TMDB response is clearly for a different title, drama detail now keeps the local series title and shows a user-facing warning instead of silently poisoning the local cache with the wrong show
- After aligning the TV interaction flow again, drama detail now keeps the same three-button primary action row shape as anime detail: play / continue first, then metadata actions
- When a drama backdrop image is still missing, the placeholder no longer paints an extra lower-right source label that can visually collide with the action row
- A real-device retest confirmed that returning from drama playback restores focus to the primary play / continue action, and pressing back once more returns to drama home with focus back on the top scan action
- A matching real-device retest in anime mode confirmed that the anime home and anime detail flow still open from the default focused cards after switching the saved startup mode
- During verified drama playback from a WebDAV-backed source, the device showed an established TCP session to the configured WebDAV service port and that socket used the same app UID as `com.miruplay.tv`, confirming that playback was still using the remote source path rather than silently falling back to local file access

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

TMDB-backed online enrichment is implemented in code and covered by unit tests. The device verification here still avoids any real private TMDB credential, so the on-device proof uses only debug-only launch overrides plus a local mock TMDB service.

After adding a debug-only TMDB base URL override, the same device was verified against that local mock TMDB service through adb-driven launch extras. In that run, drama detail rendered:

- online series title
- online summary
- original title
- first air date
- online episode title

The same mock-verified run also proved the newer "refresh and persist locally" path on-device:

- the detail screen could refresh without switching to a full-page loading blank state
- the refresh button became `刷新中` while disabled
- refreshed TMDB fields were written into local `index_entry` rows
- reopening the same drama detail reused the stored TMDB id path

This proves the on-device drama detail enrichment flow end-to-end without requiring any private real-world TMDB credential to be stored in the repository or written into documentation.

This verification section intentionally avoids recording any private device connection details.
