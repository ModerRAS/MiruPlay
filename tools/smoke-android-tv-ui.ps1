[CmdletBinding()]
param(
    [string]$DeviceId = "10.137.32.118:5555",
    [string]$ApkPath = "",
    [string]$OutputRoot = "",
    [switch]$SkipInstall,
    [switch]$KeepAppData
)

$ErrorActionPreference = "Stop"

$PackageName = "com.miruplay.tv"
$ActivityName = "com.miruplay.tv/.MainActivity"
$ScriptDirectory = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
if (-not $ApkPath) {
    $ApkPath = Join-Path $ScriptDirectory "..\app\build\outputs\apk\debug\app-debug.apk"
}
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $ScriptDirectory "..\build\android-tv-qa"
}

function New-UnicodeText {
    param([int[]]$CodePoints)
    return -join ($CodePoints | ForEach-Object { [char]$_ })
}

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Invoke-Adb {
    param([string[]]$Arguments)
    & adb -s $DeviceId @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Invoke-AdbBestEffort {
    param([string[]]$Arguments)
    try {
        & adb -s $DeviceId @Arguments *> $null
    } catch {
    }
}

function Invoke-Ffmpeg {
    param([string[]]$Arguments)
    & ffmpeg @Arguments
    return $LASTEXITCODE
}

function New-SampleVideo {
    param([string]$Path)
    $common = @(
        "-hide_banner",
        "-loglevel", "error",
        "-y",
        "-f", "lavfi",
        "-i", "testsrc2=size=640x360:rate=24:duration=4",
        "-pix_fmt", "yuv420p",
        "-movflags", "+faststart",
        $Path
    )
    $h264Args = @($common[0..9] + @("-c:v", "libx264", "-preset", "ultrafast") + $common[10..($common.Count - 1)])
    if ((Invoke-Ffmpeg -Arguments $h264Args) -eq 0) {
        return
    }

    $mpeg4Args = @($common[0..9] + @("-c:v", "mpeg4", "-q:v", "5") + $common[10..($common.Count - 1)])
    if ((Invoke-Ffmpeg -Arguments $mpeg4Args) -ne 0) {
        throw "ffmpeg could not create a sample video at $Path."
    }
}

function New-TvFixture {
    param(
        [string]$Root,
        [string]$SamplePath
    )
    $shows = @(
        "Fixture Alpha",
        "Fixture Beta",
        "Fixture Gamma",
        "Fixture Delta",
        "Fixture Epsilon",
        "Fixture Zeta",
        "Fixture Eta"
    )

    New-Item -ItemType Directory -Path $Root -Force | Out-Null
    for ($i = 0; $i -lt $shows.Count; $i++) {
        $show = $shows[$i]
        $episodeNumber = $i + 1
        $showDir = Join-Path $Root $show
        New-Item -ItemType Directory -Path $showDir -Force | Out-Null
        $episodeFile = Join-Path $showDir ("{0} - S01E{1:00}.mp4" -f $show, $episodeNumber)
        $nfoFile = [System.IO.Path]::ChangeExtension($episodeFile, ".nfo")
        Copy-Item -LiteralPath $SamplePath -Destination $episodeFile -Force
        Set-Content -LiteralPath $nfoFile -Encoding UTF8 -Value @"
<episodedetails>
  <showtitle>$show</showtitle>
  <title>Smoke Episode $episodeNumber</title>
  <season>1</season>
  <episode>$episodeNumber</episode>
  <plot>Android TV parity smoke fixture for $show.</plot>
</episodedetails>
"@
    }
}

function Save-Screenshot {
    param([string]$Path)
    & adb -s $DeviceId exec-out screencap -p > $Path
    if ($LASTEXITCODE -ne 0) {
        throw "adb screencap failed with exit code $LASTEXITCODE."
    }
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -lt 20000) {
        throw "Screenshot is unexpectedly small: $Path ($($file.Length) bytes)."
    }
}

function Get-UiXml {
    param([string]$Path)
    Invoke-Adb -Arguments @("shell", "uiautomator", "dump", "/sdcard/window.xml") | Out-Null
    Invoke-Adb -Arguments @("pull", "/sdcard/window.xml", $Path) | Out-Null
    return [xml](Get-Content -LiteralPath $Path -Raw -Encoding UTF8)
}

function Get-NodeAttribute {
    param(
        [System.Xml.XmlNode]$Node,
        [string]$Name
    )
    $attribute = $Node.Attributes[$Name]
    if ($null -eq $attribute) {
        return ""
    }
    return $attribute.Value
}

function Get-UiNodes {
    param([xml]$Xml)
    return @($Xml.SelectNodes("//node"))
}

function Find-UiNode {
    param(
        [xml]$Xml,
        [string[]]$Needles
    )
    foreach ($node in Get-UiNodes -Xml $Xml) {
        $text = Get-NodeAttribute -Node $node -Name "text"
        $description = Get-NodeAttribute -Node $node -Name "content-desc"
        foreach ($needle in $Needles) {
            if ($text -eq $needle -or $description -eq $needle -or $text.Contains($needle) -or $description.Contains($needle)) {
                return $node
            }
        }
    }
    return $null
}

function Find-FocusedNode {
    param([xml]$Xml)
    foreach ($node in Get-UiNodes -Xml $Xml) {
        if ((Get-NodeAttribute -Node $node -Name "focused") -eq "true") {
            return $node
        }
    }
    return $null
}

function Get-UiTextSummary {
    param([xml]$Xml)
    $values = foreach ($node in Get-UiNodes -Xml $Xml) {
        $text = Get-NodeAttribute -Node $node -Name "text"
        $description = Get-NodeAttribute -Node $node -Name "content-desc"
        if ($text.Trim()) { $text.Trim() }
        if ($description.Trim()) { $description.Trim() }
    }
    return (@($values) | Select-Object -Unique | Select-Object -First 40) -join " | "
}

function Wait-UiText {
    param(
        [string[]]$Needles,
        [string]$XmlPath,
        [int]$TimeoutSeconds = 45
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastXml = $null
    do {
        $lastXml = Get-UiXml -Path $XmlPath
        if (Find-UiNode -Xml $lastXml -Needles $Needles) {
            return $lastXml
        }
        Start-Sleep -Milliseconds 900
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for UI text '$($Needles -join "' or '")'. Current UI: $(Get-UiTextSummary -Xml $lastXml)"
}

function Assert-UiText {
    param(
        [xml]$Xml,
        [string[]]$Needles,
        [string]$Description
    )
    foreach ($needle in $Needles) {
        if (-not (Find-UiNode -Xml $Xml -Needles @($needle))) {
            throw "Missing $Description text '$needle'. Current UI: $(Get-UiTextSummary -Xml $Xml)"
        }
    }
}

function Assert-FocusedUiText {
    param(
        [xml]$Xml,
        [string[]]$Needles,
        [string]$Description
    )
    $focused = Find-FocusedNode -Xml $Xml
    if ($null -eq $focused) {
        throw "Missing focused $Description node. Current UI: $(Get-UiTextSummary -Xml $Xml)"
    }
    $summary = Get-NodeTreeTextSummary -Node $focused
    foreach ($needle in $Needles) {
        if (-not $summary.Contains($needle)) {
            throw "Focused $Description node does not contain '$needle'. Focused UI: $summary"
        }
    }
}

function Get-NearestClickableNode {
    param([System.Xml.XmlNode]$Node)
    $current = $Node
    while ($null -ne $current -and $current.Name -eq "node") {
        if ((Get-NodeAttribute -Node $current -Name "clickable") -eq "true" -and
            (Get-NodeAttribute -Node $current -Name "enabled") -eq "true") {
            return $current
        }
        $current = $current.ParentNode
    }
    return $Node
}

function Get-NodeTreeTextSummary {
    param([System.Xml.XmlNode]$Node)
    $values = New-Object System.Collections.Generic.List[string]
    $stack = New-Object System.Collections.Generic.Stack[System.Xml.XmlNode]
    $stack.Push($Node)
    while ($stack.Count -gt 0) {
        $current = $stack.Pop()
        $text = Get-NodeAttribute -Node $current -Name "text"
        $description = Get-NodeAttribute -Node $current -Name "content-desc"
        if ($text.Trim()) { $values.Add($text.Trim()) }
        if ($description.Trim()) { $values.Add($description.Trim()) }
        foreach ($child in $current.ChildNodes) {
            if ($child -is [System.Xml.XmlNode]) {
                $stack.Push($child)
            }
        }
    }
    return ($values | Select-Object -Unique) -join " | "
}

function Get-NodeCenter {
    param([System.Xml.XmlNode]$Node)
    $bounds = Get-NodeAttribute -Node $Node -Name "bounds"
    if ($bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw "Node has invalid bounds: $bounds"
    }
    return [pscustomobject]@{
        X = [int]((([int]$Matches[1]) + ([int]$Matches[3])) / 2)
        Y = [int]((([int]$Matches[2]) + ([int]$Matches[4])) / 2)
    }
}

function Invoke-UiClick {
    param(
        [xml]$Xml,
        [string[]]$Needles,
        [string]$Description
    )
    $node = Find-UiNode -Xml $Xml -Needles $Needles
    if ($null -eq $node) {
        throw "Cannot find $Description node '$($Needles -join "' or '")'. Current UI: $(Get-UiTextSummary -Xml $Xml)"
    }
    $clickable = Get-NearestClickableNode -Node $node
    $center = Get-NodeCenter -Node $clickable
    Invoke-Adb -Arguments @("shell", "input", "tap", $center.X, $center.Y) | Out-Null
    Start-Sleep -Milliseconds 900
}

function Invoke-DpadKey {
    param(
        [ValidateSet(
            "KEYCODE_DPAD_UP",
            "KEYCODE_DPAD_DOWN",
            "KEYCODE_DPAD_LEFT",
            "KEYCODE_DPAD_RIGHT",
            "KEYCODE_DPAD_CENTER",
            "KEYCODE_ENTER",
            "KEYCODE_BACK"
        )]
        [string]$KeyCode,
        [int]$Repeat = 1,
        [int]$DelayMilliseconds = 450
    )

    for ($i = 0; $i -lt $Repeat; $i++) {
        Invoke-Adb -Arguments @("shell", "input", "keyevent", $KeyCode) | Out-Null
        Start-Sleep -Milliseconds $DelayMilliseconds
    }
}

function Write-Report {
    param(
        [string]$Path,
        [hashtable]$Report
    )
    $Report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $Path -Encoding UTF8
}

$resolvedApkPath = Resolve-FullPath $ApkPath
$resolvedOutputRoot = Resolve-FullPath $OutputRoot
$textExplore = New-UnicodeText @(0x63A2, 0x7D22)
$textScan = New-UnicodeText @(0x626B, 0x63CF)
$textScanMediaLibrary = New-UnicodeText @(0x626B, 0x63CF, 0x5A92, 0x4F53, 0x5E93)
$textHighestHeat = New-UnicodeText @(0x6700, 0x9AD8, 0x70ED, 0x5EA6)
$textRecentlyAdded = New-UnicodeText @(0x6700, 0x8FD1, 0x6DFB, 0x52A0)
$textEpisodeShelf = New-UnicodeText @(0x9009, 0x96C6)
$textPlay = New-UnicodeText @(0x64AD, 0x653E)
$textEpisodeOne = New-UnicodeText @(0x7B2C, 0x20, 0x31, 0x20, 0x96C6)
$textLocalPlayback = New-UnicodeText @(0x672C, 0x5730, 0x64AD, 0x653E)
$textSpeed = New-UnicodeText @(0x500D, 0x901F)
$textPlaybackFailed = New-UnicodeText @(0x64AD, 0x653E, 0x5931, 0x8D25)
$textSettings = New-UnicodeText @(0x8BBE, 0x7F6E)
$textMediaSources = New-UnicodeText @(0x5A92, 0x4F53, 0x6E90)
$textMetadata = New-UnicodeText @(0x5143, 0x6570, 0x636E)
New-Item -ItemType Directory -Path $resolvedOutputRoot -Force | Out-Null

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb is required for Android TV smoke testing."
}
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw "ffmpeg is required to generate the Android TV playback fixture."
}
if (-not $SkipInstall -and -not (Test-Path -LiteralPath $resolvedApkPath -PathType Leaf)) {
    throw "APK does not exist: $resolvedApkPath"
}

$runName = "run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$runDir = Join-Path $resolvedOutputRoot $runName
$fixtureRoot = Join-Path $runDir "fixture\MiruPlayTvSmoke-$($runName.Substring(4))"
$samplePath = Join-Path $runDir "sample.mp4"
$remoteFixtureRoot = "/sdcard/Movies/$(Split-Path -Leaf $fixtureRoot)"
$libraryScreenshot = Join-Path $runDir "android-tv-library.png"
$libraryDpadScreenshot = Join-Path $runDir "android-tv-library-dpad-poster.png"
$detailsScreenshot = Join-Path $runDir "android-tv-details.png"
$detailsEpisodeFocusScreenshot = Join-Path $runDir "android-tv-details-episode-focus.png"
$playerScreenshot = Join-Path $runDir "android-tv-player.png"
$libraryReturnScreenshot = Join-Path $runDir "android-tv-library-return.png"
$settingsScreenshot = Join-Path $runDir "android-tv-settings.png"
$libraryXmlPath = Join-Path $runDir "android-tv-library.xml"
$detailsXmlPath = Join-Path $runDir "android-tv-details.xml"
$detailsEpisodeFocusXmlPath = Join-Path $runDir "android-tv-details-episode-focus.xml"
$playerXmlPath = Join-Path $runDir "android-tv-player.xml"
$detailsReturnXmlPath = Join-Path $runDir "android-tv-details-return.xml"
$libraryReturnXmlPath = Join-Path $runDir "android-tv-library-return.xml"
$settingsXmlPath = Join-Path $runDir "android-tv-settings.xml"
$reportPath = Join-Path $runDir "android-tv-smoke-report.json"
New-Item -ItemType Directory -Path $runDir -Force | Out-Null

New-SampleVideo -Path $samplePath
New-TvFixture -Root $fixtureRoot -SamplePath $samplePath

Invoke-Adb -Arguments @("get-state") | Out-Null
if (-not $SkipInstall) {
    Invoke-Adb -Arguments @("install", "-r", $resolvedApkPath) | Out-Null
}
if (-not $KeepAppData) {
    Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) | Out-Null
}

Invoke-AdbBestEffort -Arguments @("shell", "pm", "grant", $PackageName, "android.permission.READ_EXTERNAL_STORAGE")
Invoke-AdbBestEffort -Arguments @("shell", "pm", "grant", $PackageName, "android.permission.READ_MEDIA_VIDEO")
Invoke-AdbBestEffort -Arguments @("shell", "cmd", "appops", "set", $PackageName, "MANAGE_EXTERNAL_STORAGE", "allow")
Invoke-AdbBestEffort -Arguments @("shell", "mkdir", "-p", "/sdcard/Movies")
Invoke-AdbBestEffort -Arguments @("shell", "rm", "-rf", $remoteFixtureRoot)
Invoke-Adb -Arguments @("push", $fixtureRoot, "/sdcard/Movies/") | Out-Null

Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", $ActivityName, "--es", "test_local_path", $remoteFixtureRoot) | Out-Null
$xml = Wait-UiText -Needles @($textExplore, $textScan, $textScanMediaLibrary) -XmlPath $libraryXmlPath -TimeoutSeconds 30
Invoke-UiClick -Xml $xml -Needles @($textScan, $textScanMediaLibrary) -Description "scan"
$xml = Wait-UiText -Needles @("Fixture Alpha") -XmlPath $libraryXmlPath -TimeoutSeconds 90
Assert-UiText -Xml $xml -Needles @($textExplore, $textHighestHeat, $textRecentlyAdded, "Fixture Alpha") -Description "Library"
Save-Screenshot -Path $libraryScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_DPAD_RIGHT" -DelayMilliseconds 550
Invoke-DpadKey -KeyCode "KEYCODE_DPAD_LEFT" -DelayMilliseconds 550
Save-Screenshot -Path $libraryDpadScreenshot
Invoke-DpadKey -KeyCode "KEYCODE_DPAD_CENTER" -DelayMilliseconds 1100
$xml = Wait-UiText -Needles @($textEpisodeShelf, $textPlay) -XmlPath $detailsXmlPath -TimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @("Fixture Alpha", $textPlay, $textEpisodeShelf, $textEpisodeOne) -Description "Details"
Save-Screenshot -Path $detailsScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_DPAD_DOWN" -DelayMilliseconds 800
$xml = Wait-UiText -Needles @($textEpisodeOne) -XmlPath $detailsEpisodeFocusXmlPath -TimeoutSeconds 15
Assert-FocusedUiText -Xml $xml -Needles @($textEpisodeOne) -Description "Details episode row"
Save-Screenshot -Path $detailsEpisodeFocusScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_DPAD_CENTER" -DelayMilliseconds 1200
$xml = Wait-UiText -Needles @($textLocalPlayback, $textSpeed) -XmlPath $playerXmlPath -TimeoutSeconds 45
Assert-UiText -Xml $xml -Needles @($textLocalPlayback, $textSpeed) -Description "Player"
if (Find-UiNode -Xml $xml -Needles @($textPlaybackFailed)) {
    throw "Player reached an error overlay instead of the playback chrome."
}
Save-Screenshot -Path $playerScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_BACK" -DelayMilliseconds 1200
$xml = Wait-UiText -Needles @($textEpisodeShelf, $textPlay) -XmlPath $detailsReturnXmlPath -TimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @("Fixture Alpha", $textPlay, $textEpisodeShelf, $textEpisodeOne) -Description "Details after Player Back"

Invoke-DpadKey -KeyCode "KEYCODE_BACK" -DelayMilliseconds 1200
$xml = Wait-UiText -Needles @($textExplore, "Fixture Alpha") -XmlPath $libraryReturnXmlPath -TimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @($textExplore, $textScan, $textSettings, "Fixture Alpha") -Description "Library after Details Back"
Assert-FocusedUiText -Xml $xml -Needles @("Fixture Alpha") -Description "Library poster after Back"
Save-Screenshot -Path $libraryReturnScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_DPAD_UP" -DelayMilliseconds 800
Invoke-DpadKey -KeyCode "KEYCODE_DPAD_RIGHT" -DelayMilliseconds 800
Invoke-DpadKey -KeyCode "KEYCODE_DPAD_CENTER" -DelayMilliseconds 1200
$xml = Wait-UiText -Needles @($textSettings, "WebUI", $textMediaSources) -XmlPath $settingsXmlPath -TimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @($textSettings, "WebUI", $textMediaSources, $textPlay, "CloudDrive", $textScan, $textMetadata) -Description "Settings"
Save-Screenshot -Path $settingsScreenshot

Write-Report -Path $reportPath -Report @{
    generatedAt = (Get-Date).ToString("o")
    deviceId = $DeviceId
    apkPath = $resolvedApkPath
    remoteFixtureRoot = $remoteFixtureRoot
    screenshots = @{
        library = $libraryScreenshot
        libraryDpadPoster = $libraryDpadScreenshot
        details = $detailsScreenshot
        detailsEpisodeFocus = $detailsEpisodeFocusScreenshot
        player = $playerScreenshot
        libraryReturn = $libraryReturnScreenshot
        settings = $settingsScreenshot
    }
    xml = @{
        library = $libraryXmlPath
        details = $detailsXmlPath
        detailsEpisodeFocus = $detailsEpisodeFocusXmlPath
        player = $playerXmlPath
        detailsReturn = $detailsReturnXmlPath
        libraryReturn = $libraryReturnXmlPath
        settings = $settingsXmlPath
    }
    assertions = @(
        "Library contains Explore, highest-heat row, recent row, and fixture poster.",
        "Library content requests poster focus; DPAD Right/Left stays on the poster surface and DPAD Center opens Details.",
        "Details contains hero/title, Play, episode list, and first episode row.",
        "DPAD Down from the Details play action focuses the first episode row.",
        "DPAD Center on the focused Details episode row opens Player.",
        "Player contains local playback chrome and no playback failure overlay.",
        "Android Back returns from Player to Details and from Details to the poster-focused Library wall.",
        "DPAD Up/Right/Center from the returned Library poster wall opens Settings.",
        "Settings contains the WebUI, media sources, playback, CloudDrive, scan, and metadata sections."
    )
}

Write-Output "Run directory: $runDir"
Write-Output "Remote fixture: $remoteFixtureRoot"
Write-Output "Library screenshot: $libraryScreenshot"
Write-Output "Library DPAD poster screenshot: $libraryDpadScreenshot"
Write-Output "Details screenshot: $detailsScreenshot"
Write-Output "Details episode focus screenshot: $detailsEpisodeFocusScreenshot"
Write-Output "Player screenshot: $playerScreenshot"
Write-Output "Library return screenshot: $libraryReturnScreenshot"
Write-Output "Settings screenshot: $settingsScreenshot"
Write-Output "Report: $reportPath"
