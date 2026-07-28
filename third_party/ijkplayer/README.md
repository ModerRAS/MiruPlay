# debugly/ijkplayer Android artifact

MiruPlay pins the upstream Android release artifact for its experimental ijkplayer backend.

## Pinned artifact

- Upstream: https://github.com/debugly/ijkplayer
- Source commit: `2a5114f6b276744159769f17b38bfab1e74ed5ad`
- FFToolChain commit: `b7847f3b362f605085e38a0169f870bf98d0845f`
- Release tag: `k0.8.9-beta-260526101841`
- Artifact URL: https://github.com/debugly/ijkplayer/releases/download/k0.8.9-beta-260526101841/ijkplayer-cmake-release.aar
- Artifact SHA-256: `5e6c3287a361ee0be54df0eb8db0af7d6488f92f5711776ac1fc09027b997e60`
- Upstream Android POM license: LGPL-2.1
- Core source headers: LGPL-2.1-or-later

The AAR is not nested into another Android library. MiruPlay extracts and pins only the Java classes plus its two shipped ABI payloads:

- `player-ijkplayer-android/libs/ijkplayer-classes.jar`: `a441dd1f60251dc4f2c6dc5d885d7a446964412be68dd2fb068b7a23d4a90f7a`
- `arm64-v8a/libijkplayer.so`: `d9f16bbcb68c5fd8ba0f56a8b6f788962631a6749e82511f0f18305527e2d10b`
- `armeabi-v7a/libijkplayer.so`: `6070c560264097dfb44e289c61f6b22dd2dacf154ad770358599210430d2318a`

## Corresponding source and build

The pinned source and build inputs are available at:

- https://github.com/debugly/ijkplayer/tree/2a5114f6b276744159769f17b38bfab1e74ed5ad
- https://github.com/debugly/FFToolChain/tree/b7847f3b362f605085e38a0169f870bf98d0845f

On a POSIX host with JDK 17 or newer, Android SDK 35, Android NDK r27c, CMake 3.22.1, and Git LFS/submodule support:

```bash
git clone https://github.com/debugly/ijkplayer.git
git -C ijkplayer checkout 2a5114f6b276744159769f17b38bfab1e74ed5ad
git -C ijkplayer submodule update --init --recursive
cd ijkplayer/android
./install-ffmpeg.sh
cd ijkplayer
./gradlew :ijkplayer-cmake:assembleRelease --no-daemon
```

The output is `ijkplayer-cmake/build/outputs/aar/ijkplayer-cmake-release.aar`. The source tree uses symbolic links; Windows checkouts must preserve or materialize `ijkmedia` and `third-libs` before configuring CMake.

An independent Windows build from the pinned commits compiled 94 ijkplayer C/C++ translation units for both shipped ABIs. Its `classes.jar` was byte-identical to the pinned artifact. Both native outputs had identical defined-global-symbol sets, ELF section sets, and dynamic dependencies. Their bytes differ because the upstream macOS NDK compiler carries PGO/LTO metadata and the independent Windows compiler does not; the independent copy also used the release version string where the upstream binary embeds source short hash `2a5114f`.

## Static dependency audit

`libijkplayer.so` statically incorporates these pinned FFToolChain products:

| Component | Source revision | License | Configuration |
| --- | --- | --- | --- |
| FFmpeg fork | `ff4.0--ijk0.8.8--20210426--001` | LGPL-2.1-or-later | `--disable-gpl --disable-nonfree`; selected decoders/demuxers/protocols only |
| OpenSSL | `OpenSSL_1_1_1w` | OpenSSL/SSLeay | Static TLS/crypto dependency of libavformat |
| SoundTouch | `2.4.0` | LGPL-2.1-or-later | Static playback-rate processing |
| libyuv | `f94b8cf7` | BSD-3-Clause | Static pixel conversion |

The final ARM ELF files dynamically depend only on Android platform libraries: `liblog`, `libandroid`, `libEGL`, `libGLESv2`, `libjnigraphics`, `libOpenSLES`, `libm`, `libz`, `libdl`, and `libc`.

`COPYING.LGPLv2.1`, `LICENSE.OpenSSL-1.1.1w`, `LICENSE.libyuv`, and `NOTICE` accompany the binary in both this directory and the APK assets. MiruPlay remains GPLv3.
