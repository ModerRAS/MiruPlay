# MiruPlay Audio DSP Design

## Goal

Add a native PEQ and linear-phase FIR audio pipeline that works with every
current playback backend, preserves multichannel PCM when the Android output
supports it, and offers standard stereo or HRTF binaural downmix when the
selected output cannot accept the source channel layout.

## Scope

The feature covers:

- Parametric EQ bands: peaking, low/high shelf, low/high pass, notch, and
  band pass.
- Preamp/headroom protection and a linked output limiter.
- Minimum-phase biquad mode and linear-phase FIR mode generated from the same
  PEQ magnitude target.
- Channel-layout aware processing for mono, stereo, 5.1, 7.1, and unknown
  layouts with an explicit status when a layout is unsupported.
- Standard ITU stereo downmix and optional HRTF binaural downmix for 5.1/7.1.
- A common preset/configuration model, full WebUI editing, WebAPI round trips,
  and TV-side enable/preset controls.
- Native adapters for standard Exo, GL/experimental Exo, embedded mpv, and
  IJKPlayer. External mpv and legacy libVLC values continue to normalize to
  their existing supported backends.

This release does not implement stereo-to-surround upmixing, arbitrary user
matrix editing, room correction, loudness normalization, or microphone/audio
capture as a product feature. The processing graph has an explicit routing
stage so an additive upmix stage can be introduced without changing the
preset schema's existing fields.

## Architecture

### Configuration and DSP core

`core:model` owns the serializable settings contract. A new pure Kotlin
`audio-dsp-core` module owns validation, channel-layout identifiers, RBJ
biquad design, frequency-response sampling, symmetric FIR generation, and
the backend-neutral compiled plan. The core never depends on Android or a
player implementation.

The persisted object is versioned and contains:

- `enabled` and `selectedPresetId`.
- `outputMode`: `AUTO_PRESERVE`, `STEREO_DOWNMIX`, or `HRTF_BINAURAL`.
- `presets`, each with a stable id, name, preamp, phase mode, FIR quality,
  channel-group rules, and optional limiter settings.
- Channel rules target `ALL`, standard channel groups, or concrete channel
  ids. A rule contains an ordered list of PEQ bands and an output gain.

The default is disabled with one neutral preset. Unknown schema versions or
malformed values load as the neutral preset and are reported as a recoverable
configuration warning.

### Processing graph

The runtime graph is:

```
decoded PCM
  -> channel-layout normalization (identity in this release)
  -> per-output-channel PEQ / gain
  -> optional linear-phase FIR for the PEQ magnitude target
  -> optional standard downmix or HRTF binaural renderer
  -> linked limiter and PCM sink
```

The graph never changes the channel count in `AUTO_PRESERVE` when the sink
advertises the requested PCM layout. If it cannot, `AUTO_PRESERVE` selects
the configured stereo fallback and publishes the effective route. HRTF is
only used for a deliberate `HRTF_BINAURAL` selection or an automatic fallback
that explicitly names HRTF. HRTF's interaural phase is intentional; the
linear-phase guarantee applies to the PEQ correction stage and does not erase
the HRTF spatial cues.

Linear FIRs are designed from the validated PEQ magnitude response by
frequency sampling and an inverse real FFT. Coefficients are symmetric and
share one tap count/group delay across all active channels, including flat
channels, so channel alignment is preserved. FIR quality selects 1024, 2048,
or 4096 taps; 2048 is the default. Runtime plan changes crossfade at a frame
boundary and never replace coefficients mid-frame.

The HRTF path uses the Apache-2.0 Resonance Audio binaural surround renderer
and its Apache-2.0 SADIE HRTF asset. 5.1 and 7.1 input ordering is normalized
before rendering to the ITU-R BS.775-3 speaker positions. Standard downmix
uses an explicit ITU matrix and keeps LFE attenuation bounded. If an HRTF
asset or renderer cannot initialize, playback falls back to the standard
matrix only when the user selected automatic fallback; an explicit HRTF
selection reports an error and leaves the previous audio plan active.

### Backend adapters

- **Exo and GL:** build both Media3 players with a custom `AudioSink` whose
  `AudioProcessorChain` contains the native/common DSP processor. The sink
  advertises only PCM while DSP is active, disables offload/passthrough, and
  keeps the negotiated channel mask instead of forcing stereo.
- **Embedded mpv:** translate the compiled plan to mpv `af`/lavfi options.
  Minimum-phase PEQ uses FFmpeg biquad filters; linear mode uses
  `firequalizer` with the sampled response; downmix uses an explicit `pan`
  matrix; HRTF uses FFmpeg's multichannel headphone/SOFA path. Runtime option
  changes are applied through the existing `MPVLib` property/command path.
- **IJKPlayer:** rebuild the pinned debugly/ijkplayer source for both shipped
  ABIs with the audio filter components enabled, retain the existing AndroidIO
  bridge, and pass the same normalized filter plan through the native player
  option. The artifact's source revision, hashes, licenses, and build inputs
  remain documented under `third_party/ijkplayer`.

Every adapter reports its effective sample rate, input/output layout, phase
mode, tap count, and fallback reason through the shared playback diagnostics
model. Backend filter initialization is fail-closed: it keeps the previous
working plan rather than emitting unprocessed audio while claiming DSP is on.

### Settings, WebAPI, and WebUI

Playback preferences gain a small TV-facing enabled/preset projection. The
full configuration lives behind a dedicated `/api/audio-dsp` contract:

- `GET /api/audio-dsp` returns the versioned config, presets, capabilities,
  current effective route, and warnings.
- `PUT /api/audio-dsp` validates and atomically persists the complete config,
  then applies it to the current controller at the next safe audio boundary.
- `POST /api/audio-dsp/preview` accepts one unsaved preset and returns the
  sampled magnitude/phase curves used by the WebUI graph.

The existing playback settings endpoint carries only the TV projection and
remains backward compatible with nullable request fields. The WebUI gets a
dedicated Audio DSP view with preset CRUD, PEQ row editing, channel-group
selection, preamp/limiter controls, output/downmix/HRTF selection, phase/FIR
quality controls, response graph, JSON import/export, and apply status. The
TV settings screen exposes only the DSP switch and preset selector.

### Error handling and compatibility

- DSP defaults off, so existing installations retain passthrough behavior.
- Enabling DSP forces decoded PCM and disables encoded direct/offload/tunnel
  output. Disabling DSP restores the previous player output policy.
- Unsupported input layouts are normalized to a documented identity or
  stereo fallback; no silent channel reordering is allowed.
- Invalid API payloads return a structured 400 response with field errors and
  do not change the stored or active plan.
- A backend-specific filter failure leaves the last working plan active and
  exposes the reason to WebAPI/WebUI diagnostics.
- DSP changes made from WebUI apply to the active playback session at a safe
  boundary; a new playback session always reads the persisted plan.

## Verification

The pure core gets deterministic tests for band coefficient behavior, PEQ
response sampling, FIR symmetry/group delay, channel-mask normalization,
ITU downmix gains, HRTF output channel count, limiter headroom, and malformed
config recovery. Android tests cover 16-bit and float PCM processors,
end-of-stream flushing, plan crossfade, Media3 sink negotiation, and backend
option translation. WebAPI tests cover GET/PUT/preview round trips and
validation errors; the WebUI build is part of the module verification.

Device verification on the HK1 Android 13 box uses a deterministic 48 kHz
PCM sweep captured through the sndcpy/scrcpy playback-capture path as a
secondary integration check. Offline impulse/sweep tests at the processor
boundary are authoritative because playback capture cannot prove downstream
HDMI receiver processing. Evidence records the selected backend, negotiated
channel mask, effective DSP plan, and any automatic stereo/HRTF fallback.

