# Windows Release Publication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the Windows desktop ZIP into the same GitHub Release as the main Android APK on `master`/`main` pushes.

**Architecture:** Keep the existing Android release job as the source of truth for version calculation and release creation. Add a Windows release job that consumes the release version outputs, builds the Windows ZIP with that exact version, and attaches the ZIP to the already-created GitHub Release.

**Tech Stack:** GitHub Actions, Gradle, Compose Desktop/Java packaging, `softprops/action-gh-release`

---

### Task 1: Extend the release workflow outputs

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Expose the release version outputs from `build-release`**

```yaml
    outputs:
      version_name: ${{ steps.version.outputs.version_name }}
      version_code: ${{ steps.version.outputs.version_code }}
      tag_name: ${{ steps.version.outputs.tag_name }}
```

- [ ] **Step 2: Verify the workflow still defines the existing Android release steps unchanged**

Run: `rg -n -C 3 "Build version|Create GitHub Release|outputs:" .github/workflows/ci.yml`
Expected: the Android release job still computes the release version once and still creates the Android APK release as before.

### Task 2: Add the Windows release publication job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Add a `release-windows-desktop` job that depends on `build-release`**

```yaml
  release-windows-desktop:
    runs-on: windows-latest
    needs: build-release
    permissions:
      contents: write
    if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/master'

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Java ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: 'gradle'

      - name: Setup Bun ${{ env.BUN_VERSION }}
        uses: oven-sh/setup-bun@v2
        with:
          bun-version: ${{ env.BUN_VERSION }}

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle.kts', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Build Windows desktop ZIP for release
        run: .\gradlew.bat :desktop-app:test :desktop-app:distZip "-PwindowsPackageVersion=${{ needs.build-release.outputs.version_name }}" -PbundleMpvRuntime=false --no-daemon --stacktrace

      - name: Attach Windows desktop ZIP to GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ needs.build-release.outputs.tag_name }}
          target_commitish: ${{ github.sha }}
          name: v${{ needs.build-release.outputs.version_name }}
          files: desktop-app/build/distributions/*.zip
          overwrite_files: true
          fail_on_unmatched_files: true
          prerelease: false
          draft: false
          make_latest: true
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 2: Verify the new job is wired to the release version instead of the nightly version**

Run: `rg -n -C 3 "release-windows-desktop|windowsPackageVersion=.*build-release.outputs.version_name|nightly-windows-desktop" .github/workflows/ci.yml`
Expected: the release job uses `build-release` outputs and the existing nightly job remains untouched.

