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
. (Join-Path $scriptRoot "desktop-window-helper.ps1")
if ([string]::IsNullOrWhiteSpace($AppScript)) {
    $AppScript = Join-Path $scriptRoot "..\desktop-app\build\install\desktop-app\bin\desktop-app.bat"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $scriptRoot "..\build\desktop-local-source-ui"
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
    public static extern bool SetCursorPos(int X, int Y);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
}
"@

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlayLocalSourceSmokeWin32+RECT
    if (-not [MiruPlayLocalSourceSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
        throw "Unable to read window bounds for process $($Process.Id)."
    }
    return $rect
}

function Invoke-RelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y
    )

    $rect = Get-WindowRect -Process $Process
    [MiruPlayLocalSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
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

    [MiruPlayLocalSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
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
        if ($sentinelSet -and $text -eq "__MIRUPLAY_EMPTY_SELECTION__") {
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

function Read-StoreState {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Wait-StoreState {
    param(
        [string]$Path,
        [scriptblock]$Predicate,
        [string]$Description,
        [int]$TimeoutSeconds = 20
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = Read-StoreState -Path $Path
        if ($state -and (& $Predicate $state)) {
            return $state
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Description in $Path."
}

function Assert-ScreenshotHasContent {
    param([string]$Path)
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -lt 20000) {
        throw "Screenshot file is unexpectedly small: $Path ($($file.Length) bytes)"
    }

    $bitmap = [System.Drawing.Bitmap]::FromFile($Path)
    try {
        $colors = New-Object 'System.Collections.Generic.HashSet[int]'
        $redAccentPixels = 0
        $brightTextPixels = 0
        $xStep = [Math]::Max(1, [int]($bitmap.Width / 96))
        $yStep = [Math]::Max(1, [int]($bitmap.Height / 64))
        for ($x = 0; $x -lt $bitmap.Width; $x += $xStep) {
            for ($y = 0; $y -lt $bitmap.Height; $y += $yStep) {
                $pixel = $bitmap.GetPixel($x, $y)
                [void]$colors.Add($pixel.ToArgb())
                $r = [int]$pixel.R
                $g = [int]$pixel.G
                $b = [int]$pixel.B
                if ($r -ge 150 -and $g -ge 35 -and $g -le 125 -and $b -ge 45 -and $b -le 155 -and ($r - $g) -ge 55) {
                    $redAccentPixels++
                }
                if ($r -ge 140 -and $g -ge 140 -and $b -ge 140) {
                    $brightTextPixels++
                }
            }
        }
        if ($colors.Count -lt 28) {
            throw "Screenshot appears blank or nearly blank: $Path"
        }
        if ($redAccentPixels -lt 8) {
            throw "Screenshot is missing the expected MiruPlay red accent: $Path"
        }
        if ($brightTextPixels -lt 8) {
            throw "Screenshot has too little readable light text: $Path"
        }
    } finally {
        $bitmap.Dispose()
    }
}

function Save-WindowScreenshot {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Path
    )

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

$resolvedAppScript = Resolve-FullPath $AppScript
$resolvedOutputRoot = Resolve-FullPath $OutputRoot
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
    $resolvedLibraryRoot = Resolve-FullPath $LibraryRoot
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
$env:MIRUPLAY_DESKTOP_STORE = $storePath
$startedProcess = $null
try {
    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayDesktopWindowProcess

    Set-TextByRelativeClick -Process $windowProcess -X 240 -Y 268 -Text $resolvedLibraryRoot -Description "local library root" -SkipReadback
    Invoke-RelativeClick -Process $windowProcess -X 500 -Y 337
    Wait-StoreState -Path $storePath -Description "saved local source" -Predicate {
        param($state)
        @($state.mediaSources).Count -ge 1
    } | Out-Null

    Invoke-RelativeClick -Process $windowProcess -X 664 -Y 337
    $state = Wait-StoreState -Path $storePath -Description "scanned local index entry" -Predicate {
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
    if (-not $KeepOpen) {
        $windowProcess = Get-MiruPlayDesktopWindowProcess
        if ($windowProcess) {
            $windowProcess.CloseMainWindow() | Out-Null
            Start-Sleep -Milliseconds 700
            if (-not $windowProcess.HasExited) {
                $runningWindowProcess = Get-Process -Id $windowProcess.Id -ErrorAction SilentlyContinue
                if ($runningWindowProcess) {
                    Stop-Process -Id $windowProcess.Id -Force -ErrorAction SilentlyContinue
                }
            }
        }
        if ($startedProcess -and -not $startedProcess.HasExited) {
            $runningStartedProcess = Get-Process -Id $startedProcess.Id -ErrorAction SilentlyContinue
            if ($runningStartedProcess) {
                Stop-Process -Id $startedProcess.Id -Force -ErrorAction SilentlyContinue
            }
        }
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

