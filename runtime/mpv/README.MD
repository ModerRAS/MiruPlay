# MiruPlay mpv Runtime Placeholder

This tracked directory is intentionally not a bundled Windows mpv runtime.
It exists so local release/QA scripts have a stable default path for
`runtime/mpv`, but Gradle treats it as empty unless an actual `mpv.exe` is
present.

Before running the self-contained Windows runtime, installer, or RIFE gates,
prepare a real payload here or pass `-PmpvRuntimeSource=<path>` to Gradle.
The prepared payload must contain at least:

```text
runtime/mpv/
  mpv.exe
  portable_config/
  runtime-manifest.json
```

For the default RIFE release gate, the manifest/runtime must also cover:

```text
portable_config/vs/MEMC_RIFE_NV.vpy
portable_config/vs/MEMC_RIFE_DML.vpy
```

See `docs/mpv-runtime-packaging.md` for the mpv_PlayKit preparation command,
expected checksums, Gradle smoke tasks, and target-hardware RIFE report flow.
