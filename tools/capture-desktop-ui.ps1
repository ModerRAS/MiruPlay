[CmdletBinding()]
param(
    [string]$AppScript = "",
    [string]$OutputDir = "",
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
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $scriptRoot "..\build\desktop-ui-qa"
}

Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlayWin32 {
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

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlayWin32+RECT
    if (-not [MiruPlayWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
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
    [MiruPlayWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
    [MiruPlayWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlayWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlayWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 450
}

function Assert-ScreenshotHasVisualQuality {
    param([string]$Path)
    Assert-DesktopSmokeScreenshotQuality -Path $Path -RequireDarkTheme $true
}

function Assert-CapturesAreDistinct {
    param([string[]]$Paths)
    $hashes = @($Paths | ForEach-Object { (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash })
    $uniqueCount = @($hashes | Select-Object -Unique).Count
    if ($uniqueCount -ne $Paths.Count) {
        throw "Screenshot captures are not distinct; navigation may not have changed sections."
    }
}

function Save-WindowScreenshot {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Name,
        [string]$Directory
    )

    $rect = Get-WindowRect -Process $Process
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    if ($width -lt 1100 -or $height -lt 700) {
        throw "Window is smaller than expected for TV-style QA: ${width}x$height"
    }

    $path = Join-Path $Directory "$Name.png"
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)
        $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }

    Assert-ScreenshotHasVisualQuality -Path $path
    return $path
}

$resolvedAppScript = Resolve-DesktopSmokeFullPath $AppScript
$resolvedOutputDir = Resolve-DesktopSmokeFullPath $OutputDir
if (-not (Test-Path -LiteralPath $resolvedAppScript)) {
    throw "Desktop app launcher was not found at $resolvedAppScript. Run :desktop-app:installDist first."
}
if (-not (Test-Path -LiteralPath $resolvedOutputDir)) {
    New-Item -ItemType Directory -Path $resolvedOutputDir -Force | Out-Null
}

Stop-MiruPlayWindowProcesses
$startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
$windowProcess = Wait-MiruPlayDesktopWindowProcess

$captures = @()
$captures += Save-WindowScreenshot -Process $windowProcess -Name "library" -Directory $resolvedOutputDir

Invoke-RelativeClick -Process $windowProcess -X 1165 -Y 110
$captures += Save-WindowScreenshot -Process $windowProcess -Name "settings" -Directory $resolvedOutputDir

Invoke-RelativeClick -Process $windowProcess -X 120 -Y 290
$captures += Save-WindowScreenshot -Process $windowProcess -Name "details" -Directory $resolvedOutputDir

Invoke-RelativeClick -Process $windowProcess -X 120 -Y 370
$captures += Save-WindowScreenshot -Process $windowProcess -Name "player" -Directory $resolvedOutputDir

Invoke-RelativeClick -Process $windowProcess -X 130 -Y 120
Invoke-RelativeClick -Process $windowProcess -X 120 -Y 450
Invoke-RelativeClick -Process $windowProcess -X 410 -Y 450
$captures += Save-WindowScreenshot -Process $windowProcess -Name "settings-cloud" -Directory $resolvedOutputDir

Assert-CapturesAreDistinct -Paths $captures

if (-not $KeepOpen) {
    $windowProcess.CloseMainWindow() | Out-Null
    Start-Sleep -Milliseconds 700
    if (-not $windowProcess.HasExited) {
        Stop-Process -Id $windowProcess.Id -Force
    }
    if ($startedProcess -and -not $startedProcess.HasExited) {
        Stop-Process -Id $startedProcess.Id -Force
    }
}

$captures | ForEach-Object { Write-Output $_ }

