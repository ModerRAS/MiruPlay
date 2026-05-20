[CmdletBinding()]
param(
    [string]$AppScript = (Join-Path $PSScriptRoot "..\desktop-app\build\install\desktop-app\bin\desktop-app.bat"),
    [string]$OutputRoot = (Join-Path $PSScriptRoot "..\build\desktop-smb-source-ui"),
    [string]$ShareTestPath = "\\smb.ynz.local\share\临时文件\测试",
    [string]$SmbBaseUrl = "smb://smb.ynz.local/share/临时文件/测试",
    [string]$SmbUsername = $(if ($env:MIRUPLAY_SMB_SMOKE_USERNAME) { $env:MIRUPLAY_SMB_SMOKE_USERNAME } else { "ynsz" }),
    [string]$SmbPassword = $(if ($env:MIRUPLAY_SMB_SMOKE_PASSWORD) { $env:MIRUPLAY_SMB_SMOKE_PASSWORD } else { "ynsz" }),
    [string]$SmbDomain = $env:MIRUPLAY_SMB_SMOKE_DOMAIN,
    [switch]$KeepFixture,
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
if (-not ("MiruPlaySmbSourceSmokeWin32" -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlaySmbSourceSmokeWin32 {
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
    $rect = New-Object MiruPlaySmbSourceSmokeWin32+RECT
    if (-not [MiruPlaySmbSourceSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
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
    [MiruPlaySmbSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
    [MiruPlaySmbSourceSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlaySmbSourceSmokeWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlaySmbSourceSmokeWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 350
}

function Invoke-RelativeMouseWheel {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X = 650,
        [int]$Y = 500,
        [int]$Notches,
        [int]$DeltaPerNotch = 420
    )

    $rect = Get-WindowRect -Process $Process
    [MiruPlaySmbSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    [MiruPlaySmbSourceSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    Start-Sleep -Milliseconds 150
    $direction = if ($Notches -lt 0) { -1 } else { 1 }
    for ($i = 0; $i -lt [Math]::Abs($Notches); $i++) {
        [MiruPlaySmbSourceSmokeWin32]::mouse_event(0x0800, 0, 0, $direction * $DeltaPerNotch, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
    }
    Start-Sleep -Milliseconds 500
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
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        return $null
    }
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

function Redact-StoreSecrets {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $state = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    foreach ($source in @($state.mediaSources)) {
        if (-not $source.connectionInfo) {
            continue
        }
        foreach ($secretName in @("username", "password", "domain")) {
            if ($source.connectionInfo.PSObject.Properties.Name -contains $secretName) {
                $source.connectionInfo.$secretName = "__REDACTED__"
            }
        }
    }
    $state | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $Path -Encoding UTF8
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

function Join-SmbUrl {
    param(
        [string]$BaseUrl,
        [string]$ChildName
    )
    return "$($BaseUrl.TrimEnd('/'))/$ChildName"
}

function New-SmbFixture {
    param(
        [string]$Root,
        [string]$RunName
    )

    $fixtureRoot = Join-Path $Root "MiruPlaySmoke-$RunName"
    $showName = "Fixture SMB"
    $showDir = Join-Path $fixtureRoot $showName
    New-Item -ItemType Directory -Path $showDir -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $showDir "$showName - S01E02.mkv") -Value "fixture smb video bytes" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $showDir "$showName - S01E02.nfo") -Encoding UTF8 -Value @"
<episodedetails>
  <showtitle>$showName</showtitle>
  <title>SMB Fixture Episode</title>
  <season>1</season>
  <episode>2</episode>
  <plot>Fixture SMB plot for desktop GUI smoke.</plot>
</episodedetails>
"@
    Set-Content -LiteralPath (Join-Path $showDir "tvshow.nfo") -Encoding UTF8 -Value @"
<tvshow>
  <title>$showName</title>
  <originaltitle>$showName Original</originaltitle>
</tvshow>
"@
    return $fixtureRoot
}

$resolvedAppScript = Resolve-FullPath $AppScript
$resolvedOutputRoot = Resolve-FullPath $OutputRoot
if (-not (Test-Path -LiteralPath $resolvedAppScript)) {
    throw "Desktop app launcher was not found at $resolvedAppScript. Run :desktop-app:installDist first."
}
if (Get-MiruPlayWindowProcess) {
    throw "A MiruPlay Desktop window is already open. Close it before running this isolated smoke test."
}
if ($ShareTestPath.Trim() -notmatch "\\临时文件\\测试$") {
    throw "Refusing to write SMB smoke fixture outside the approved 临时文件\\测试 directory: $ShareTestPath"
}
if (-not (Test-Path -LiteralPath $ShareTestPath -PathType Container)) {
    throw "SMB smoke test directory does not exist or is not accessible: $ShareTestPath"
}

$runName = "run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$runDir = Join-Path $resolvedOutputRoot $runName
$storePath = Join-Path $runDir "store\desktop-store.json"
$openScreenshotPath = Join-Path $runDir "smb-source-opened.png"
$scanScreenshotPath = Join-Path $runDir "smb-source-scanned.png"
$posterScreenshotPath = Join-Path $runDir "smb-source-poster-wall.png"
$detailsScreenshotPath = Join-Path $runDir "smb-source-details.png"
$playerScreenshotPath = Join-Path $runDir "smb-source-player.png"
New-Item -ItemType Directory -Path (Split-Path -Parent $storePath) -Force | Out-Null

$fixtureRoot = New-SmbFixture -Root $ShareTestPath -RunName $runName
$fixtureName = Split-Path -Leaf $fixtureRoot
$smbFixtureUrl = Join-SmbUrl -BaseUrl $SmbBaseUrl -ChildName $fixtureName
$expectedVideoPath = Join-SmbUrl -BaseUrl (Join-SmbUrl -BaseUrl $smbFixtureUrl -ChildName "Fixture SMB") -ChildName "Fixture SMB - S01E02.mkv"
$previousClipboard = Get-Clipboard -Raw -ErrorAction SilentlyContinue
$previousStoreEnv = $env:MIRUPLAY_DESKTOP_STORE
$env:MIRUPLAY_DESKTOP_STORE = $storePath
$startedProcess = $null

try {
    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayWindow

    Invoke-RelativeMouseWheel -Process $windowProcess -Notches -8
    Set-TextByRelativeClick -Process $windowProcess -X 280 -Y 536 -Text $smbFixtureUrl -Description "SMB URL"
    if ($SmbDomain.Trim()) {
        Set-TextByRelativeClick -Process $windowProcess -X 132 -Y 614 -Text $SmbDomain -Description "SMB domain"
    }
    if ($SmbUsername.Trim()) {
        Set-TextByRelativeClick -Process $windowProcess -X 292 -Y 614 -Text $SmbUsername -Description "SMB username"
    }
    if ($SmbPassword.Trim()) {
        Set-TextByRelativeClick -Process $windowProcess -X 452 -Y 614 -Text $SmbPassword -Description "SMB password"
    }
    Invoke-RelativeClick -Process $windowProcess -X 139 -Y 681

    $state = Wait-StoreState -Path $storePath -Description "saved SMB source" -Predicate {
        param($state)
        @($state.mediaSources | Where-Object { $_.type -eq "SMB" }).Count -eq 1
    }
    Start-Sleep -Milliseconds 1200
    Save-WindowScreenshot -Process $windowProcess -Path $openScreenshotPath

    $source = @($state.mediaSources | Where-Object { $_.type -eq "SMB" })[0]
    if ($source.connectionInfo.url.TrimEnd("/") -ne $smbFixtureUrl.TrimEnd("/")) {
        throw "Stored SMB URL does not match fixture URL: $($source.connectionInfo.url)"
    }
    if ($SmbUsername.Trim() -and $source.connectionInfo.username -ne $SmbUsername) {
        throw "Stored SMB username does not match fixture username: $($source.connectionInfo.username)"
    }
    if ($SmbPassword.Trim() -and $source.connectionInfo.password -ne $SmbPassword) {
        throw "Stored SMB password does not match fixture password."
    }

    Invoke-RelativeClick -Process $windowProcess -X 295 -Y 681
    $state = Wait-StoreState -Path $storePath -Description "scanned SMB index entry" -Predicate {
        param($state)
        @($state.index | Where-Object { -not $_.isDirectory }).Count -ge 1
    } -TimeoutSeconds 90

    $indexedVideos = @($state.index | Where-Object { -not $_.isDirectory })
    if ($indexedVideos.Count -ne 1) {
        throw "Expected exactly one SMB fixture video, found $($indexedVideos.Count)."
    }
    $selectedVideo = $indexedVideos[0]
    if ($selectedVideo.path -ne $expectedVideoPath) {
        throw "Expected indexed SMB path '$expectedVideoPath', found '$($selectedVideo.path)'."
    }
    if ($selectedVideo.animeName -ne "Fixture SMB") {
        throw "Expected NFO anime name 'Fixture SMB', found '$($selectedVideo.animeName)'."
    }
    if ($selectedVideo.episodeNumber -ne 2) {
        throw "Expected episode number 2, found '$($selectedVideo.episodeNumber)'."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $scanScreenshotPath

    Invoke-RelativeMouseWheel -Process $windowProcess -Notches 10
    Start-Sleep -Milliseconds 700
    Save-WindowScreenshot -Process $windowProcess -Path $posterScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 130 -Y 360
    Start-Sleep -Milliseconds 700
    Save-WindowScreenshot -Process $windowProcess -Path $detailsScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 674 -Y 466
    Start-Sleep -Milliseconds 500
    Invoke-RelativeClick -Process $windowProcess -X 520 -Y 615
    $selectedMediaPath = Get-FocusedText
    if ($selectedMediaPath -ne $expectedVideoPath) {
        throw "Player media path did not match selected SMB poster. Expected '$expectedVideoPath', found '$selectedMediaPath'."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $playerScreenshotPath
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
    Redact-StoreSecrets -Path $storePath
    if (-not $KeepFixture -and $fixtureRoot -and (Test-Path -LiteralPath $fixtureRoot)) {
        $leaf = Split-Path -Leaf $fixtureRoot
        if ($leaf -like "MiruPlaySmoke-run-*") {
            Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
        }
    }
}

Write-Output "Run directory: $runDir"
Write-Output "Store: $storePath"
Write-Output "Fixture root: $fixtureRoot"
Write-Output "SMB fixture URL: $smbFixtureUrl"
Write-Output "Open screenshot: $openScreenshotPath"
Write-Output "Scan screenshot: $scanScreenshotPath"
Write-Output "Poster wall screenshot: $posterScreenshotPath"
Write-Output "Details screenshot: $detailsScreenshotPath"
Write-Output "Player screenshot: $playerScreenshotPath"
