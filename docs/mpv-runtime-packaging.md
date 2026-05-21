# MiruPlay mpv Runtime Packaging

MiruPlay Desktop expects a Windows mpv runtime with this shape:

```text
mpv-runtime/
  mpv.exe
  portable_config/
    mpv.conf
    input.conf
    scripts/
    script-opts/
    shaders/
    vs/
      MEMC_RIFE_NV.vpy
      MEMC_RIFE_DML.vpy
      MEMC_RIFE_STD.vpy
```

`hooke007/mpv_PlayKit` is the reference layout for this payload. MiruPlay does
not commit the large binary runtime, VapourSynth plugins, or model files.

As of the `20260510` / `2026FM` mpv_PlayKit release, GitHub publishes a small
`noVS` archive, a standard self-extracting `.exe`, and larger
VapourSynth/RIFE-oriented payloads such as the `vsNV` split archives. For a
MiruPlay build that must ship interpolation, use a base payload that contains
`mpv.exe` and `portable_config/`, then overlay a payload that actually contains
`portable_config/vs/` and the selected RIFE scripts. A `noVS` payload by itself
is expected to fail the RIFE release gate.

To audit the currently published mpv_PlayKit assets before packaging:

```powershell
gh release view 20260510 --repo hooke007/mpv_PlayKit --json tagName,name,publishedAt,assets
```

## Local Distribution Build

Prepare a local runtime payload from either an extracted mpv_PlayKit directory or
an archive. The currently validated `20260510` path uses the standard
`mpv-lazy-20260510.exe` as the base and overlays the `vsNV` split archive; keep
`.7z.002` next to `.7z.001` so `7z.exe` can read the full split payload:

```powershell
.\tools\prepare-mpv-runtime.ps1 `
  -Source D:\Downloads\mpv-lazy-20260510.exe `
  -OverlaySource D:\Downloads\mpv-lazy-20260510-vsNV.7z.001 `
  -Destination .\runtime\mpv `
  -RequiredRifeBackends 'NVIDIA,DIRECTML' `
  -ExpectedSha256 'mpv-lazy-20260510.exe=sha256:f03e288d5c41b00604a64d30bb81e5f76e7f125c0447926ff39561b32d44fef4;mpv-lazy-20260510-vsNV.7z.001=sha256:f877973fae54d7cc05451d0369340f5a4ef6c82a2f633c66b786a337d3e6ddda;mpv-lazy-20260510-vsNV.7z.002=sha256:1c7303d76f3767f16122c5ea16894add08b6c044ab15d2ccefba6155b8782007' `
  -Force
```

The script uses `7z.exe` for archives and self-extracting packages, validates
the expected RIFE scripts, then writes `runtime-manifest.json` into the prepared
payload. Passing an extracted directory as `-Source` or `-OverlaySource` skips
extraction and copies that directory directly. The desktop app's `Check runtime`
dialog reads this manifest when it is present and treats the declared relative
file/directory entries plus required RIFE backends as verification evidence. A
manifest that names missing files, unknown backends, absolute paths, or `..`
entries is reported as incomplete instead of being accepted as provenance.
The Gradle runtime gates use the same manifest evidence checks for source
runtime payloads, packaged distribution zips, and native app-image runtime
content.
`-ExpectedSha256` is optional, but recommended for downloaded release assets; it
is validated before archive
extraction. For split archives or base+overlay builds, pass a semicolon separated
`filename=sha256` list so every part is checked before extraction:

```powershell
-ExpectedSha256 'mpv-lazy-20260510-vsNV.7z.001=sha256:f877973fae54d7cc05451d0369340f5a4ef6c82a2f633c66b786a337d3e6ddda;mpv-lazy-20260510-vsNV.7z.002=sha256:1c7303d76f3767f16122c5ea16894add08b6c044ab15d2ccefba6155b8782007'
```

Known `20260510` / `2026FM` release asset digests:

| Asset | Size | SHA256 |
|---|---:|---|
| `mpv-lazy-20260510-noVS.7z` | 49,319,606 | `cd8373316417ddf9a688d3f52aa47e1c67e863aef68cf5dba380c57f41b2c855` |
| `mpv-lazy-20260510-vsNV.7z.001` | 1,610,612,736 | `f877973fae54d7cc05451d0369340f5a4ef6c82a2f633c66b786a337d3e6ddda` |
| `mpv-lazy-20260510-vsNV.7z.002` | 1,295,256,359 | `1c7303d76f3767f16122c5ea16894add08b6c044ab15d2ccefba6155b8782007` |
| `mpv-lazy-20260510.exe` | 132,331,618 | `f03e288d5c41b00604a64d30bb81e5f76e7f125c0447926ff39561b32d44fef4` |

```powershell
$env:JAVA_HOME='C:\Users\adqew\scoop\apps\temurin21-jdk\current'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :desktop-app:installDist -PmpvRuntimeSource=runtime\mpv
```

`mpvRuntimeSource` accepts either an absolute path or a path relative to the
repository root. The source directory should directly contain `mpv.exe`.
When `mpvRuntimeSource` is present, Gradle verifies the payload before building
the distribution and uses that prepared source as the only bundled runtime. If
`mpvRuntimeSource` is omitted, the distribution bundles the repository-local
`runtime/mpv` directory.

For UI-only development loops that do not need the self-contained mpv payload,
skip the large runtime copy:

```powershell
.\gradlew.bat :desktop-app:installDist -PbundleMpvRuntime=false
```

`bundleMpvRuntime` defaults to `true` so release artifacts remain
self-contained unless explicitly disabled.

The resulting app is written to:

```text
desktop-app/build/install/desktop-app/
```

The prepared runtime is copied into:

```text
desktop-app/build/install/desktop-app/runtime/mpv/
```

Launch `bin/desktop-app.bat` and click `Check runtime`. The app reports whether
`mpv.exe`, `portable_config/`, the configured RIFE scripts, and any
`runtime-manifest.json` entries are present.

## Release Gate

Use `requireMpvRuntime=true` when building a Windows artifact that must be
self-contained and RIFE-capable:

```powershell
.\gradlew.bat :desktop-app:distZip `
  -PmpvRuntimeSource=D:\Downloads\mpv_PlayKit `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML
```

`requiredRifeBackends` accepts `NVIDIA`, `DIRECTML`, and `STANDARD`, separated by
commas, spaces, or semicolons. The default release gate requires the two
RIFE paths that are packaged by the current validated base+overlay workflow:

```text
portable_config/vs/MEMC_RIFE_NV.vpy
portable_config/vs/MEMC_RIFE_DML.vpy
```

Pass `-PrequiredRifeBackends=NVIDIA,DIRECTML,STANDARD` only when the payload also
contains the additional Standard backend plugin stack. Passing an empty value
verifies only `mpv.exe` and `portable_config/`.

## Runtime Smoke Check

The release gate above checks the payload structure. When a real mpv runtime is
available on the build machine, run the smoke check as well:

```powershell
.\gradlew.bat :desktop-app:smokeMpvRuntime `
  -PmpvRuntimeSource=D:\Downloads\mpv_PlayKit `
  -PrequireMpvRuntime=true
```

This launches the selected `mpv.exe --version` and fails if the executable does
not start. To make `distZip` run the same smoke check during packaging, add
`-PrunMpvSmoke=true`.

For the full local release artifact gate, build the distribution zip, launch
the runtime used for packaging with `mpv.exe --version`, and verify the zip
contains `runtime/mpv/mpv.exe`, `runtime-manifest.json`, and the requested RIFE
backend scripts. When the bundled runtime contains `runtime-manifest.json`, this
task also verifies every manifest-declared package-relative file/directory and
required backend against the actual zip entries:

```powershell
.\gradlew.bat :desktop-app:smokePackagedMpvRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML
```

To verify the native Windows app-image shape before installer/signing work, run
the jpackage gate. It builds `desktop-app/build/jpackage/output/MiruPlay/`,
checks the launcher, validates the generated `app/MiruPlay.cfg` main class and
jar classpath entries, verifies the bundled `runtime/mpv` payload plus the
requested RIFE scripts and manifest-declared entries, and launches the generated `MiruPlay.exe` with the
headless desktop-entry smoke argument to confirm the app-image resolves its own
runtime and writes a JSON report:

```powershell
.\gradlew.bat :desktop-app:smokeNativeAppImageRuntime `
  -PmpvRuntimeSource=runtime\mpv `
  -PrequireMpvRuntime=true `
  -PrequiredRifeBackends=NVIDIA,DIRECTML
```

This task uses the JDK `jpackage --type app-image` tool and intentionally stops
at an unpacked app image; MSI/EXE installer generation and signing are separate
release steps.

For a real VapourSynth/RIFE filter smoke, run:

```powershell
.\tools\smoke-mpv-rife.ps1 -RuntimeRoot .\runtime\mpv -Backend DIRECTML
```

For target-host evidence that can be attached to release QA, add `-ReportPath`.
The JSON report records the runtime path, mpv version, generated clip shape,
backend results, per-backend log paths, `runtime-manifest.json` evidence,
Windows version, CPU, detected video controllers, and `nvidia-smi` output when
available. Manifest evidence includes whether the file was present, declared
RIFE backends, declared package-relative files/directories, and any manifest
problems found while reading the target runtime:

```powershell
.\tools\smoke-mpv-rife.ps1 `
  -RuntimeRoot .\runtime\mpv `
  -Backend ALL `
  -AllowFailures `
  -ReportPath .\build\mpv-smoke\rife-target-report.json
```

Validate a generated target-host report before attaching it to release QA:

```powershell
.\tools\assert-mpv-rife-report.ps1 `
  -ReportPath .\build\mpv-smoke\rife-target-report.json `
  -RequiredBackends DIRECTML `
  -RequireRuntimeManifest
```

For a matrix report that intentionally allows unsupported backends to fail, pass
the same allowance while still checking the report schema, host diagnostics, mpv
version, and selected backend entries:

```powershell
.\tools\assert-mpv-rife-report.ps1 `
  -ReportPath .\build\mpv-smoke\rife-matrix-report.json `
  -RequiredBackends NVIDIA,DIRECTML `
  -RequireRuntimeManifest `
  -AllowFailures
```

To exercise every backend on a target machine and print a matrix summary, run:

```powershell
.\tools\smoke-mpv-rife.ps1 -RuntimeRoot .\runtime\mpv -Backend ALL
```

The script generates a two-frame 1440x810 Y4M clip under `build/mpv-smoke/`,
runs the selected RIFE backend with `--vo=null --ao=null`, writes an mpv log next
to the clip, and fails on timeout or non-zero exit. `-Backend ALL` prints a
summary table for NVIDIA, DIRECTML, and STANDARD. Add `-AllowFailures` if you
want the matrix report and optional JSON report to finish even when one or more
backends fail. `tools/verify-windows-port.ps1 -Rife` runs this smoke and then
validates the generated JSON report with `assert-mpv-rife-report.ps1` and
`-RequireRuntimeManifest`, so target-host evidence must come from a runtime that
still carries a valid manifest. Use
`-Backend NVIDIA` on a machine with a compatible NVIDIA driver/CUDA stack; use
`-Backend STANDARD` only when the payload includes the extra Standard backend
plugin stack.

Validation note: the current local runtime was prepared from
`mpv-lazy-20260510.exe` plus the `mpv-lazy-20260510-vsNV.7z.001` overlay and
passed `:desktop-app:smokeMpvRuntime -PrequireMpvRuntime=true
-PrequiredRifeBackends=NVIDIA,DIRECTML` with
`mpv v0.41.0-615-g7b057f66f`. The local release zip gate
`:desktop-app:smokePackagedMpvRuntime -PmpvRuntimeSource=runtime\mpv
-PrequireMpvRuntime=true -PrequiredRifeBackends=NVIDIA,DIRECTML` also passed
and verified the bundled `desktop-app.zip` runtime entries.
`tools/smoke-mpv-rife.ps1 -Backend DIRECTML` also passed using
`MEMC_RIFE_DML.vpy` and a generated two-frame 1440x810 Y4M clip. NVIDIA reached
the `vsmlrt`/TensorRT path but failed on this machine because the CUDA driver is
too old for the bundled CUDA runtime; Standard script presence is optional, and
its runtime path currently requires an additional `rife` VapourSynth plugin.

## Overrides

Runtime discovery checks these locations in order:

1. Java system property `miruplay.mpv.runtime`
2. Environment variable `MIRUPLAY_MPV_RUNTIME`
3. `runtime/mpv` next to the installed desktop app
4. `app/runtime/mpv` inside app-image layouts that keep extra content under `app/`
5. `runtime/mpv` under the current working directory
6. `mpv` under the current working directory

Use overrides for development or for users who want to keep mpv_PlayKit outside
the MiruPlay install directory.
