[CmdletBinding()]
param(
    [string]$AppScript,
    [string]$OutputRoot,
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
. (Join-Path $scriptRoot "desktop-window-helper.ps1")
. (Join-Path $scriptRoot "desktop-smoke-common.ps1")
if ([string]::IsNullOrWhiteSpace($AppScript)) {
    $AppScript = Join-Path $scriptRoot "..\desktop-app\build\install\desktop-app\bin\desktop-app.bat"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $scriptRoot "..\build\desktop-bangumi-metadata-ui"
}
$bangumiFixtureTitle = ([char]0x846C) + ([char]0x9001) + ([char]0x7684) + ([char]0x8299) + ([char]0x8389) + ([char]0x83B2)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
if (-not ("MiruPlayBangumiMetadataSmokeWin32" -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlayBangumiMetadataSmokeWin32 {
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
    public static extern void mouse_event(uint flags, uint dx, uint dy, int data, UIntPtr extraInfo);
}
"@
}

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlayBangumiMetadataSmokeWin32+RECT
    if (-not [MiruPlayBangumiMetadataSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
        throw "Unable to read window bounds for process $($Process.Id)."
    }
    return $rect
}

function Invoke-RelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y,
        [int]$DelayMilliseconds = 350
    )

    $rect = Get-WindowRect -Process $Process
    [MiruPlayBangumiMetadataSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
    [MiruPlayBangumiMetadataSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlayBangumiMetadataSmokeWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlayBangumiMetadataSmokeWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds $DelayMilliseconds
}

function Send-AppKeys {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Keys,
        [int]$DelayMilliseconds = 350
    )

    [MiruPlayBangumiMetadataSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 120
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
    Start-Sleep -Milliseconds $DelayMilliseconds
}

function Assert-ScreenshotHasContent {
    param([string]$Path)

    Assert-DesktopSmokeScreenshotQuality -Path $Path
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

function Write-InitialStore {
    param(
        [string]$Path,
        [string]$LibraryRoot,
        [string]$EpisodePath
    )

    $json = @{
        nextSourceId = 2
        nextRssSubscriptionId = 1
        nextRssDownloadTaskId = 1
        mediaSources = @(
            @{
                id = 1
                name = "Bangumi Fixture"
                type = "LOCAL"
                connectionInfo = @{
                    path = $LibraryRoot
                }
                isConnected = $true
                lastScanned = 0
            }
        )
        progress = @()
        index = @(
            @{
                sourceId = 1
                path = $EpisodePath
                animeName = $bangumiFixtureTitle
                episodeTitle = "Fixture Metadata Episode"
                plot = "Fixture plot for Bangumi metadata GUI smoke."
                seasonNumber = 1
                episodeNumber = 1
                metadataSource = $null
                metadataId = $null
                metadataTitle = $null
                isDirectory = $false
                fileSize = 24
                lastModified = 0
            }
        )
        indexBatchUndo = @()
        cloudDriveConfig = @{
            endpointUrl = ""
            username = ""
            webDavSourceId = $null
            inboxPath = ""
            libraryPath = ""
            intervalMinutes = 30
            enabled = $false
            lastRunAt = 0
            rssProxyEnabled = $false
            rssProxyHost = ""
            rssProxyPort = 1080
        }
        rssSubscriptions = @()
        rssProcessedItems = @()
        rssDownloadTasks = @()
        cloudDriveToken = $null
        cloudDrivePassword = $null
        bangumiAccessToken = $null
    } | ConvertTo-Json -Depth 12

    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
    Set-Content -LiteralPath $Path -Value $json -Encoding UTF8
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
$episodePath = Join-Path $fixtureDir "Frieren - S01E01.mkv"
$detailsScreenshotPath = Join-Path $runDir "bangumi-details-ready.png"
$focusBangumiScreenshotPath = Join-Path $runDir "bangumi-focus-bangumi.png"
$searchScreenshotPath = Join-Path $runDir "bangumi-search-results.png"
$appliedScreenshotPath = Join-Path $runDir "bangumi-metadata-applied.png"
$clearedScreenshotPath = Join-Path $runDir "bangumi-metadata-cleared.png"
New-Item -ItemType Directory -Path $fixtureDir -Force | Out-Null
Set-Content -LiteralPath $episodePath -Value "fixture metadata video bytes" -Encoding UTF8
Write-InitialStore -Path $storePath -LibraryRoot (Split-Path -Parent $fixtureDir) -EpisodePath $episodePath

$previousStoreEnv = $env:MIRUPLAY_DESKTOP_STORE
$startedProcess = $null
try {
    $env:MIRUPLAY_DESKTOP_STORE = $storePath
    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayDesktopWindowProcess

    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 900
    Save-WindowScreenshot -Process $windowProcess -Path $detailsScreenshotPath

    Send-AppKeys -Process $windowProcess -Keys "{DOWN}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{DOWN}" -DelayMilliseconds 350
    Save-WindowScreenshot -Process $windowProcess -Path $focusBangumiScreenshotPath
    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 350
    Send-AppKeys -Process $windowProcess -Keys "{RIGHT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 3500
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "metadata still clear after search" -Predicate {
        param($state)
        $entry = @($state.index | Where-Object { -not $_.isDirectory })[0]
        $entry.metadataId -eq $null -and $entry.animeName -eq $bangumiFixtureTitle
    }
    Save-WindowScreenshot -Process $windowProcess -Path $searchScreenshotPath

    Send-AppKeys -Process $windowProcess -Keys "{RIGHT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{RIGHT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{LEFT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 900
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "applied Bangumi metadata" -Predicate {
        param($state)
        $entry = @($state.index | Where-Object { -not $_.isDirectory })[0]
        $entry.metadataSource -eq "BANGUMI" -and
            $entry.metadataId -eq "400602" -and
            $entry.metadataTitle -eq $bangumiFixtureTitle
    } -TimeoutSeconds 20
    Save-WindowScreenshot -Process $windowProcess -Path $appliedScreenshotPath

    Send-AppKeys -Process $windowProcess -Keys "{RIGHT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 900
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "cleared Bangumi metadata" -Predicate {
        param($state)
        $entry = @($state.index | Where-Object { -not $_.isDirectory })[0]
        $entry.metadataSource -eq $null -and
            $entry.metadataId -eq $null -and
            $entry.metadataTitle -eq $null
    } -TimeoutSeconds 20
    Save-WindowScreenshot -Process $windowProcess -Path $clearedScreenshotPath
} finally {
    $env:MIRUPLAY_DESKTOP_STORE = $previousStoreEnv
    if (-not $KeepOpen) {
        $windowProcess = Get-MiruPlayDesktopWindowProcess
        if ($windowProcess) {
            $windowProcess.CloseMainWindow() | Out-Null
            Start-Sleep -Milliseconds 700
            if (-not $windowProcess.HasExited) {
                Stop-Process -Id $windowProcess.Id -Force
            }
        }
        if ($startedProcess -and -not $startedProcess.HasExited) {
            Stop-Process -Id $startedProcess.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Output "Run directory: $runDir"
Write-Output "Store: $storePath"
Write-Output "Details screenshot: $detailsScreenshotPath"
Write-Output "Bangumi focus screenshot: $focusBangumiScreenshotPath"
Write-Output "Search screenshot: $searchScreenshotPath"
Write-Output "Applied screenshot: $appliedScreenshotPath"
Write-Output "Cleared screenshot: $clearedScreenshotPath"

