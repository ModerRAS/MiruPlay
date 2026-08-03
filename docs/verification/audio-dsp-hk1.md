# Audio DSP Verification

## Build and automated checks

- `:core:model:test --tests '*AudioDspModelsTest'`
- `:audio-dsp-core:test`
- `:data:testDebugUnitTest --tests '*PlaybackPreferencesManagerTest'`
- `:player-core:testDebugUnitTest --tests '*DspAudioProcessorTest' --tests '*AudioDspMpvOptionsTest' --tests '*AudioDspOutputPolicyTest'`
- `:web-control-core:test --tests '*WebControlSettingsRouteTest'`
- `:ui-tv:compileDebugKotlin`
- `:web-control:compileDebugKotlin`
- `:app:assembleDebug`
- `web-control/frontend`: `bun run build`

All commands completed successfully on 2026-08-03.

## Android TV smoke check

The debug APK was installed on the configured HK1 Android TV test device and launched with the native ADB shell flow. The settings screen exposed the new `音频 PEQ / DSP` section, `Neutral` preset, and TV-only enable switch. Toggling the switch wrote the versioned `audio_dsp_config` preference and the test restored the switch to disabled afterwards. No AndroidRuntime crash was observed in the launch log.

## Audio capture limits

QtScrcpy's sndcpy path uses Android `AudioPlaybackCapture` and exposes 48 kHz, stereo, 16-bit PCM over an ADB-forwarded socket. It is useful for comparing DSP on/off response curves, but it does not observe HDMI sink negotiation, encoded passthrough, or the receiving device's multichannel/downmix behavior. HDMI preservation and HRTF output therefore remain covered by the pure Kotlin channel/downmix tests and backend option tests; they require an external HDMI analyzer or receiver-side capture for end-to-end confirmation.

## Backend route notes

- Exo/GL force decoded PCM while DSP is enabled and keep the negotiated multichannel layout when the sink supports it.
- Embedded mpv and IJK receive native FFmpeg-style filter options. Their exact filter acceptance still depends on the native binary shipped by the selected backend and should be checked in a device playback session when a suitable multichannel test file is available.
- Saving a WebUI or TV change updates the persisted/runtime configuration; an already-running audio renderer keeps its current plan and applies the new plan on the next playback session.
- HRTF currently uses the built-in fixed binaural compatibility matrix, not a measured HRIR/SOFA renderer. A native HRIR renderer can be added later without changing the WebUI contract.
