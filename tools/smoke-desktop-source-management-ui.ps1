[CmdletBinding()]
param(
    [string]$AppScript = (Join-Path $PSScriptRoot "..\desktop-app\build\install\desktop-app\bin\desktop-app.bat"),
    [string]$OutputRoot = (Join-Path $PSScriptRoot "..\build\desktop-source-management-ui"),
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"

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
    public static extern bool SetCursorPos(int X, int Y);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, int data, UIntPtr extraInfo);
}
"@
}

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Get-MiruPlayWindowProcess {
    Get-Process |
        Where-Object { $_.MainWindowTitle -like "*MiruPlay Desktop*" -and $_.MainWindowHandle -ne 0 } |
        Select-Object -First 1
}

function Wait-MiruPlayWindow {
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $process = Get-MiruPlayWindowProcess
        if ($process) {
            return $process
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)

    throw "MiruPlay Desktop window did not appear within 30 seconds."
}

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlaySourceManagementSmokeWin32+RECT
    if (-not [MiruPlaySourceManagementSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
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
    [MiruPlaySourceManagementSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
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
    [MiruPlaySourceManagementSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    [MiruPlaySourceManagementSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    Start-Sleep -Milliseconds 150
    $direction = if ($Notches -lt 0) { -1 } else { 1 }
    for ($i = 0; $i -lt [Math]::Abs($Notches); $i++) {
        [MiruPlaySourceManagementSmokeWin32]::mouse_event(0x0800, 0, 0, $direction * $DeltaPerNotch, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
    }
    Start-Sleep -Milliseconds 700
}

function Set-FocusedText {
    param([string]$Text)
    Set-Clipboard -Value $Text
    [System.Windows.Forms.SendKeys]::SendWait("^a")
    Start-Sleep -Milliseconds 80
    [System.Windows.Forms.SendKeys]::SendWait("^v")
    Start-Sleep -Milliseconds 350
}

function Get-FocusedText {
    $before = Get-Clipboard -Raw -ErrorAction SilentlyContinue
    try {
        Set-Clipboard -Value "__MIRUPLAY_EMPTY_SELECTION__"
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
            Set-Clipboard -Value $before
        }
    }
}

function Set-TextByRelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y,
        [string]$Text,
        [string]$Description,
        [int]$Attempts = 3
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        Invoke-RelativeClick -Process $Process -X $X -Y $Y
        Set-FocusedText -Text $Text
        $actual = Get-FocusedText
        if ($actual -eq $Text) {
            return
        }
        Start-Sleep -Milliseconds 300
    }

    throw "Unable to set $Description to '$Text'."
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

$resolvedAppScript = Resolve-FullPath $AppScript
$resolvedOutputRoot = Resolve-FullPath $OutputRoot
if (-not (Test-Path -LiteralPath $resolvedAppScript)) {
    throw "Desktop app launcher was not found at $resolvedAppScript. Run :desktop-app:installDist first."
}
if (Get-MiruPlayWindowProcess) {
    throw "A MiruPlay Desktop window is already open. Close it before running this isolated smoke test."
}

$runName = "run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$runDir = Join-Path $resolvedOutputRoot $runName
$fixtureDir = Join-Path $runDir "media\Very Long Source Name For Focus And Path Preview Validation\Season 01\Manage"
$storePath = Join-Path $runDir "store\desktop-store.json"
$scannedScreenshotPath = Join-Path $runDir "source-management-scanned.png"
$controlsScreenshotPath = Join-Path $runDir "source-management-controls.png"
$clearedScreenshotPath = Join-Path $runDir "source-management-cleared.png"
$removeReadyScreenshotPath = Join-Path $runDir "source-management-remove-ready.png"
$removedScreenshotPath = Join-Path $runDir "source-management-removed.png"
New-Item -ItemType Directory -Path (Split-Path -Parent $storePath) -Force | Out-Null
New-Item -ItemType Directory -Path $fixtureDir -Force | Out-Null

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

$resolvedLibraryRoot = Split-Path -Parent $fixtureDir
$previousClipboard = Get-Clipboard -Raw -ErrorAction SilentlyContinue
$previousStoreEnv = $env:MIRUPLAY_DESKTOP_STORE
$env:MIRUPLAY_DESKTOP_STORE = $storePath
$startedProcess = $null
try {
    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayWindow

    Set-TextByRelativeClick -Process $windowProcess -X 240 -Y 268 -Text $resolvedLibraryRoot -Description "local library root"
    Invoke-RelativeClick -Process $windowProcess -X 500 -Y 337
    $state = Wait-StoreState -Path $storePath -Description "saved local source" -Predicate {
        param($state)
        @($state.mediaSources).Count -eq 1
    }
    $savedSource = @($state.mediaSources)[0]
    $sourceId = [long]$savedSource.id
    if ($savedSource.name -ne "Season 01") {
        throw "Expected saved source name to reflect the long path leaf 'Season 01', found '$($savedSource.name)'."
    }
    if ($savedSource.connectionInfo.path -ne $resolvedLibraryRoot) {
        throw "Saved source path did not preserve the long local root."
    }

    Invoke-RelativeClick -Process $windowProcess -X 664 -Y 337
    $state = Wait-StoreState -Path $storePath -Description "scanned local index entry" -Predicate {
        param($state)
        @($state.index | Where-Object { -not $_.isDirectory }).Count -eq 1
    } -TimeoutSeconds 90
    $indexedVideo = @($state.index | Where-Object { -not $_.isDirectory })[0]
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
    $state = Wait-StoreState -Path $storePath -Description "cleared source index" -Predicate {
        param($state)
        @($state.mediaSources).Count -eq 1 -and @($state.index).Count -eq 0 -and @($state.indexBatchUndo).Count -eq 0
    }
    if ([long]@($state.mediaSources)[0].id -ne $sourceId) {
        throw "Source id changed after clearing index."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $clearedScreenshotPath

    Invoke-RelativeMouseWheel -Process $windowProcess -Notches 4
    Start-Sleep -Milliseconds 400
    Save-WindowScreenshot -Process $windowProcess -Path $removeReadyScreenshotPath
    Invoke-RelativeClick -Process $windowProcess -X 1112 -Y 327
    $state = Wait-StoreState -Path $storePath -Description "removed source and associated index state" -Predicate {
        param($state)
        @($state.mediaSources).Count -eq 0 -and @($state.index).Count -eq 0 -and @($state.indexBatchUndo).Count -eq 0
    }
    Save-WindowScreenshot -Process $windowProcess -Path $removedScreenshotPath
} finally {
    if ($null -ne $previousClipboard) {
        Set-Clipboard -Value $previousClipboard
    }
    $env:MIRUPLAY_DESKTOP_STORE = $previousStoreEnv
    if (-not $KeepOpen) {
        $windowProcess = Get-MiruPlayWindowProcess
        if ($windowProcess) {
            $windowProcess.CloseMainWindow() | Out-Null
            Start-Sleep -Milliseconds 700
            if (-not $windowProcess.HasExited) {
                Stop-Process -Id $windowProcess.Id -Force
            }
        }
        if ($startedProcess -and -not $startedProcess.HasExited) {
            Stop-Process -Id $startedProcess.Id -Force
        }
    }
}

Write-Output "Run directory: $runDir"
Write-Output "Store: $storePath"
Write-Output "Scanned screenshot: $scannedScreenshotPath"
Write-Output "Controls screenshot: $controlsScreenshotPath"
Write-Output "Cleared screenshot: $clearedScreenshotPath"
Write-Output "Remove ready screenshot: $removeReadyScreenshotPath"
Write-Output "Removed screenshot: $removedScreenshotPath"
