$script:DesktopSmokeDefaultPollMilliseconds = 300

if (-not ("MiruPlayDesktopSmokeCaptureWin32" -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlayDesktopSmokeCaptureWin32 {
    [DllImport("user32.dll")]
    public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, uint nFlags);
}
"@
}

function Resolve-DesktopSmokeFullPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Read-DesktopSmokeStoreState {
    param(
        [string]$Path,
        [switch]$IgnoreParseErrors
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    try {
        return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        if ($IgnoreParseErrors) {
            return $null
        }
        throw
    }
}

function Wait-DesktopSmokeStoreState {
    param(
        [string]$Path,
        [scriptblock]$Predicate,
        [string]$Description,
        [int]$TimeoutSeconds = 20,
        [int]$PollMilliseconds = $script:DesktopSmokeDefaultPollMilliseconds,
        [switch]$IgnoreParseErrors
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = Read-DesktopSmokeStoreState -Path $Path -IgnoreParseErrors:$IgnoreParseErrors
        if ($state -and (& $Predicate $state)) {
            return $state
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Description in $Path."
}

function Test-DesktopSmokeBitmapEncodedSize {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [int]$MinimumFileSizeBytes = 20000
    )

    $stream = New-Object System.IO.MemoryStream
    try {
        $Bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
        return $stream.Length -ge $MinimumFileSizeBytes
    } finally {
        $stream.Dispose()
    }
}

function Save-DesktopSmokeWindowBitmap {
    param(
        [System.Diagnostics.Process]$Process,
        [object]$Rect,
        [string]$Path
    )

    $width = $Rect.Right - $Rect.Left
    $height = $Rect.Bottom - $Rect.Top
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $hdc = $graphics.GetHdc()
        try {
            $printed = [MiruPlayDesktopSmokeCaptureWin32]::PrintWindow($Process.MainWindowHandle, $hdc, 2)
        } finally {
            $graphics.ReleaseHdc($hdc)
        }
        if ($printed -and -not (Test-DesktopSmokeBitmapEncodedSize -Bitmap $bitmap)) {
            $printed = $false
        }
        if (-not $printed) {
            $graphics.CopyFromScreen($Rect.Left, $Rect.Top, 0, 0, $bitmap.Size)
        }
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Assert-DesktopSmokeScreenshotQuality {
    param(
        [string]$Path,
        [bool]$RequireRedAccent = $true,
        [bool]$RequireDarkTheme = $false,
        [double]$MinimumDarkThemeRatio = 0.20,
        [int]$MinimumFileSizeBytes = 20000,
        [int]$MinimumColorCount = 28,
        [int]$MinimumRedAccentPixels = 8,
        [int]$MinimumBrightTextPixels = 8
    )

    $file = Get-Item -LiteralPath $Path
    if ($file.Length -lt $MinimumFileSizeBytes) {
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

        if ($colors.Count -lt $MinimumColorCount) {
            throw "Screenshot appears blank or nearly blank: $Path"
        }
        if ($RequireDarkTheme) {
            $darkRatio = $darkPixels / [double]$sampleCount
            if ($darkRatio -lt $MinimumDarkThemeRatio) {
                throw "Screenshot does not look like the dark TV theme: $Path (dark sample ratio $([Math]::Round($darkRatio, 3)))"
            }
        }
        if ($RequireRedAccent -and $redAccentPixels -lt $MinimumRedAccentPixels) {
            throw "Screenshot is missing the expected MiruPlay red accent: $Path"
        }
        if ($brightTextPixels -lt $MinimumBrightTextPixels) {
            throw "Screenshot has too little readable light text: $Path"
        }
    } finally {
        $bitmap.Dispose()
    }
}
