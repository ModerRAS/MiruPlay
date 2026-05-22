[CmdletBinding()]
param(
    [string]$AppScript = "",
    [string]$OutputRoot = "",
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
if ([string]::IsNullOrWhiteSpace($AppScript)) {
    $AppScript = Join-Path $scriptRoot "..\desktop-app\build\install\desktop-app\bin\desktop-app.bat"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $scriptRoot "..\build\desktop-keyboard-focus-ui"
}

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
if (-not ("MiruPlayKeyboardFocusSmokeWin32" -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlayKeyboardFocusSmokeWin32 {
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

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Get-MiruPlayWindowProcess {
    Get-Process |
        Where-Object {
            ($_.MainWindowTitle -like "*MiruPlay Desktop*" -or ($_.ProcessName -eq "java" -and $_.MainWindowTitle -like "*MiruPlay*")) -and
            $_.MainWindowHandle -ne 0
        } |
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
    $rect = New-Object MiruPlayKeyboardFocusSmokeWin32+RECT
    if (-not [MiruPlayKeyboardFocusSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
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

    [MiruPlayKeyboardFocusSmokeWin32]::ShowWindow($Process.MainWindowHandle, $swRestore) | Out-Null
    [MiruPlayKeyboardFocusSmokeWin32]::SetWindowPos($Process.MainWindowHandle, $hwndTopMost, 0, 0, 0, 0, $flags) | Out-Null
    [MiruPlayKeyboardFocusSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 120
    [MiruPlayKeyboardFocusSmokeWin32]::SetWindowPos($Process.MainWindowHandle, $hwndNoTopMost, 0, 0, 0, 0, $flags) | Out-Null
    [MiruPlayKeyboardFocusSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
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
    [MiruPlayKeyboardFocusSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlayKeyboardFocusSmokeWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlayKeyboardFocusSmokeWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 450
}

function Send-DesktopKey {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Key
    )

    Set-MiruPlayWindowForeground -Process $Process
    [System.Windows.Forms.SendKeys]::SendWait($Key)
    Start-Sleep -Milliseconds 350
}

function Start-MiruPlayDesktopSmokeProcess {
    param(
        [string]$LauncherPath,
        [string]$StorePath
    )

    $binDir = Split-Path -Parent $LauncherPath
    $appHome = Split-Path -Parent $binDir
    $libDir = Join-Path $appHome "lib"
    if (-not (Test-Path -LiteralPath $libDir -PathType Container)) {
        throw "Desktop app lib directory was not found at $libDir. Run :desktop-app:installDist first."
    }
    $classpath = (Get-ChildItem -LiteralPath $libDir -Filter "*.jar" -File | ForEach-Object { $_.FullName }) -join ";"
    if ([string]::IsNullOrWhiteSpace($classpath)) {
        throw "Desktop app classpath is empty under $libDir."
    }

    $javaExe = "java.exe"
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaHomeExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path -LiteralPath $javaHomeExe -PathType Leaf) {
            $javaExe = $javaHomeExe
        }
    }

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $javaExe
    $processInfo.UseShellExecute = $false
    $processInfo.WorkingDirectory = $appHome
    $processInfo.EnvironmentVariables["MIRUPLAY_DESKTOP_STORE"] = $StorePath
    $processInfo.Arguments = @(
        Quote-ProcessArgument "-Dmiruplay.desktop.store=$StorePath"
        Quote-ProcessArgument "-classpath"
        Quote-ProcessArgument $classpath
        Quote-ProcessArgument "com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt"
    ) -join " "
    return [System.Diagnostics.Process]::Start($processInfo)
}

function Quote-ProcessArgument {
    param([string]$Value)

    if ($Value -notmatch '[\s"]') {
        return $Value
    }
    return '"' + ($Value -replace '\\(?=\\*")', '$0\' -replace '"', '\"') + '"'
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
        $darkPixels = 0
        $redAccentPixels = 0
        $brightTextPixels = 0
        $sampleCount = 0
        $xStep = [Math]::Max(1, [int]($bitmap.Width / 96))
        $yStep = [Math]::Max(1, [int]($bitmap.Height / 64))
        for ($x = 0; $x -lt $bitmap.Width; $x += $xStep) {
            for ($y = 0; $y -lt $bitmap.Height; $y += $yStep) {
                $pixel = $bitmap.GetPixel($x, $y)
                [void]$colors.Add($pixel.ToArgb())
                $sampleCount++
                $r = [int]$pixel.R
                $g = [int]$pixel.G
                $b = [int]$pixel.B
                if ($r -le 55 -and $g -le 75 -and $b -le 110) {
                    $darkPixels++
                }
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
        $darkRatio = $darkPixels / [double]$sampleCount
        if ($darkRatio -lt 0.20) {
            throw "Screenshot does not look like the dark TV theme: $Path"
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
}

function Assert-ContentRegionChanged {
    param(
        [string]$BeforePath,
        [string]$AfterPath,
        [double]$MinimumChangedRatio = 0.06
    )

    $before = [System.Drawing.Bitmap]::FromFile($BeforePath)
    $after = [System.Drawing.Bitmap]::FromFile($AfterPath)
    try {
        $xStart = 610
        $yStart = 170
        $xEnd = [Math]::Min($before.Width, $after.Width) - 40
        $yEnd = [Math]::Min($before.Height, $after.Height) - 45
        $changed = 0
        $samples = 0
        for ($x = $xStart; $x -lt $xEnd; $x += 7) {
            for ($y = $yStart; $y -lt $yEnd; $y += 7) {
                $a = $before.GetPixel($x, $y)
                $b = $after.GetPixel($x, $y)
                $delta = [Math]::Abs([int]$a.R - [int]$b.R) +
                    [Math]::Abs([int]$a.G - [int]$b.G) +
                    [Math]::Abs([int]$a.B - [int]$b.B)
                if ($delta -gt 36) {
                    $changed++
                }
                $samples++
            }
        }
        $ratio = $changed / [double]$samples
        if ($ratio -lt $MinimumChangedRatio) {
            throw "Keyboard navigation did not visibly change the settings content region. Changed ratio: $([Math]::Round($ratio, 3))"
        }
    } finally {
        $before.Dispose()
        $after.Dispose()
    }
}

function Write-InitialStore {
    param([string]$Path)

    $json = @{
        nextSourceId = 1
        nextRssSubscriptionId = 3
        nextRssDownloadTaskId = 1
        mediaSources = @()
        progress = @()
        index = @()
        indexBatchUndo = @()
        cloudDriveConfig = @{
            endpointUrl = "http://127.0.0.1:19798"
            username = "desktop-smoke"
            webDavSourceId = $null
            inboxPath = "/Downloads/RSS"
            libraryPath = "/Library/Anime"
            intervalMinutes = 30
            enabled = $true
            lastRunAt = 0
            rssProxyEnabled = $false
            rssProxyHost = ""
            rssProxyPort = 1080
        }
        rssSubscriptions = @(
            @{
                id = 1
                name = "Keyboard RSS Alpha"
                url = "https://rss.example.test/alpha.xml"
                filterRegex = "Alpha"
                enabled = $true
                lastCheckedAt = 0
            },
            @{
                id = 2
                name = "Keyboard RSS Beta"
                url = "https://rss.example.test/beta.xml"
                filterRegex = "Beta"
                enabled = $true
                lastCheckedAt = 0
            }
        )
        rssProcessedItems = @()
        rssDownloadTasks = @()
        cloudDriveToken = $null
        cloudDrivePassword = $null
        bangumiAccessToken = $null
    } | ConvertTo-Json -Depth 12

    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $json, $utf8NoBom)
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
$sourceScreenshotPath = Join-Path $runDir "keyboard-settings-sources.png"
$cloudScreenshotPath = Join-Path $runDir "keyboard-settings-cloud.png"
$rssFirstScreenshotPath = Join-Path $runDir "keyboard-settings-rss-first.png"
$rssSecondScreenshotPath = Join-Path $runDir "keyboard-settings-rss-second.png"
$schedulerStartedScreenshotPath = Join-Path $runDir "keyboard-settings-scheduler-started.png"
$schedulerStoppedScreenshotPath = Join-Path $runDir "keyboard-settings-scheduler-stopped.png"
$navDetailsScreenshotPath = Join-Path $runDir "keyboard-nav-details.png"
$navPlayerScreenshotPath = Join-Path $runDir "keyboard-nav-player.png"
$navBackDetailsScreenshotPath = Join-Path $runDir "keyboard-back-details.png"
$navBackLibraryScreenshotPath = Join-Path $runDir "keyboard-back-library.png"
New-Item -ItemType Directory -Path (Split-Path -Parent $storePath) -Force | Out-Null
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
Write-InitialStore -Path $storePath

$startedProcess = $null
try {
    $startedProcess = Start-MiruPlayDesktopSmokeProcess -LauncherPath $resolvedAppScript -StorePath $storePath
    $windowProcess = Wait-MiruPlayWindow

    Invoke-RelativeClick -Process $windowProcess -X 1170 -Y 109
    Invoke-RelativeClick -Process $windowProcess -X 465 -Y 292
    Save-WindowScreenshot -Process $windowProcess -Path $sourceScreenshotPath

    Send-DesktopKey -Process $windowProcess -Key "{DOWN}"
    Send-DesktopKey -Process $windowProcess -Key "{DOWN}"
    Save-WindowScreenshot -Process $windowProcess -Path $cloudScreenshotPath
    Assert-ContentRegionChanged -BeforePath $sourceScreenshotPath -AfterPath $cloudScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 1080 -Y 765
    Save-WindowScreenshot -Process $windowProcess -Path $rssFirstScreenshotPath
    Assert-ContentRegionChanged -BeforePath $cloudScreenshotPath -AfterPath $rssFirstScreenshotPath -MinimumChangedRatio 0.01

    Send-DesktopKey -Process $windowProcess -Key "{DOWN}"
    Save-WindowScreenshot -Process $windowProcess -Path $rssSecondScreenshotPath
    Assert-ContentRegionChanged -BeforePath $rssFirstScreenshotPath -AfterPath $rssSecondScreenshotPath -MinimumChangedRatio 0.01

    Send-DesktopKey -Process $windowProcess -Key "{DOWN}"
    Send-DesktopKey -Process $windowProcess -Key "{ENTER}"
    Save-WindowScreenshot -Process $windowProcess -Path $schedulerStartedScreenshotPath
    Assert-ContentRegionChanged -BeforePath $rssSecondScreenshotPath -AfterPath $schedulerStartedScreenshotPath -MinimumChangedRatio 0.002

    Send-DesktopKey -Process $windowProcess -Key "{RIGHT}"
    Send-DesktopKey -Process $windowProcess -Key "{ENTER}"
    Save-WindowScreenshot -Process $windowProcess -Path $schedulerStoppedScreenshotPath
    Assert-ContentRegionChanged -BeforePath $schedulerStartedScreenshotPath -AfterPath $schedulerStoppedScreenshotPath -MinimumChangedRatio 0.002

    Invoke-RelativeClick -Process $windowProcess -X 170 -Y 286
    Save-WindowScreenshot -Process $windowProcess -Path $navDetailsScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 170 -Y 286
    Send-DesktopKey -Process $windowProcess -Key "{DOWN}"
    Save-WindowScreenshot -Process $windowProcess -Path $navPlayerScreenshotPath
    Assert-ContentRegionChanged -BeforePath $navDetailsScreenshotPath -AfterPath $navPlayerScreenshotPath

    Send-DesktopKey -Process $windowProcess -Key "{ESC}"
    Save-WindowScreenshot -Process $windowProcess -Path $navBackDetailsScreenshotPath
    Assert-ContentRegionChanged -BeforePath $navPlayerScreenshotPath -AfterPath $navBackDetailsScreenshotPath

    Send-DesktopKey -Process $windowProcess -Key "{ESC}"
    Save-WindowScreenshot -Process $windowProcess -Path $navBackLibraryScreenshotPath
    Assert-ContentRegionChanged -BeforePath $navBackDetailsScreenshotPath -AfterPath $navBackLibraryScreenshotPath
} finally {
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
Write-Output "Sources screenshot: $sourceScreenshotPath"
Write-Output "CloudDrive screenshot: $cloudScreenshotPath"
Write-Output "RSS first subscription screenshot: $rssFirstScreenshotPath"
Write-Output "RSS second subscription screenshot: $rssSecondScreenshotPath"
Write-Output "Scheduler started screenshot: $schedulerStartedScreenshotPath"
Write-Output "Scheduler stopped screenshot: $schedulerStoppedScreenshotPath"
Write-Output "Navigation details screenshot: $navDetailsScreenshotPath"
Write-Output "Navigation player screenshot: $navPlayerScreenshotPath"
Write-Output "Back to details screenshot: $navBackDetailsScreenshotPath"
Write-Output "Back to library screenshot: $navBackLibraryScreenshotPath"
