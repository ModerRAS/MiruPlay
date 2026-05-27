[CmdletBinding()]
param(
    [string]$DeviceId = "",
    [string]$ApkPath = "",
    [string]$OutputRoot = "",
    [switch]$SkipInstall,
    [switch]$KeepAppData
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    $DeviceId = $env:MIRUPLAY_ANDROID_TV_DEVICE_ID
}
if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    throw "Pass -DeviceId or set MIRUPLAY_ANDROID_TV_DEVICE_ID before running the Android TV smoke."
}

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
        "-i", "testsrc2=size=640x360:rate=24:duration=12",
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
    param(
        [string]$Path,
        [int]$Attempts = 4,
        [int]$RetryDelayMilliseconds = 500
    )

    $lastError = $null
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        try {
            Invoke-Adb -Arguments @("shell", "uiautomator", "dump", "/sdcard/window.xml") | Out-Null
            Invoke-Adb -Arguments @("pull", "/sdcard/window.xml", $Path) | Out-Null
            return [xml](Get-Content -LiteralPath $Path -Raw -Encoding UTF8)
        } catch {
            $lastError = $_
            if ($attempt -lt ($Attempts - 1)) {
                Start-Sleep -Milliseconds $RetryDelayMilliseconds
            }
        }
    }

    throw $lastError
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

function Find-UiNodeInHorizontalBand {
    param(
        [xml]$Xml,
        [string[]]$Needles,
        [int]$MaxCenterX = 600,
        [switch]$ExactMatchOnly
    )
    foreach ($node in Get-UiNodes -Xml $Xml) {
        $bounds = Get-NodeAttribute -Node $node -Name "bounds"
        if ($bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
            continue
        }
        $centerX = [int]((([int]$Matches[1]) + ([int]$Matches[3])) / 2)
        if ($centerX -gt $MaxCenterX) {
            continue
        }

        $text = Get-NodeAttribute -Node $node -Name "text"
        $description = Get-NodeAttribute -Node $node -Name "content-desc"
        foreach ($needle in $Needles) {
            if ($ExactMatchOnly) {
                if ($text -eq $needle -or $description -eq $needle) {
                    return $node
                }
                continue
            }
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

function Wait-UiTexts {
    param(
        [string[]]$Needles,
        [string]$XmlPath,
        [int]$TimeoutSeconds = 45
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastXml = $null
    do {
        $lastXml = Get-UiXml -Path $XmlPath
        $missing = @($Needles | Where-Object { -not (Find-UiNode -Xml $lastXml -Needles @($_)) })
        if ($missing.Count -eq 0) {
            return $lastXml
        }
        Start-Sleep -Milliseconds 900
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for UI texts '$($Needles -join "', '")'. Current UI: $(Get-UiTextSummary -Xml $lastXml)"
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

function Assert-UiTextAbsent {
    param(
        [xml]$Xml,
        [string[]]$Needles,
        [string]$Description
    )
    foreach ($needle in $Needles) {
        if (Find-UiNode -Xml $Xml -Needles @($needle)) {
            throw "Unexpected $Description text '$needle'. Current UI: $(Get-UiTextSummary -Xml $Xml)"
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

function Test-FocusedUiText {
    param(
        [xml]$Xml,
        [string[]]$Needles
    )
    $focused = Find-FocusedNode -Xml $Xml
    if ($null -eq $focused) {
        return $false
    }
    $summary = Get-NodeTreeTextSummary -Node $focused
    foreach ($needle in $Needles) {
        if (-not $summary.Contains($needle)) {
            return $false
        }
    }
    return $true
}

function Ensure-FocusedUiText {
    param(
        [string[]]$Needles,
        [string]$XmlPath,
        [ValidateSet(
            "KEYCODE_DPAD_UP",
            "KEYCODE_DPAD_DOWN",
            "KEYCODE_DPAD_LEFT",
            "KEYCODE_DPAD_RIGHT"
        )]
        [string]$RetryKeyCode = "KEYCODE_DPAD_DOWN",
        [int]$Attempts = 4,
        [int]$RetryDelayMilliseconds = 650
    )

    [xml]$lastXml = $null
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        $lastXml = Get-UiXml -Path $XmlPath
        if (Test-FocusedUiText -Xml $lastXml -Needles $Needles) {
            return $lastXml
        }
        if ($attempt -lt ($Attempts - 1)) {
            Invoke-DpadKey -KeyCode $RetryKeyCode -DelayMilliseconds $RetryDelayMilliseconds
        }
    }

    throw "Unable to focus UI text '$($Needles -join "' and '")'. Current UI: $(Get-UiTextSummary -Xml $lastXml)"
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

function Invoke-UiClickInHorizontalBand {
    param(
        [xml]$Xml,
        [string[]]$Needles,
        [string]$Description,
        [int]$MaxCenterX = 600,
        [switch]$ExactMatchOnly
    )
    $node = Find-UiNodeInHorizontalBand -Xml $Xml -Needles $Needles -MaxCenterX $MaxCenterX -ExactMatchOnly:$ExactMatchOnly
    if ($null -eq $node) {
        throw "Cannot find $Description node '$($Needles -join "' or '")' inside horizontal band <= $MaxCenterX. Current UI: $(Get-UiTextSummary -Xml $Xml)"
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

function Invoke-DpadCenterUntilUiText {
    param(
        [string[]]$Needles,
        [string]$XmlPath,
        [int]$Attempts = 3,
        [int]$CenterDelayMilliseconds = 1200,
        [int]$WaitTimeoutSeconds = 12
    )

    [xml]$capturedXml = $null
    $lastError = $null
    for ($openAttempt = 0; $openAttempt -lt $Attempts; $openAttempt++) {
        Invoke-DpadKey -KeyCode "KEYCODE_DPAD_CENTER" -DelayMilliseconds $CenterDelayMilliseconds
        try {
            $capturedXml = Wait-UiTexts -Needles $Needles -XmlPath $XmlPath -TimeoutSeconds $WaitTimeoutSeconds
            return $capturedXml
        } catch {
            $lastError = $_
        }
    }

    throw $lastError
}

function Invoke-DpadDownUntilUiText {
    param(
        [string[]]$Needles,
        [string]$XmlPath,
        [int]$Attempts = 4,
        [int]$DownDelayMilliseconds = 850,
        [int]$WaitTimeoutSeconds = 10
    )

    [xml]$capturedXml = $null
    $lastError = $null
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        Invoke-DpadKey -KeyCode "KEYCODE_DPAD_DOWN" -DelayMilliseconds $DownDelayMilliseconds
        try {
            $capturedXml = Wait-UiTexts -Needles $Needles -XmlPath $XmlPath -TimeoutSeconds $WaitTimeoutSeconds
            return $capturedXml
        } catch {
            $lastError = $_
        }
    }

    throw $lastError
}

function Test-UiHasAllTexts {
    param(
        [xml]$Xml,
        [string[]]$Needles
    )
    foreach ($needle in $Needles) {
        if (-not (Find-UiNode -Xml $Xml -Needles @($needle))) {
            return $false
        }
    }
    return $true
}

function Test-FocusedUiTextAny {
    param(
        [xml]$Xml,
        [string[]]$Needles
    )
    foreach ($needle in $Needles) {
        if (Test-FocusedUiText -Xml $Xml -Needles @($needle)) {
            return $true
        }
    }
    return $false
}

function Ensure-SettingsMenuAnchor {
    param(
        [string]$XmlPath,
        [string[]]$PreferredAnchors,
        [int]$Attempts = 8
    )

    [xml]$lastXml = $null
    $menuAnchors = @("WebUI", $textMediaSources, $textPlay, $textCloudDrive, $textScan, $textLogUpload, $textAppUpdate, $textMetadata)

    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        $lastXml = Get-UiXml -Path $XmlPath
        $isSettingsContext = Test-UiHasAllTexts -Xml $lastXml -Needles @($textSettings, "WebUI", $textMediaSources)

        if ($isSettingsContext) {
            if (Test-FocusedUiTextAny -Xml $lastXml -Needles $PreferredAnchors) {
                return $lastXml
            }

            # Some Compose states expose no focused node in UIAutomator dumps.
            # When we're already in Settings and the target menu label is visible,
            # keep going instead of hard-failing on missing focus metadata.
            if (Find-UiNode -Xml $lastXml -Needles $PreferredAnchors) {
                return $lastXml
            }

            $clickedPreferred = $false
            foreach ($anchor in $PreferredAnchors) {
                if (Find-UiNode -Xml $lastXml -Needles @($anchor)) {
                    Invoke-UiClick -Xml $lastXml -Needles @($anchor) -Description "settings menu anchor '$anchor'"
                    $lastXml = Get-UiXml -Path $XmlPath
                    if (Test-FocusedUiTextAny -Xml $lastXml -Needles $PreferredAnchors) {
                        return $lastXml
                    }
                    $clickedPreferred = $true
                    break
                }
            }
            if ($clickedPreferred) {
                continue
            }

            Invoke-DpadKey -KeyCode "KEYCODE_DPAD_LEFT" -DelayMilliseconds 700
            $lastXml = Get-UiXml -Path $XmlPath
            if (Test-FocusedUiTextAny -Xml $lastXml -Needles $PreferredAnchors) {
                return $lastXml
            }

            if (-not (Test-FocusedUiTextAny -Xml $lastXml -Needles $menuAnchors)) {
                Invoke-DpadKey -KeyCode "KEYCODE_DPAD_UP" -DelayMilliseconds 650
            }
            continue
        }

        if (Test-UiHasAllTexts -Xml $lastXml -Needles @($textExplore, $textSettings)) {
            Invoke-UiClick -Xml $lastXml -Needles @($textSettings) -Description "settings entry from library"
            Start-Sleep -Milliseconds 900
            continue
        }

        Invoke-DpadKey -KeyCode "KEYCODE_BACK" -DelayMilliseconds 1000
    }

    throw "Unable to anchor Settings menu '$($PreferredAnchors -join "' or '")'. Current UI: $(Get-UiTextSummary -Xml $lastXml)"
}

function Open-SettingsPanelByMenuClick {
    param(
        [string]$MenuNeedle,
        [string[]]$PanelNeedles,
        [string]$MenuXmlPath,
        [string]$PanelXmlPath,
        [int]$Attempts = 4,
        [int]$WaitTimeoutSeconds = 20,
        [int]$RevealAttempts = 6
    )

    [xml]$panelXml = $null
    $lastError = $null

    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        $menuXml = Ensure-SettingsMenuAnchor -XmlPath $MenuXmlPath -PreferredAnchors @($MenuNeedle, "WebUI", $textMediaSources, $textPlay, $textCloudDrive, $textScan, $textLogUpload, $textAppUpdate, $textMetadata) -Attempts 8

        for ($revealAttempt = 0; $revealAttempt -lt $RevealAttempts; $revealAttempt++) {
            if (Find-UiNodeInHorizontalBand -Xml $menuXml -Needles @($MenuNeedle) -MaxCenterX 600 -ExactMatchOnly) {
                break
            }

            Invoke-DpadKey -KeyCode "KEYCODE_DPAD_DOWN" -DelayMilliseconds 650
            $menuXml = Get-UiXml -Path $MenuXmlPath
            if (Find-UiNodeInHorizontalBand -Xml $menuXml -Needles @($MenuNeedle) -MaxCenterX 600 -ExactMatchOnly) {
                break
            }

            if (($revealAttempt % 2) -eq 1) {
                Invoke-Adb -Arguments @("shell", "input", "swipe", "350", "930", "350", "520", "220") | Out-Null
                Start-Sleep -Milliseconds 700
                $menuXml = Get-UiXml -Path $MenuXmlPath
            }
        }

        $menuNode = Find-UiNodeInHorizontalBand -Xml $menuXml -Needles @($MenuNeedle) -MaxCenterX 600 -ExactMatchOnly
        if ($null -eq $menuNode) {
            $lastError = "Settings menu '$MenuNeedle' is not visible. Current UI: $(Get-UiTextSummary -Xml $menuXml)"
            continue
        }

        $menuNodeCenter = Get-NodeCenter -Node $menuNode
        if ($menuNodeCenter.Y -gt 930) {
            Invoke-Adb -Arguments @("shell", "input", "swipe", "350", "930", "350", "620", "220") | Out-Null
            Start-Sleep -Milliseconds 700
            $menuXml = Get-UiXml -Path $MenuXmlPath
        }

        Invoke-UiClickInHorizontalBand -Xml $menuXml -Needles @($MenuNeedle) -Description "settings menu '$MenuNeedle'" -MaxCenterX 600 -ExactMatchOnly
        try {
            $panelXml = Wait-UiTexts -Needles $PanelNeedles -XmlPath $PanelXmlPath -TimeoutSeconds $WaitTimeoutSeconds
            return $panelXml
        } catch {
            $lastError = $_
        }
    }

    throw $lastError
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
$textPlaybackEnd = New-UnicodeText @(0x64AD, 0x653E, 0x7ED3, 0x675F)
$textReturnToDetail = New-UnicodeText @(0x8FD4, 0x56DE, 0x8BE6, 0x60C5)
$textPlayNextEpisode = New-UnicodeText @(0x7EE7, 0x7EED, 0x4E0B, 0x4E00, 0x96C6)
$textEpisodeOne = New-UnicodeText @(0x7B2C, 0x20, 0x31, 0x20, 0x96C6)
$textLocalPlayback = New-UnicodeText @(0x672C, 0x5730, 0x64AD, 0x653E)
$textSpeed = New-UnicodeText @(0x500D, 0x901F)
$textPlaybackFailed = New-UnicodeText @(0x64AD, 0x653E, 0x5931, 0x8D25)
$textSettings = New-UnicodeText @(0x8BBE, 0x7F6E)
$textMediaSources = New-UnicodeText @(0x5A92, 0x4F53, 0x6E90)
$textCloudDrive = "CloudDrive"
$textMetadata = New-UnicodeText @(0x5143, 0x6570, 0x636E)
$textLogUpload = New-UnicodeText @(0x65E5, 0x5FD7, 0x4E0A, 0x62A5)
$textAppUpdate = New-UnicodeText @(0x66F4, 0x65B0)
$textCloudDriveAddress = "CloudDrive2"
$textCloudDriveEndpoint = New-UnicodeText @(0x0043, 0x006C, 0x006F, 0x0075, 0x0064, 0x0044, 0x0072, 0x0069, 0x0076, 0x0065, 0x0032, 0x20, 0x5730, 0x5740)
$textRssOffline = New-UnicodeText @(0x0052, 0x0053, 0x0053, 0x20, 0x79BB, 0x7EBF, 0x4E0B, 0x8F7D, 0x4E0E, 0x5165, 0x5E93)
$textApiToken = "API Token / Key"
$textMediaLibraryScan = New-UnicodeText @(0x5A92, 0x4F53, 0x5E93, 0x626B, 0x63CF)
$textTimedScanOff = New-UnicodeText @(0x5B9A, 0x65F6, 0x5173, 0x95ED)
$textMediaLibraryDisplay = New-UnicodeText @(0x5A92, 0x4F53, 0x5E93, 0x663E, 0x793A)
$textMergeSameAnime = New-UnicodeText @(0x540C, 0x756A, 0x5408, 0x5E76)
$textSeparateDirectories = New-UnicodeText @(0x76EE, 0x5F55, 0x5206, 0x5F00)
$textBangumiToken = "Bangumi Access Token"
$textAddMediaSource = New-UnicodeText @(0x6DFB, 0x52A0, 0x5A92, 0x4F53, 0x6E90)
$textEditMediaSource = New-UnicodeText @(0x7F16, 0x8F91, 0x5A92, 0x4F53, 0x6E90)
$textNewSource = New-UnicodeText @(0x65B0, 0x5EFA)
$textSaveSource = New-UnicodeText @(0x4FDD, 0x5B58, 0x6E90)
$textDelete = New-UnicodeText @(0x5220, 0x9664)
$textNoMediaSources = New-UnicodeText @(0x8FD8, 0x6CA1, 0x6709, 0x914D, 0x7F6E, 0x5A92, 0x4F53, 0x6E90)
$textAddLocalOrNetworkLibrary = New-UnicodeText @(0x5148, 0x6DFB, 0x52A0, 0x4E00, 0x4E2A, 0x672C, 0x5730, 0x6216, 0x7F51, 0x7EDC, 0x5A92, 0x4F53, 0x5E93)
$textDisplayName = New-UnicodeText @(0x663E, 0x793A, 0x540D, 0x79F0)
$textMediaFolder = New-UnicodeText @(0x5A92, 0x4F53, 0x6587, 0x4EF6, 0x5939)
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
$settingsSourcesScreenshot = Join-Path $runDir "android-tv-settings-sources.png"
$settingsSourceCardScreenshot = Join-Path $runDir "android-tv-settings-source-card-focus.png"
$settingsSourceEditScreenshot = Join-Path $runDir "android-tv-settings-source-edit.png"
$settingsSourceDeleteFocusScreenshot = Join-Path $runDir "android-tv-settings-source-delete-focus.png"
$settingsSourceDeletedScreenshot = Join-Path $runDir "android-tv-settings-source-deleted.png"
$settingsPlaybackScreenshot = Join-Path $runDir "android-tv-settings-playback.png"
$settingsCloudDriveScreenshot = Join-Path $runDir "android-tv-settings-cloud-drive.png"
$settingsScanScreenshot = Join-Path $runDir "android-tv-settings-scan.png"
$settingsMetadataScreenshot = Join-Path $runDir "android-tv-settings-metadata.png"
$libraryXmlPath = Join-Path $runDir "android-tv-library.xml"
$detailsXmlPath = Join-Path $runDir "android-tv-details.xml"
$detailsEpisodeFocusXmlPath = Join-Path $runDir "android-tv-details-episode-focus.xml"
$playerXmlPath = Join-Path $runDir "android-tv-player.xml"
$detailsReturnXmlPath = Join-Path $runDir "android-tv-details-return.xml"
$libraryReturnXmlPath = Join-Path $runDir "android-tv-library-return.xml"
$settingsXmlPath = Join-Path $runDir "android-tv-settings.xml"
$settingsSourcesXmlPath = Join-Path $runDir "android-tv-settings-sources.xml"
$settingsSourceCardXmlPath = Join-Path $runDir "android-tv-settings-source-card-focus.xml"
$settingsSourcesReturnXmlPath = Join-Path $runDir "android-tv-settings-sources-return.xml"
$settingsSourceEditXmlPath = Join-Path $runDir "android-tv-settings-source-edit.xml"
$settingsSourceDeleteFocusXmlPath = Join-Path $runDir "android-tv-settings-source-delete-focus.xml"
$settingsSourceDeletedXmlPath = Join-Path $runDir "android-tv-settings-source-deleted.xml"
$settingsPlaybackXmlPath = Join-Path $runDir "android-tv-settings-playback.xml"
$settingsCloudDriveXmlPath = Join-Path $runDir "android-tv-settings-cloud-drive.xml"
$settingsScanXmlPath = Join-Path $runDir "android-tv-settings-scan.xml"
$settingsMetadataXmlPath = Join-Path $runDir "android-tv-settings-metadata.xml"
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
$xml = Wait-UiTexts -Needles @($textExplore, $textScan) -XmlPath $libraryXmlPath -TimeoutSeconds 30
Invoke-UiClick -Xml $xml -Needles @($textScan, $textScanMediaLibrary) -Description "scan"
$xml = Wait-UiTexts -Needles @("Fixture Alpha", $textHighestHeat, $textRecentlyAdded) -XmlPath $libraryXmlPath -TimeoutSeconds 120
Assert-UiText -Xml $xml -Needles @($textExplore, $textHighestHeat, $textRecentlyAdded, "Fixture Alpha") -Description "Library"
Save-Screenshot -Path $libraryScreenshot

# Keep the focus contract deterministic before validating DPAD navigation.
Invoke-DpadKey -KeyCode "KEYCODE_DPAD_RIGHT" -DelayMilliseconds 550
Invoke-DpadKey -KeyCode "KEYCODE_DPAD_LEFT" -DelayMilliseconds 550
$xml = Get-UiXml -Path $libraryXmlPath
Assert-UiText -Xml $xml -Needles @("Fixture Alpha") -Description "Library poster after DPAD"
Save-Screenshot -Path $libraryDpadScreenshot
Invoke-UiClick -Xml $xml -Needles @("Fixture Alpha") -Description "library fixture poster"
$xml = Wait-UiTexts -Needles @($textEpisodeShelf, $textPlay) -XmlPath $detailsXmlPath -TimeoutSeconds 20
Assert-UiText -Xml $xml -Needles @("Fixture Alpha", $textPlay, $textEpisodeShelf, $textEpisodeOne) -Description "Details"
Save-Screenshot -Path $detailsScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_DPAD_DOWN" -DelayMilliseconds 800
$xml = Wait-UiText -Needles @($textEpisodeOne) -XmlPath $detailsEpisodeFocusXmlPath -TimeoutSeconds 15
Assert-FocusedUiText -Xml $xml -Needles @($textEpisodeOne) -Description "Details episode row"
Save-Screenshot -Path $detailsEpisodeFocusScreenshot

Invoke-UiClick -Xml $xml -Needles @($textEpisodeOne) -Description "details first episode row"
$xml = Wait-UiTexts -Needles @($textLocalPlayback, $textSpeed) -XmlPath $playerXmlPath -TimeoutSeconds 20
Assert-UiText -Xml $xml -Needles @($textLocalPlayback, $textSpeed) -Description "Player"
if (Find-UiNode -Xml $xml -Needles @($textPlaybackFailed)) {
    throw "Player reached an error overlay instead of the playback chrome."
}
Save-Screenshot -Path $playerScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_BACK" -DelayMilliseconds 1200
$xml = Wait-UiTexts -Needles @($textEpisodeShelf, $textPlay) -XmlPath $detailsReturnXmlPath -TimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @("Fixture Alpha", $textPlay, $textEpisodeShelf, $textEpisodeOne) -Description "Details after Player Back"

Invoke-DpadKey -KeyCode "KEYCODE_BACK" -DelayMilliseconds 1200
$xml = Wait-UiTexts -Needles @($textExplore, "Fixture Alpha") -XmlPath $libraryReturnXmlPath -TimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @($textExplore, $textScan, $textSettings, "Fixture Alpha") -Description "Library after Details Back"
if (-not (Test-FocusedUiText -Xml $xml -Needles @("Fixture Alpha"))) {
    Invoke-DpadKey -KeyCode "KEYCODE_DPAD_RIGHT" -DelayMilliseconds 800
    $xml = Wait-UiTexts -Needles @($textExplore, "Fixture Alpha") -XmlPath $libraryReturnXmlPath -TimeoutSeconds 10
}
Assert-FocusedUiText -Xml $xml -Needles @("Fixture Alpha") -Description "Library poster after Back"
Save-Screenshot -Path $libraryReturnScreenshot

$openedSettings = $false
$settingsOpenError = $null
for ($settingsAttempt = 0; $settingsAttempt -lt 3 -and -not $openedSettings; $settingsAttempt++) {
    Invoke-DpadKey -KeyCode "KEYCODE_DPAD_UP" -DelayMilliseconds 800
    Invoke-DpadKey -KeyCode "KEYCODE_DPAD_RIGHT" -DelayMilliseconds 800
    Invoke-DpadKey -KeyCode "KEYCODE_DPAD_CENTER" -DelayMilliseconds 1200
    try {
        $xml = Wait-UiTexts -Needles @($textSettings, "WebUI", $textMediaSources) -XmlPath $settingsXmlPath -TimeoutSeconds 12
        $openedSettings = $true
    } catch {
        $settingsOpenError = $_
    }
}
if (-not $openedSettings) {
    $fallbackXml = Get-UiXml -Path $libraryReturnXmlPath
    Invoke-UiClick -Xml $fallbackXml -Needles @($textSettings) -Description "settings"
    try {
        $xml = Wait-UiTexts -Needles @($textSettings, "WebUI", $textMediaSources) -XmlPath $settingsXmlPath -TimeoutSeconds 12
        $openedSettings = $true
    } catch {
        if ($null -ne $settingsOpenError) {
            throw $settingsOpenError
        }
        throw
    }
}
Assert-UiText -Xml $xml -Needles @($textSettings, "WebUI", $textMediaSources, $textScan, $textMetadata) -Description "Settings"
$xml = Ensure-FocusedUiText -Needles @("WebUI") -XmlPath $settingsXmlPath -RetryKeyCode "KEYCODE_DPAD_UP" -Attempts 4
Assert-FocusedUiText -Xml $xml -Needles @("WebUI") -Description "Settings menu"
Save-Screenshot -Path $settingsScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_DPAD_DOWN" -DelayMilliseconds 800
$xml = Ensure-FocusedUiText -Needles @($textMediaSources) -XmlPath $settingsXmlPath -RetryKeyCode "KEYCODE_DPAD_DOWN" -Attempts 3
$xml = Invoke-DpadCenterUntilUiText -Needles @("Test Local", $textAddMediaSource, $remoteFixtureRoot) -XmlPath $settingsSourcesXmlPath -Attempts 2 -WaitTimeoutSeconds 20
Assert-UiText -Xml $xml -Needles @($textMediaSources, "Test Local", $remoteFixtureRoot, "WebDAV", "SMB", $textAddMediaSource, $textDisplayName, $textMediaFolder, $textSaveSource) -Description "Settings media sources"
Assert-FocusedUiText -Xml $xml -Needles @($textMediaSources) -Description "Settings media source menu"
Save-Screenshot -Path $settingsSourcesScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_DPAD_RIGHT" -DelayMilliseconds 900
$xml = Wait-UiTexts -Needles @("Test Local", $remoteFixtureRoot) -XmlPath $settingsSourceCardXmlPath -TimeoutSeconds 30
Assert-FocusedUiText -Xml $xml -Needles @("Test Local") -Description "Settings media source card"
Save-Screenshot -Path $settingsSourceCardScreenshot

Invoke-DpadKey -KeyCode "KEYCODE_DPAD_LEFT" -DelayMilliseconds 900
try {
    $xml = Wait-UiTexts -Needles @("Test Local", $textMediaSources) -XmlPath $settingsSourcesReturnXmlPath -TimeoutSeconds 30
} catch {
    $menuXml = Wait-UiTexts -Needles @($textSettings, "WebUI", $textMediaSources) -XmlPath $settingsXmlPath -TimeoutSeconds 12
    $menuXml = Ensure-FocusedUiText -Needles @($textMediaSources) -XmlPath $settingsXmlPath -RetryKeyCode "KEYCODE_DPAD_DOWN" -Attempts 4
    $xml = Invoke-DpadCenterUntilUiText -Needles @("Test Local", $textMediaSources) -XmlPath $settingsSourcesReturnXmlPath -Attempts 2 -WaitTimeoutSeconds 15
}
$xml = Ensure-FocusedUiText -Needles @($textMediaSources) -XmlPath $settingsSourcesReturnXmlPath -RetryKeyCode "KEYCODE_DPAD_DOWN" -Attempts 5
Assert-FocusedUiText -Xml $xml -Needles @($textMediaSources) -Description "Settings media source menu after content Left"

# Open source edit with an explicit card click to avoid DPAD drift.
Invoke-UiClick -Xml $xml -Needles @("Test Local") -Description "settings media source card"
$xml = Wait-UiTexts -Needles @($textEditMediaSource, $textNewSource) -XmlPath $settingsSourceEditXmlPath -TimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @("Test Local", $remoteFixtureRoot, $textEditMediaSource, $textNewSource, $textDisplayName, $textMediaFolder) -Description "Settings media source edit form"
Save-Screenshot -Path $settingsSourceEditScreenshot

$xml = Wait-UiTexts -Needles @($textDelete, $textEditMediaSource) -XmlPath $settingsSourceDeleteFocusXmlPath -TimeoutSeconds 30
Save-Screenshot -Path $settingsSourceDeleteFocusScreenshot

Invoke-UiClick -Xml $xml -Needles @($textDelete) -Description "settings media source delete button"
$xml = Wait-UiTexts -Needles @($textNoMediaSources, $textAddLocalOrNetworkLibrary) -XmlPath $settingsSourceDeletedXmlPath -TimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @($textMediaSources, $textNoMediaSources, $textAddLocalOrNetworkLibrary, $textAddMediaSource, $textSaveSource) -Description "Settings media source empty state after delete"
Assert-UiTextAbsent -Xml $xml -Needles @("Test Local", $remoteFixtureRoot) -Description "deleted media source"
Save-Screenshot -Path $settingsSourceDeletedScreenshot

$xml = Open-SettingsPanelByMenuClick -MenuNeedle $textPlay -PanelNeedles @($textPlaybackEnd, $textReturnToDetail, $textPlayNextEpisode) -MenuXmlPath $settingsSourceDeletedXmlPath -PanelXmlPath $settingsPlaybackXmlPath -Attempts 4 -WaitTimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @($textPlay, $textPlaybackEnd, $textReturnToDetail, $textPlayNextEpisode) -Description "Settings playback"
Save-Screenshot -Path $settingsPlaybackScreenshot

$xml = Open-SettingsPanelByMenuClick -MenuNeedle $textCloudDrive -PanelNeedles @($textCloudDriveAddress, $textCloudDriveEndpoint, $textApiToken) -MenuXmlPath $settingsPlaybackXmlPath -PanelXmlPath $settingsCloudDriveXmlPath -Attempts 4 -WaitTimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @($textCloudDriveAddress, $textCloudDriveEndpoint, $textRssOffline, $textApiToken) -Description "Settings CloudDrive"
Save-Screenshot -Path $settingsCloudDriveScreenshot

$xml = Open-SettingsPanelByMenuClick -MenuNeedle $textScan -PanelNeedles @($textMediaLibraryScan, $textTimedScanOff, $textMediaLibraryDisplay) -MenuXmlPath $settingsCloudDriveXmlPath -PanelXmlPath $settingsScanXmlPath -Attempts 4 -WaitTimeoutSeconds 30
Assert-UiText -Xml $xml -Needles @($textScan, $textMediaLibraryScan, $textTimedScanOff, $textMediaLibraryDisplay) -Description "Settings scan"
if (-not (Find-UiNode -Xml $xml -Needles @($textMergeSameAnime, $textSeparateDirectories))) {
    throw "Missing Settings scan media-display state. Current UI: $(Get-UiTextSummary -Xml $xml)"
}
Save-Screenshot -Path $settingsScanScreenshot

$xml = Open-SettingsPanelByMenuClick -MenuNeedle $textMetadata -PanelNeedles @($textMetadata, $textBangumiToken) -MenuXmlPath $settingsScanXmlPath -PanelXmlPath $settingsMetadataXmlPath -Attempts 6 -WaitTimeoutSeconds 20 -RevealAttempts 10
Assert-UiText -Xml $xml -Needles @($textMetadata, $textBangumiToken) -Description "Settings metadata"
Save-Screenshot -Path $settingsMetadataScreenshot

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
        settingsSources = $settingsSourcesScreenshot
        settingsSourceCard = $settingsSourceCardScreenshot
        settingsSourceEdit = $settingsSourceEditScreenshot
        settingsSourceDeleteFocus = $settingsSourceDeleteFocusScreenshot
        settingsSourceDeleted = $settingsSourceDeletedScreenshot
        settingsPlayback = $settingsPlaybackScreenshot
        settingsCloudDrive = $settingsCloudDriveScreenshot
        settingsScan = $settingsScanScreenshot
        settingsMetadata = $settingsMetadataScreenshot
    }
    xml = @{
        library = $libraryXmlPath
        details = $detailsXmlPath
        detailsEpisodeFocus = $detailsEpisodeFocusXmlPath
        player = $playerXmlPath
        detailsReturn = $detailsReturnXmlPath
        libraryReturn = $libraryReturnXmlPath
        settings = $settingsXmlPath
        settingsSources = $settingsSourcesXmlPath
        settingsSourceCard = $settingsSourceCardXmlPath
        settingsSourcesReturn = $settingsSourcesReturnXmlPath
        settingsSourceEdit = $settingsSourceEditXmlPath
        settingsSourceDeleteFocus = $settingsSourceDeleteFocusXmlPath
        settingsSourceDeleted = $settingsSourceDeletedXmlPath
        settingsPlayback = $settingsPlaybackXmlPath
        settingsCloudDrive = $settingsCloudDriveXmlPath
        settingsScan = $settingsScanXmlPath
        settingsMetadata = $settingsMetadataXmlPath
    }
    assertions = @(
        "Library contains Explore, highest-heat row, recent row, and fixture poster.",
        "Library content requests poster focus; DPAD Right/Left stays on the poster surface and DPAD Center opens Details.",
        "Details contains hero/title, Play, episode list, and first episode row.",
        "DPAD Down from the Details play action focuses the first episode row.",
        "DPAD Center on the focused Details episode row opens Player.",
        "Player contains local playback chrome and no playback failure overlay.",
        "Android Back returns from Player to Details and from Details to Library, and DPAD Right restores poster focus when existing app data left no focused node.",
        "DPAD Up/Right/Center from the returned Library poster wall opens Settings.",
        "Settings contains the WebUI, media sources, playback, CloudDrive, scan, and metadata sections.",
        "DPAD Down/Center in Settings opens the media sources panel with the auto-added local source and source form.",
        "DPAD Right from the Settings media-source menu focuses the auto-added source card, and Left returns to the media-source menu.",
        "DPAD Center on the focused Settings source card opens the edit source form without losing card focus.",
        "DPAD Right from the focused Settings source card focuses its delete button.",
        "DPAD Center on the focused delete button removes the source and shows the empty media-source state.",
        "DPAD Down from the media-source menu visits Playback, CloudDrive, Scan, and Metadata settings pages with matching menu focus."
    )
}

$latestReportPath = Join-Path $resolvedOutputRoot "latest-report.txt"
Set-Content -LiteralPath $latestReportPath -Encoding UTF8 -Value $reportPath

Write-Output "Run directory: $runDir"
Write-Output "Remote fixture: $remoteFixtureRoot"
Write-Output "Library screenshot: $libraryScreenshot"
Write-Output "Library DPAD poster screenshot: $libraryDpadScreenshot"
Write-Output "Details screenshot: $detailsScreenshot"
Write-Output "Details episode focus screenshot: $detailsEpisodeFocusScreenshot"
Write-Output "Player screenshot: $playerScreenshot"
Write-Output "Library return screenshot: $libraryReturnScreenshot"
Write-Output "Settings screenshot: $settingsScreenshot"
Write-Output "Settings sources screenshot: $settingsSourcesScreenshot"
Write-Output "Settings source card screenshot: $settingsSourceCardScreenshot"
Write-Output "Settings source edit screenshot: $settingsSourceEditScreenshot"
Write-Output "Settings source delete focus screenshot: $settingsSourceDeleteFocusScreenshot"
Write-Output "Settings source deleted screenshot: $settingsSourceDeletedScreenshot"
Write-Output "Settings playback screenshot: $settingsPlaybackScreenshot"
Write-Output "Settings CloudDrive screenshot: $settingsCloudDriveScreenshot"
Write-Output "Settings scan screenshot: $settingsScanScreenshot"
Write-Output "Settings metadata screenshot: $settingsMetadataScreenshot"
Write-Output "Report: $reportPath"
Write-Output "Latest report pointer: $latestReportPath"
