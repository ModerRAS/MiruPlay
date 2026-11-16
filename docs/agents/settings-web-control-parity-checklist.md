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
- 代理相关文案（TV 面板 / WebUI 卡片）属 copy-only：TV 视检 + WebUI 打开「代理配置」对照即可；OAuth 登录页的 WebView 代理是 TV 运行时能力，按 `pre-pr-checklist.md` 的「Bangumi OAuth WebView 走应用出站代理（patch）」小节真机验证

### 6. Subagent follow-through
After the main settings/menu change is done, a focused subagent may be used to audit parity gaps:
- Ask it to compare app-side settings, WebAPI, and WebUI exposure
- Ask it to list missing fields, routes, payload wiring, and tests
- Parent agent reconciles the diff before finalizing

Suggested scope for the subagent:
- Only settings/menu parity
- Only affected modules/files
- Only missing WebAPI/WebUI/app-surface follow-through

## Known multi-surface features (reference)

| Feature | TV settings | WebAPI | WebUI | Notes |
|---|---|---|---|---|
| Topping DAC 控制（toppingctl 协议） | ✅ AUDIO_DSP 区内 `ToppingDacControls` | ✅ `/api/topping/{status,volume,preset,flat,gain,power,preamp,usb-permission}` | ✅ 音频 DSP 视图 Topping DAC 卡片 | 音量仅 DX5 II（音量不在 HID 上的型号禁用输入）；> −10 dB 需确认；未 confirmed 型号拒写 |
| 出站代理（Bangumi API / Archive / RSS / OAuth 登录页） | ✅ 设置 →「出站代理」面板（`settings_proxy_panel_desc` 已含「Bangumi 登录页」，字段 `rssProxy*`）；OAuth 对话框运行时把同一配置装到内置 WebView（`BangumiOAuthWebViewProxy`，进程级 override） | ✅ `GET/PUT /api/proxy`（`NetworkProxyDto` ↔ `CloudDriveAutomationConfig.rssProxy*`） | ✅「代理配置」视图的出站代理卡片（说明文案已含「Bangumi 登录页」） | 配置面三端同源；WebView 代理是设备进程级能力，Web 端只读写配置、不参与 apply |
| Bangumi 登录 | ✅ 元数据区 WebView / 外部浏览器，回环 `127.0.0.1:13712/callback` | ✅ `POST /api/metadata/bangumi-login/start|captcha|cancel` 与 `POST /api/metadata/bangumi-login`（邮箱、密码、验证码）。手动 token 仍走 `bangumi-token`。密码不入库、不回显 | ✅ 元数据页账号登录：验证码由电视代拉（走出站代理），通过后设备换 token | 网页不把浏览器重定向到 `127.0.0.1`。TV WebView 与网页登录共用 `BangumiOAuthCoordinator`。未配 client id/secret 时网页返回 `not_configured` |

## Fast File Pointers
- TV settings UI: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/settings/`
- Shared settings state: `ui-tv/src/main/kotlin/com/miruplay/tv/ui/settings/SettingsViewModel.kt`
- Preference / persistence layer: `data/src/main/kotlin/com/miruplay/tv/data/preferences/`
- WebAPI DTOs/services: `web-control-core/src/main/kotlin/com/miruplay/tv/webcontrol/`
- Android Web Control host: `web-control/src/main/kotlin/com/miruplay/tv/webcontrol/`
- WebUI: `web-control/frontend/src/`
