[CmdletBinding()]
param(
    [string]$AppScript = (Join-Path $PSScriptRoot "..\desktop-app\build\install\desktop-app\bin\desktop-app.bat"),
    [string]$OutputRoot = (Join-Path $PSScriptRoot "..\build\desktop-mpv-launch-ui"),
    [string]$SamplePath = "",
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlayMpvLaunchSmokeWin32 {
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

    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
"@

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
    $deadline = (Get-Date).AddSeconds(75)
    do {
        $process = Get-MiruPlayWindowProcess
        if ($process) {
            return $process
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)

    throw "MiruPlay Desktop window did not appear within 75 seconds."
}

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlayMpvLaunchSmokeWin32+RECT
    if (-not [MiruPlayMpvLaunchSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
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
    [MiruPlayMpvLaunchSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
    [MiruPlayMpvLaunchSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlayMpvLaunchSmokeWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlayMpvLaunchSmokeWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds $DelayMilliseconds
}

function Set-FocusedText {
    param([string]$Text)
    Set-Clipboard -Value $Text
    [System.Windows.Forms.SendKeys]::SendWait("^a")
    Start-Sleep -Milliseconds 80
    [System.Windows.Forms.SendKeys]::SendWait("^v")
    Start-Sleep -Milliseconds 300
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
        Start-Sleep -Milliseconds 250
    }

    throw "Unable to set $Description to '$Text'."
}

function Assert-ScreenshotHasContent {
    param(
        [string]$Path,
        [switch]$RequireRedAccent
    )
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
        if ($RequireRedAccent -and $redAccentPixels -lt 8) {
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
    Assert-ScreenshotHasContent -Path $Path -RequireRedAccent
}

function Save-WindowScreenshotWithoutRedRequirement {
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

function New-Y4mSmokeClip {
    param(
        [string]$Path,
        [int]$ClipWidth = 160,
        [int]$ClipHeight = 90,
        [int]$FrameCount = 1440
    )

    $directory = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    $lumaBytes = $ClipWidth * $ClipHeight
    $chromaBytes = [int]($lumaBytes / 4)
    $frameHeader = [System.Text.Encoding]::ASCII.GetBytes("FRAME`n")
    $file = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $header = [System.Text.Encoding]::ASCII.GetBytes("YUV4MPEG2 W$ClipWidth H$ClipHeight F24:1 Ip A0:0 C420jpeg`n")
        $file.Write($header, 0, $header.Length)
        for ($frame = 0; $frame -lt $FrameCount; $frame++) {
            $luma = [byte[]]::new($lumaBytes)
            $u = [byte[]]::new($chromaBytes)
            $v = [byte[]]::new($chromaBytes)
            for ($i = 0; $i -lt $luma.Length; $i++) {
                $x = $i % $ClipWidth
                $y = [int]($i / $ClipWidth)
                $luma[$i] = [byte](32 + (($x + $y + ($frame * 5)) % 180))
            }
            for ($i = 0; $i -lt $chromaBytes; $i++) {
                $u[$i] = [byte](96 + (($i + ($frame * 3)) % 48))
                $v[$i] = [byte](144 - (($i + ($frame * 2)) % 48))
            }
            $file.Write($frameHeader, 0, $frameHeader.Length)
            $file.Write($luma, 0, $luma.Length)
            $file.Write($u, 0, $u.Length)
            $file.Write($v, 0, $v.Length)
        }
    } finally {
        $file.Dispose()
    }
}

function Wait-MpvChildProcess {
    param(
        [int]$ParentProcessId,
        [string]$ExpectedSamplePath,
        [int]$TimeoutSeconds = 20
    )

    $normalizedSample = $ExpectedSamplePath.ToLowerInvariant()
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $candidates = @(Get-CimInstance Win32_Process -Filter "Name = 'mpv.exe'" -ErrorAction SilentlyContinue |
            Where-Object {
                $_.ParentProcessId -eq $ParentProcessId -and
                    $_.CommandLine -and
                    $_.CommandLine.ToLowerInvariant().Contains($normalizedSample)
            })
        if ($candidates.Count -gt 0) {
            return $candidates[0]
        }
        Start-Sleep -Milliseconds 350
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for mpv.exe child process for sample $ExpectedSamplePath."
}

function Minimize-ProcessWindow {
    param([int]$ProcessId)

    $deadline = (Get-Date).AddSeconds(5)
    do {
        $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if ($process -and $process.MainWindowHandle -ne 0) {
            [MiruPlayMpvLaunchSmokeWin32]::ShowWindow($process.MainWindowHandle, 6) | Out-Null
            return
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
}

function Wait-MpvProcessGone {
    param(
        [int]$ProcessId,
        [int]$TimeoutSeconds = 15
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
            return
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)

    throw "mpv.exe process $ProcessId was still running after Stop."
}

function Invoke-StopAndWait {
    param(
        [System.Diagnostics.Process]$WindowProcess,
        [int]$MpvProcessId
    )

    $stopPoints = @(
        @{ X = 782; Y = 278 },
        @{ X = 785; Y = 282 },
        @{ X = 776; Y = 276 }
    )
    foreach ($point in $stopPoints) {
        Invoke-RelativeClick -Process $WindowProcess -X $point.X -Y $point.Y -DelayMilliseconds 1200
        $deadline = (Get-Date).AddSeconds(5)
        do {
            if (-not (Get-Process -Id $MpvProcessId -ErrorAction SilentlyContinue)) {
                return
            }
            Start-Sleep -Milliseconds 300
        } while ((Get-Date) -lt $deadline)
    }

    throw "mpv.exe process $MpvProcessId was still running after GUI Stop."
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
$storePath = Join-Path $runDir "store\desktop-store.json"
$sample = if ($SamplePath.Trim()) {
    Resolve-FullPath $SamplePath
} else {
    Join-Path $runDir "media\miruplay-mpv-smoke.y4m"
}
$preLaunchScreenshotPath = Join-Path $runDir "mpv-launch-ready.png"
$launchedScreenshotPath = Join-Path $runDir "mpv-launched.png"
$controlledScreenshotPath = Join-Path $runDir "mpv-controls-used.png"
$stoppedScreenshotPath = Join-Path $runDir "mpv-stopped.png"
New-Item -ItemType Directory -Path (Split-Path -Parent $storePath) -Force | Out-Null

if ($SamplePath.Trim()) {
    if (-not (Test-Path -LiteralPath $sample -PathType Leaf)) {
        throw "SamplePath does not exist or is not a file: $sample"
    }
} else {
    New-Y4mSmokeClip -Path $sample
}

$previousClipboard = Get-Clipboard -Raw -ErrorAction SilentlyContinue
$previousStoreEnv = $env:MIRUPLAY_DESKTOP_STORE
$env:MIRUPLAY_DESKTOP_STORE = $storePath
$startedProcess = $null
$mpvProcess = $null
try {
    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayWindow

    Invoke-RelativeClick -Process $windowProcess -X 1028 -Y 110
    Set-TextByRelativeClick -Process $windowProcess -X 455 -Y 614 -Text $sample -Description "player media path"
    Invoke-RelativeClick -Process $windowProcess -X 438 -Y 756
    Save-WindowScreenshot -Process $windowProcess -Path $preLaunchScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 596 -Y 278 -DelayMilliseconds 900
    $mpvProcess = Wait-MpvChildProcess -ParentProcessId $windowProcess.Id -ExpectedSamplePath $sample
    Wait-StoreState -Path $storePath -Description "initial playback progress record" -Predicate {
        param($state)
        $records = @($state.progress | Where-Object { $_.episodeId -eq $sample })
        $records.Count -eq 1 -and $records[0].playCount -ge 1
    } | Out-Null
    Minimize-ProcessWindow -ProcessId $mpvProcess.ProcessId
    Start-Sleep -Milliseconds 900
    Save-WindowScreenshotWithoutRedRequirement -Process $windowProcess -Path $launchedScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 596 -Y 278 -DelayMilliseconds 500
    Invoke-RelativeClick -Process $windowProcess -X 498 -Y 278 -DelayMilliseconds 500
    Invoke-RelativeClick -Process $windowProcess -X 696 -Y 278 -DelayMilliseconds 500
    Save-WindowScreenshotWithoutRedRequirement -Process $windowProcess -Path $controlledScreenshotPath
    Invoke-StopAndWait -WindowProcess $windowProcess -MpvProcessId $mpvProcess.ProcessId
    $finalState = Wait-StoreState -Path $storePath -Description "stopped playback progress update" -Predicate {
        param($state)
        $records = @($state.progress | Where-Object { $_.episodeId -eq $sample })
        $records.Count -eq 1 -and $records[0].playCount -ge 1 -and $records[0].positionMs -ge 20000
    }
    $finalProgress = @($finalState.progress | Where-Object { $_.episodeId -eq $sample })[0]
    Save-WindowScreenshotWithoutRedRequirement -Process $windowProcess -Path $stoppedScreenshotPath
} finally {
    if ($mpvProcess) {
        Stop-Process -Id $mpvProcess.ProcessId -Force -ErrorAction SilentlyContinue
    }
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
Write-Output "Sample: $sample"
Write-Output "mpv pid: $($mpvProcess.ProcessId)"
if ($finalProgress) {
    Write-Output "Saved position ms: $($finalProgress.positionMs)"
    Write-Output "Play count: $($finalProgress.playCount)"
}
Write-Output "Pre-launch screenshot: $preLaunchScreenshotPath"
Write-Output "Launched screenshot: $launchedScreenshotPath"
Write-Output "Controls screenshot: $controlledScreenshotPath"
Write-Output "Stopped screenshot: $stoppedScreenshotPath"
