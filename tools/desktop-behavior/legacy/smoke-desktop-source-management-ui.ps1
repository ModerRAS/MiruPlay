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
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot "..\..\.."))
. (Join-Path $scriptRoot "desktop-window-helper.ps1")
. (Join-Path $scriptRoot "desktop-smoke-common.ps1")
if ([string]::IsNullOrWhiteSpace($AppScript)) {
    $AppScript = Join-Path $repoRoot "desktop-app\build\install\desktop-app\bin\desktop-app.bat"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot "build\desktop-source-management-ui"
}

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
if (-not ("MiruPlaySourceManagementSmokeWin32" -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlaySourceManagementSmokeWin32 {
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
    public static extern void mouse_event(uint flags, uint dx, uint dy, int data, UIntPtr extraInfo);

}
"@
}

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlaySourceManagementSmokeWin32+RECT
    if (-not [MiruPlaySourceManagementSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
        throw "Unable to read window bounds for process $($Process.Id)."
    }
    return $rect
}

function Set-MiruPlayWindowForeground {
    param([System.Diagnostics.Process]$Process)

    $hwndTopMost = [IntPtr]::new(-1)
    $hwndNoTopMost = [IntPtr]::new(-2)
    $swRestore = 9
    $swpNoSize = 0x0001
    $swpNoMove = 0x0002
    $swpShowWindow = 0x0040
    $flags = $swpNoSize -bor $swpNoMove -bor $swpShowWindow

    [MiruPlaySourceManagementSmokeWin32]::ShowWindow($Process.MainWindowHandle, $swRestore) | Out-Null
    [MiruPlaySourceManagementSmokeWin32]::SetWindowPos($Process.MainWindowHandle, $hwndTopMost, 0, 0, 0, 0, $flags) | Out-Null
    [MiruPlaySourceManagementSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 120
    [MiruPlaySourceManagementSmokeWin32]::SetWindowPos($Process.MainWindowHandle, $hwndNoTopMost, 0, 0, 0, 0, $flags) | Out-Null
    [MiruPlaySourceManagementSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 180
}

function Invoke-RelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y
    )

    $rect = Get-WindowRect -Process $Process
    Set-MiruPlayWindowForeground -Process $Process
    [MiruPlaySourceManagementSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlaySourceManagementSmokeWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlaySourceManagementSmokeWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 350
}

function Invoke-RelativeMouseWheel {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X = 650,
        [int]$Y = 500,
        [int]$Notches,
        [int]$DeltaPerNotch = 300
    )

    $rect = Get-WindowRect -Process $Process
    Set-MiruPlayWindowForeground -Process $Process
    [MiruPlaySourceManagementSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    $direction = if ($Notches -lt 0) { -1 } else { 1 }
    for ($i = 0; $i -lt [Math]::Abs($Notches); $i++) {
        [MiruPlaySourceManagementSmokeWin32]::mouse_event(0x0800, 0, 0, $direction * $DeltaPerNotch, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
    }
    Start-Sleep -Milliseconds 700
}

function Send-AppKeys {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Keys,
        [int]$DelayMilliseconds = 350
    )

    Set-MiruPlayWindowForeground -Process $Process
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

    Set-MiruPlayWindowForeground -Process $Process
    $rect = Get-WindowRect -Process $Process
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    if ($width -lt 1100 -or $height -lt 700) {
        throw "Window is smaller than expected for TV-style QA: ${width}x$height"
    }

    Save-DesktopSmokeWindowBitmap -Process $Process -Rect $rect -Path $Path
    Assert-ScreenshotHasContent -Path $Path
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
$fixtureDir = Join-Path $runDir "media\Very Long Source Name For Focus And Path Preview Validation\Season 01\Manage"
$secondFixtureDir = Join-Path $runDir "media\Very Long Source Name For Focus And Path Preview Validation\Season 02\Manage"
$storePath = Join-Path $runDir "store\desktop-store.json"
$scannedScreenshotPath = Join-Path $runDir "source-management-scanned.png"
$controlsScreenshotPath = Join-Path $runDir "source-management-controls.png"
$sourceSwitchScreenshotPath = Join-Path $runDir "source-management-saved-source-keyboard.png"
$clearedScreenshotPath = Join-Path $runDir "source-management-cleared.png"
$removeReadyScreenshotPath = Join-Path $runDir "source-management-remove-ready.png"
$removedScreenshotPath = Join-Path $runDir "source-management-removed.png"
New-Item -ItemType Directory -Path (Split-Path -Parent $storePath) -Force | Out-Null
New-Item -ItemType Directory -Path $fixtureDir -Force | Out-Null
New-Item -ItemType Directory -Path $secondFixtureDir -Force | Out-Null

$episodePath = Join-Path $fixtureDir "Manage - S01E01.mkv"
$nfoPath = Join-Path $fixtureDir "Manage - S01E01.nfo"
Set-Content -LiteralPath $episodePath -Value "fixture management video bytes" -Encoding UTF8
Set-Content -LiteralPath $nfoPath -Encoding UTF8 -Value @"
<episodedetails>
  <showtitle>Fixture Manage</showtitle>
  <title>Source Management Episode</title>
  <season>1</season>
  <episode>1</episode>
  <plot>Fixture plot for source management GUI smoke.</plot>
</episodedetails>
"@

$secondEpisodePath = Join-Path $secondFixtureDir "Manage - S02E01.mkv"
$secondNfoPath = Join-Path $secondFixtureDir "Manage - S02E01.nfo"
Set-Content -LiteralPath $secondEpisodePath -Value "fixture second source video bytes" -Encoding UTF8
Set-Content -LiteralPath $secondNfoPath -Encoding UTF8 -Value @"
<episodedetails>
  <showtitle>Fixture Manage Second</showtitle>
  <title>Second Source Episode</title>
  <season>2</season>
  <episode>1</episode>
  <plot>Fixture plot for saved-source keyboard switching.</plot>
</episodedetails>
"@

$resolvedLibraryRoot = Split-Path -Parent $fixtureDir
$secondResolvedLibraryRoot = Split-Path -Parent $secondFixtureDir
$initialStore = @{
    nextSourceId = 3
    nextRssSubscriptionId = 1
    nextRssDownloadTaskId = 1
    mediaSources = @(
        @{
            id = 1
            name = "Season 01"
            type = "LOCAL"
            connectionInfo = @{
                path = $resolvedLibraryRoot
            }
            isConnected = $true
            lastScanned = 0
        },
        @{
            id = 2
            name = "Season 02"
            type = "LOCAL"
            connectionInfo = @{
                path = $secondResolvedLibraryRoot
            }
            isConnected = $true
            lastScanned = 0
        }
    )
    progress = @()
    index = @()
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
}
$initialStoreJson = $initialStore | ConvertTo-Json -Depth 20
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($storePath, $initialStoreJson, $utf8NoBom)
$previousStoreEnv = $env:MIRUPLAY_DESKTOP_STORE
$env:MIRUPLAY_DESKTOP_STORE = $storePath
$startedProcess = $null
try {
    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayDesktopWindowProcess

    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "preloaded local sources" -Predicate {
        param($state)
        @($state.mediaSources).Count -eq 2
    }
    $savedSource = @($state.mediaSources | Where-Object { $_.connectionInfo.path -eq $resolvedLibraryRoot })[0]
    $sourceId = [long]$savedSource.id
    if ($savedSource.name -ne "Season 01") {
        throw "Expected saved source name to reflect the long path leaf 'Season 01', found '$($savedSource.name)'."
    }
    if ($savedSource.connectionInfo.path -ne $resolvedLibraryRoot) {
        throw "Saved source path did not preserve the long local root."
    }

    $secondSavedSource = @($state.mediaSources | Where-Object { $_.connectionInfo.path -eq $secondResolvedLibraryRoot })[0]
    if (-not $secondSavedSource) {
        throw "Second saved source was not persisted with path '$secondResolvedLibraryRoot'."
    }
    if ($secondSavedSource.name -ne "Season 02") {
        throw "Expected second saved source name 'Season 02', found '$($secondSavedSource.name)'."
    }

    Send-AppKeys -Process $windowProcess -Keys "{UP}" -DelayMilliseconds 900
    Start-Sleep -Milliseconds 900
    Save-WindowScreenshot -Process $windowProcess -Path $sourceSwitchScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 664 -Y 337
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "scanned local index entry" -Predicate {
        param($state)
        @($state.index | Where-Object { -not $_.isDirectory }).Count -eq 1
    } -TimeoutSeconds 90
    $indexedVideo = @($state.index | Where-Object { -not $_.isDirectory })[0]
    if ([long]$indexedVideo.sourceId -ne $sourceId) {
        throw "Expected scan to index the keyboard-selected source id $sourceId, found '$($indexedVideo.sourceId)'."
    }
    if ($indexedVideo.animeName -ne "Fixture Manage") {
        throw "Expected NFO anime name 'Fixture Manage', found '$($indexedVideo.animeName)'."
    }
    if ($indexedVideo.episodeNumber -ne 1) {
        throw "Expected episode number 1, found '$($indexedVideo.episodeNumber)'."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $scannedScreenshotPath

    Invoke-RelativeMouseWheel -Process $windowProcess -Notches -4
    Save-WindowScreenshot -Process $windowProcess -Path $controlsScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 1112 -Y 356
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "cleared source index" -Predicate {
        param($state)
        @($state.mediaSources).Count -eq 2 -and @($state.index).Count -eq 0 -and @($state.indexBatchUndo).Count -eq 0
    }
    if (-not (@($state.mediaSources) | Where-Object { [long]$_.id -eq $sourceId })) {
        throw "Keyboard-selected source id disappeared after clearing index."
    }
    if (-not (@($state.mediaSources) | Where-Object { [long]$_.id -eq [long]$secondSavedSource.id })) {
        throw "Second source disappeared after clearing the active source index."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $clearedScreenshotPath

    Invoke-RelativeMouseWheel -Process $windowProcess -Notches 4
    Start-Sleep -Milliseconds 400
    Save-WindowScreenshot -Process $windowProcess -Path $removeReadyScreenshotPath
    Invoke-RelativeClick -Process $windowProcess -X 1112 -Y 327
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "removed source and associated index state" -Predicate {
        param($state)
        @($state.mediaSources).Count -eq 1 -and @($state.index).Count -eq 0 -and @($state.indexBatchUndo).Count -eq 0
    }
    $remainingSource = @($state.mediaSources)[0]
    if ([long]$remainingSource.id -ne [long]$secondSavedSource.id) {
        throw "Expected the untouched second source to remain after removing source $sourceId."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $removedScreenshotPath
} finally {
    $env:MIRUPLAY_DESKTOP_STORE = $previousStoreEnv
    if (-not $KeepOpen) {
        Close-MiruPlayDesktopWindowProcessIfRunning
        Stop-MiruPlayDesktopProcessIfRunning -Process $startedProcess
    }
}

Write-Output "Run directory: $runDir"
Write-Output "Store: $storePath"
Write-Output "Scanned screenshot: $scannedScreenshotPath"
Write-Output "Controls screenshot: $controlsScreenshotPath"
Write-Output "Saved-source keyboard screenshot: $sourceSwitchScreenshotPath"
Write-Output "Cleared screenshot: $clearedScreenshotPath"
Write-Output "Remove ready screenshot: $removeReadyScreenshotPath"
Write-Output "Removed screenshot: $removedScreenshotPath"

