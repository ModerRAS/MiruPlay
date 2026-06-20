# Settings / Web Control Parity Checklist

Use this checklist whenever a settings-related menu item, toggle, form field, config field, or settings workflow changes in any one of these surfaces:
- TV app / settings UI
- Web API
- WebUI

Goal: avoid app-only, WebAPI-only, or WebUI-only drift unless the user explicitly wants a surface-specific feature.

## Trigger Cases
- Added or changed a settings menu item in `ui-tv`
- Added or changed a remotely readable/configurable field in `data`, preferences, or repositories
- Added or changed a Web API request/response field in `web-control-core` or `web-control`
- Added or changed a WebUI form, card, settings panel, or menu in `web-control/frontend`

## Surface Decision
Before coding, decide which surfaces should expose the change:
- TV settings only
- TV settings + WebAPI
- TV settings + WebAPI + WebUI
- WebAPI + WebUI only
- WebUI only

If the feature is intentionally surface-specific, say that explicitly in the final notes or review context.

## Checklist

### 1. App-side settings flow
Check whether the TV-side settings flow also needs updating:
- Settings screen, menu item, label, or toggle in `ui-tv`
- Backing `ViewModel` / `StateFlow`
- Preferences / repository / model plumbing in `data` or shared modules
- Any default value or migration that would otherwise diverge from Web surfaces

### 2. Web API contract
Check whether WebAPI also needs updating:
- Request / response DTOs in `web-control-core`
- Route handling in `web-control-core/src/main/kotlin/com/miruplay/tv/webcontrol/NanoHttpWebControlServer.kt`
- Service wiring in `web-control-core` or `web-control`
- Field serialization, redaction, and persistence behavior
- Explicit plumbing for new fields; do not rely on silent defaults to "probably match"

### 3. WebUI presentation
Check whether WebUI also needs updating:
- Display the field or menu entry where users expect it
- Add the field to form state and edit-state restore logic
- Include the field in submit payloads and refresh/load flows
- Keep labels/options aligned with the underlying app meaning

Primary hotspot:
- `web-control/frontend/src/App.vue`

### 4. Reverse-direction parity
Do the same check in reverse:
- If you only changed WebAPI and WebUI, confirm whether the TV settings page should also expose it
- If you only changed the TV settings page, confirm whether WebAPI and WebUI should also expose it
- If you only changed WebUI, confirm whether the API and app-side state are now missing explicit support

### 5. Verification
Verify at least the smallest round-trip that proves parity:
- One API request/response path for the changed field
- One WebUI load/edit/save/display path for the changed field
- If TV settings are affected, one app-side settings path too

Prefer nearby existing tests when they exist. For UI-only copy/layout changes, a targeted manual verification is enough.

### 6. Subagent follow-through
After the main settings/menu change is done, a focused subagent may be used to audit parity gaps:
- Ask it to compare app-side settings, WebAPI, and WebUI exposure
- Ask it to list missing fields, routes, payload wiring, and tests
- Parent agent reconciles the diff before finalizing

Suggested scope for the subagent:
- Only settings/menu parity
- Only affected modules/files
- Only missing WebAPI/WebUI/app-surface follow-through

## Fast File Pointers
- TV settings UI: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/settings/`
- Shared settings state: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/settings/SettingsViewModel.kt`
- Preference / persistence layer: `data/src/main/kotlin/com/miruplay/tv/data/preferences/`
- WebAPI DTOs/services: `web-control-core/src/main/kotlin/com/miruplay/tv/webcontrol/`
- Android Web Control host: `web-control/src/main/kotlin/com/miruplay/tv/webcontrol/`
- WebUI: `web-control/frontend/src/`
