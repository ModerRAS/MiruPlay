[CmdletBinding()]
param(
    [string]$AppScript = "",
    [string]$OutputRoot = "",
    [string]$LibraryRoot = "",
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"
$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot "..\..\.."))
. (Join-Path $scriptRoot "desktop-window-helper.ps1")
. (Join-Path $scriptRoot "desktop-smoke-common.ps1")
if ([string]::IsNullOrWhiteSpace($AppScript)) {
    $AppScript = Join-Path $repoRoot "desktop-app\build\install\desktop-app\bin\desktop-app.bat"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot "build\desktop-local-source-ui"
}

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlayLocalSourceSmokeWin32 {
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

    [DllImport("user32.dll")]
    public static extern bool SetCursorPos(int X, int Y);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
}
"@

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlayLocalSourceSmokeWin32+RECT
    if (-not [MiruPlayLocalSourceSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
        throw "Unable to read window bounds for process $($Process.Id)."
    }
    return $rect
}

function Set-MiruPlayWindowForeground {
    param([System.Diagnostics.Process]$Process)

    $Process.Refresh()
    if ($Process.HasExited -or $Process.MainWindowHandle -eq 0) {
        throw "MiruPlay Desktop window is not available for process $($Process.Id)."
    }

    $hwndTopMost = [IntPtr]::new(-1)
    $hwndNoTopMost = [IntPtr]::new(-2)
    $swRestore = 9
    $swpNoSize = 0x0001
    $swpShowWindow = 0x0040
    $moveFlags = $swpNoSize -bor $swpShowWindow
    $raiseFlags = $swpNoSize -bor 0x0002 -bor $swpShowWindow
    $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea

    [MiruPlayLocalSourceSmokeWin32]::ShowWindow($Process.MainWindowHandle, $swRestore) | Out-Null
    [MiruPlayLocalSourceSmokeWin32]::SetWindowPos($Process.MainWindowHandle, $hwndTopMost, $bounds.Left, $bounds.Top, 0, 0, $moveFlags) | Out-Null
    [MiruPlayLocalSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 160
    [MiruPlayLocalSourceSmokeWin32]::SetWindowPos($Process.MainWindowHandle, $hwndNoTopMost, $bounds.Left, $bounds.Top, 0, 0, $raiseFlags) | Out-Null
    [MiruPlayLocalSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 220
}

function Invoke-RelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y
    )

    Set-MiruPlayWindowForeground -Process $Process
    $rect = Get-WindowRect -Process $Process
    Start-Sleep -Milliseconds 150
    [MiruPlayLocalSourceSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlayLocalSourceSmokeWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlayLocalSourceSmokeWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 350
}

function Send-AppKeys {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Keys,
        [int]$DelayMilliseconds = 350
    )

    Set-MiruPlayWindowForeground -Process $Process
    Start-Sleep -Milliseconds 120
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
    Start-Sleep -Milliseconds $DelayMilliseconds
}

function Set-ClipboardValue {
    param(
        [AllowEmptyString()]
        [string]$Text,
        [int]$Attempts = 4,
        [switch]$IgnoreFailure
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            Set-Clipboard -Value $Text -ErrorAction Stop
            return $true
        } catch {
            if ($attempt -ge $Attempts) {
                if ($IgnoreFailure) {
                    return $false
                }
                throw
            }
            Start-Sleep -Milliseconds 120
        }
    }

    return $false
}

function Set-FocusedText {
    param([string]$Text)
    [void](Set-ClipboardValue -Text $Text)
    [System.Windows.Forms.SendKeys]::SendWait("^a")
    Start-Sleep -Milliseconds 80
    [System.Windows.Forms.SendKeys]::SendWait("^v")
    Start-Sleep -Milliseconds 350
}

function Set-TextByRelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y,
        [string]$Text,
        [string]$Description,
        [switch]$SkipReadback,
        [int]$Attempts = 3
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        Invoke-RelativeClick -Process $Process -X $X -Y $Y
        Set-FocusedText -Text $Text
        if ($SkipReadback) {
            return
        }
        $actual = Get-FocusedText
        if ($actual -eq $Text) {
            return
        }
        Start-Sleep -Milliseconds 300
    }

    throw "Unable to set $Description to '$Text'."
}

function Get-FocusedText {
    $before = Get-Clipboard -Raw -ErrorAction SilentlyContinue
    try {
        $sentinelSet = Set-ClipboardValue -Text "__MIRUPLAY_EMPTY_SELECTION__" -IgnoreFailure
        [System.Windows.Forms.SendKeys]::SendWait("^a")
        Start-Sleep -Milliseconds 80
        [System.Windows.Forms.SendKeys]::SendWait("^c")
        Start-Sleep -Milliseconds 200
        $text = (Get-Clipboard -Raw -ErrorAction SilentlyContinue).Trim()
        if ($text -eq "__MIRUPLAY_EMPTY_SELECTION__") {
            return ""
        }
        return $text
    } finally {
        if ($null -ne $before) {
            [void](Set-ClipboardValue -Text $before -IgnoreFailure)
        }
    }
}

function Get-FocusedTextWithRetry {
    param(
        [int]$Attempts = 4,
        [int]$DelayMilliseconds = 180
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $text = Get-FocusedText
        if (-not [string]::IsNullOrWhiteSpace($text) -or $attempt -ge $Attempts) {
            return $text
        }
        Start-Sleep -Milliseconds $DelayMilliseconds
    }

    return ""
}

function Assert-ScreenshotHasContent {
    param([string]$Path)

    Assert-DesktopSmokeScreenshotQuality -Path $Path
}

function Save-WindowScreenshot {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Path,
        [int]$Attempts = 5
    )

    $lastError = $null
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            Set-MiruPlayWindowForeground -Process $Process
            $rect = Get-WindowRect -Process $Process
            $width = $rect.Right - $rect.Left
            $height = $rect.Bottom - $rect.Top
            if ($width -lt 1100 -or $height -lt 700) {
                throw "Window is smaller than expected for TV-style QA: ${width}x$height"
            }

            $bitmap = New-Object System.Drawing.Bitmap($width, $height)
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)
                $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $graphics.Dispose()
                $bitmap.Dispose()
            }
            Assert-ScreenshotHasContent -Path $Path
            return
        } catch {
            $lastError = $_
            if ($attempt -ge $Attempts) {
                break
            }
            Start-Sleep -Milliseconds (250 * $attempt)
        }
    }

    throw "Unable to capture MiruPlay Desktop screenshot after $Attempts attempts: $($lastError.Exception.Message)"
}

function Test-EntryMatchesQuery {
    param(
        [object]$Entry,
        [string]$Query
    )

    $normalizedQuery = $Query.Trim().ToLowerInvariant()
    if (-not $normalizedQuery) {
        return $true
    }

    $fields = @(
        $Entry.path,
        $Entry.animeName,
        $Entry.episodeTitle,
        $Entry.plot,
        $Entry.metadataTitle,
        $Entry.metadataId
    )
    foreach ($field in $fields) {
        if ($null -ne $field -and $field.ToString().ToLowerInvariant().Contains($normalizedQuery)) {
            return $true
        }
    }
    return $false
}

function Get-SearchMatches {
    param(
        [object[]]$Entries,
        [string]$Query
    )

    @($Entries |
        Where-Object { Test-EntryMatchesQuery -Entry $_ -Query $Query } |
        Sort-Object { $_.path.ToString().ToLowerInvariant() })
}

function Get-EntryFilenameStem {
    param([object]$Entry)

    $fileName = [System.IO.Path]::GetFileNameWithoutExtension($Entry.path.ToString())
    if ($fileName.Trim()) {
        return $fileName.Trim()
    }
    if ($Entry.animeName -and $Entry.animeName.ToString().Trim()) {
        return $Entry.animeName.ToString().Trim()
    }
    throw "Unable to derive a non-empty search query for indexed entry $($Entry.path)."
}

$resolvedAppScript = Resolve-DesktopSmokeFullPath $AppScript
$resolvedOutputRoot = Resolve-DesktopSmokeFullPath $OutputRoot
if (-not (Test-Path -LiteralPath $resolvedAppScript)) {
    throw "Desktop app launcher was not found at $resolvedAppScript. Run :desktop-app:installDist first."
}
if (Get-MiruPlayDesktopWindowProcess) {
    throw "A MiruPlay Desktop window is already open. Close it before running this isolated smoke test."
}

$runName = "run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$runDir = Join-Path $resolvedOutputRoot $runName
$fixtureDir = Join-Path $runDir "media\Frieren"
$storePath = Join-Path $runDir "store\desktop-store.json"
$initialScreenshotPath = Join-Path $runDir "local-source-initial.png"
$openAttemptScreenshotPath = Join-Path $runDir "local-source-open-attempt.png"
$scanScreenshotPath = Join-Path $runDir "local-source-scanned.png"
$searchScreenshotPath = Join-Path $runDir "local-source-search.png"
$posterKeyboardScreenshotPath = Join-Path $runDir "local-source-poster-keyboard.png"
$detailsScreenshotPath = Join-Path $runDir "local-source-details.png"
$detailsEpisodesScreenshotPath = Join-Path $runDir "local-source-details-episodes.png"
$detailsEpisodeBackToHeroScreenshotPath = Join-Path $runDir "local-source-details-episode-back-to-hero.png"
$detailsEpisodeSelectionScreenshotPath = Join-Path $runDir "local-source-details-episode-selected.png"
$detailsEpisodeToBangumiScreenshotPath = Join-Path $runDir "local-source-details-episode-to-bangumi.png"
$detailsBangumiBackToEpisodeScreenshotPath = Join-Path $runDir "local-source-details-bangumi-back-to-episode.png"
$playerScreenshotPath = Join-Path $runDir "local-source-player.png"
New-Item -ItemType Directory -Path (Split-Path -Parent $storePath) -Force | Out-Null

if ($LibraryRoot.Trim()) {
    $resolvedLibraryRoot = Resolve-DesktopSmokeFullPath $LibraryRoot
    if (-not (Test-Path -LiteralPath $resolvedLibraryRoot -PathType Container)) {
        throw "LibraryRoot does not exist or is not a directory: $resolvedLibraryRoot"
    }
} else {
    New-Item -ItemType Directory -Path $fixtureDir -Force | Out-Null
    $firstEpisodePath = Join-Path $fixtureDir "Frieren - S01E01.mkv"
    $firstNfoPath = Join-Path $fixtureDir "Frieren - S01E01.nfo"
    Set-Content -LiteralPath $firstEpisodePath -Value "first fixture video bytes" -Encoding UTF8
    Set-Content -LiteralPath $firstNfoPath -Encoding UTF8 -Value @"
<episodedetails>
  <showtitle>Fixture Frieren</showtitle>
  <title>Fixture First Episode</title>
  <season>1</season>
  <episode>1</episode>
  <plot>Fixture plot for desktop GUI episode shelf smoke.</plot>
</episodedetails>
"@
    $episodePath = Join-Path $fixtureDir "Frieren - S01E02.mkv"
    $nfoPath = Join-Path $fixtureDir "Frieren - S01E02.nfo"
    Set-Content -LiteralPath $episodePath -Value "fixture video bytes" -Encoding UTF8
    Set-Content -LiteralPath $nfoPath -Encoding UTF8 -Value @"
<episodedetails>
  <showtitle>Fixture Frieren</showtitle>
  <title>Fixture Episode</title>
  <season>1</season>
  <episode>2</episode>
  <plot>Fixture plot for desktop GUI smoke.</plot>
</episodedetails>
"@
    $secondFixtureDir = Join-Path $runDir "media\Bocchi"
    New-Item -ItemType Directory -Path $secondFixtureDir -Force | Out-Null
    $secondEpisodePath = Join-Path $secondFixtureDir "Bocchi - S01E01.mkv"
    $secondNfoPath = Join-Path $secondFixtureDir "Bocchi - S01E01.nfo"
    Set-Content -LiteralPath $secondEpisodePath -Value "second fixture video bytes" -Encoding UTF8
    Set-Content -LiteralPath $secondNfoPath -Encoding UTF8 -Value @"
<episodedetails>
  <showtitle>Fixture Bocchi</showtitle>
  <title>Fixture Second Episode</title>
  <season>1</season>
  <episode>1</episode>
  <plot>Second fixture plot for desktop GUI search smoke.</plot>
</episodedetails>
"@
    $resolvedLibraryRoot = Split-Path -Parent $fixtureDir
}

$previousClipboard = Get-Clipboard -Raw -ErrorAction SilentlyContinue
$previousStoreEnv = $env:MIRUPLAY_DESKTOP_STORE
$previousInitialLibraryRootEnv = $env:MIRUPLAY_DESKTOP_INITIAL_LIBRARY_ROOT
$env:MIRUPLAY_DESKTOP_STORE = $storePath
$env:MIRUPLAY_DESKTOP_INITIAL_LIBRARY_ROOT = $resolvedLibraryRoot
$startedProcess = $null
try {
    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayDesktopWindowProcess
    Start-Sleep -Milliseconds 1500
    Save-WindowScreenshot -Process $windowProcess -Path $initialScreenshotPath
    Write-Output "Initial screenshot: $initialScreenshotPath"

    Invoke-RelativeClick -Process $windowProcess -X 500 -Y 337
    Start-Sleep -Milliseconds 700
    Save-WindowScreenshot -Process $windowProcess -Path $openAttemptScreenshotPath
    Write-Output "Open attempt screenshot: $openAttemptScreenshotPath"
    Wait-DesktopSmokeStoreState -Path $storePath -Description "saved local source" -Predicate {
        param($state)
        @($state.mediaSources).Count -ge 1
    } | Out-Null

    Invoke-RelativeClick -Process $windowProcess -X 664 -Y 337
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "scanned local index entry" -Predicate {
        param($state)
        @($state.index | Where-Object { -not $_.isDirectory }).Count -ge 1
    } -TimeoutSeconds 90

    $source = @($state.mediaSources)[0]
    $indexedVideos = @($state.index | Where-Object { -not $_.isDirectory })
    if ($source.type -ne "LOCAL") {
        throw "Expected LOCAL source, found $($source.type)."
    }
    if ($source.connectionInfo.path -ne $resolvedLibraryRoot) {
        throw "Stored source path does not match library root: $($source.connectionInfo.path)"
    }
    if (-not $LibraryRoot.Trim()) {
        $frierenVideo = @($indexedVideos | Where-Object { $_.animeName -eq "Fixture Frieren" })
        if ($frierenVideo.Count -ne 2) {
            throw "Expected exactly two NFO episodes named 'Fixture Frieren', found $($frierenVideo.Count)."
        }
        $frierenEpisodeNumbers = @($frierenVideo | Sort-Object episodeNumber | ForEach-Object { $_.episodeNumber })
        if (($frierenEpisodeNumbers -join ",") -ne "1,2") {
            throw "Expected Frieren episode numbers 1,2, found '$($frierenEpisodeNumbers -join ",")'."
        }
        if ($indexedVideos.Count -lt 3) {
            throw "Expected at least three generated fixture videos for search filtering, found $($indexedVideos.Count)."
        }
    }

    Save-WindowScreenshot -Process $windowProcess -Path $scanScreenshotPath

    if (-not $LibraryRoot.Trim()) {
        $searchQuery = "Frieren"
        $indexedVideos = Get-SearchMatches -Entries @($state.index | Where-Object { -not $_.isDirectory }) -Query $searchQuery
        if ($indexedVideos.Count -ne 2 -or @($indexedVideos | Where-Object { $_.animeName -ne "Fixture Frieren" }).Count -ne 0) {
            throw "Expected repository search helper to isolate Fixture Frieren, found $($indexedVideos.Count) result(s)."
        }
        Save-WindowScreenshot -Process $windowProcess -Path $searchScreenshotPath
    }

    if ($LibraryRoot.Trim()) {
        $selectedVideo = $null
        Invoke-RelativeClick -Process $windowProcess -X 130 -Y 360
    } else {
        $selectedVideo = @($indexedVideos | Where-Object { $_.episodeNumber -eq 2 })[0]
        Send-AppKeys -Process $windowProcess -Keys "{RIGHT}"
        Save-WindowScreenshot -Process $windowProcess -Path $posterKeyboardScreenshotPath
        Send-AppKeys -Process $windowProcess -Keys "{ENTER}"
    }
    Start-Sleep -Milliseconds 700
    Save-WindowScreenshot -Process $windowProcess -Path $detailsScreenshotPath

    if (-not $LibraryRoot.Trim()) {
        Send-AppKeys -Process $windowProcess -Keys "{DOWN}"
        Save-WindowScreenshot -Process $windowProcess -Path $detailsEpisodesScreenshotPath
        Send-AppKeys -Process $windowProcess -Keys "{UP}"
        Save-WindowScreenshot -Process $windowProcess -Path $detailsEpisodeBackToHeroScreenshotPath
        Send-AppKeys -Process $windowProcess -Keys "{DOWN}"
        Send-AppKeys -Process $windowProcess -Keys "{DOWN}"
        Save-WindowScreenshot -Process $windowProcess -Path $detailsEpisodeSelectionScreenshotPath
        Send-AppKeys -Process $windowProcess -Keys "{DOWN}"
        Save-WindowScreenshot -Process $windowProcess -Path $detailsEpisodeToBangumiScreenshotPath
        Send-AppKeys -Process $windowProcess -Keys "{UP}"
        Save-WindowScreenshot -Process $windowProcess -Path $detailsBangumiBackToEpisodeScreenshotPath
        Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 500
    } else {
        Invoke-RelativeClick -Process $windowProcess -X 674 -Y 466
    }
    Start-Sleep -Milliseconds 500
    Invoke-RelativeClick -Process $windowProcess -X 520 -Y 615
    $selectedMediaPath = Get-FocusedTextWithRetry
    if ([string]::IsNullOrWhiteSpace($selectedMediaPath)) {
        Write-Warning "Unable to read player media path from the focused input; skipping strict path assertion."
    } elseif ($LibraryRoot.Trim()) {
        $indexedPaths = @($indexedVideos | ForEach-Object { $_.path })
        if ($selectedMediaPath -notin $indexedPaths) {
            throw "Player media path did not match any indexed poster. Found '$selectedMediaPath'."
        }
    } elseif ($selectedMediaPath -ne $selectedVideo.path) {
        throw "Player media path did not match selected poster. Expected '$($selectedVideo.path)', found '$selectedMediaPath'."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $playerScreenshotPath
} finally {
    if ($null -ne $previousClipboard) {
        if (-not (Set-ClipboardValue -Text $previousClipboard -IgnoreFailure)) {
            Write-Warning "Failed to restore clipboard after local-source smoke."
        }
    }
    $env:MIRUPLAY_DESKTOP_STORE = $previousStoreEnv
    $env:MIRUPLAY_DESKTOP_INITIAL_LIBRARY_ROOT = $previousInitialLibraryRootEnv
    if (-not $KeepOpen) {
        Close-MiruPlayDesktopWindowProcessIfRunning
        Stop-MiruPlayDesktopProcessIfRunning -Process $startedProcess
    }
}

Write-Output "Run directory: $runDir"
Write-Output "Store: $storePath"
Write-Output "Scan screenshot: $scanScreenshotPath"
if (-not $LibraryRoot.Trim()) {
    Write-Output "Search screenshot: $searchScreenshotPath"
    Write-Output "Poster keyboard screenshot: $posterKeyboardScreenshotPath"
}
Write-Output "Details screenshot: $detailsScreenshotPath"
if (-not $LibraryRoot.Trim()) {
    Write-Output "Details episodes screenshot: $detailsEpisodesScreenshotPath"
    Write-Output "Details episode back-to-hero screenshot: $detailsEpisodeBackToHeroScreenshotPath"
    Write-Output "Details episode selection screenshot: $detailsEpisodeSelectionScreenshotPath"
    Write-Output "Details episode-to-Bangumi screenshot: $detailsEpisodeToBangumiScreenshotPath"
    Write-Output "Details Bangumi back-to-episode screenshot: $detailsBangumiBackToEpisodeScreenshotPath"
}
Write-Output "Player screenshot: $playerScreenshotPath"

