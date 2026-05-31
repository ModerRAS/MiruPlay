[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [string]$RequiredDeviceId = "",
    [int]$MinScreenshotBytes = 20000,
    [int]$MinXmlBytes = 1000
)

$ErrorActionPreference = "Stop"

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Assert-Truthy {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        $script:failures.Add($Message) | Out-Null
    }
}

function Get-JsonPropertyNames {
    param($Value)
    if ($null -eq $Value) {
        return @()
    }
    return @($Value.PSObject.Properties | ForEach-Object { $_.Name })
}

function Get-JsonArray {
    param($Value)
    if ($null -eq $Value) {
        return @()
    }
    return ,@($Value)
}

function Assert-FileEvidence {
    param(
        [object]$Container,
        [string[]]$RequiredNames,
        [string]$Kind,
        [int64]$MinBytes
    )
    Assert-Truthy -Condition ($null -ne $Container) -Message "Missing $Kind container."
    if ($null -eq $Container) {
        return
    }
    $names = Get-JsonPropertyNames $Container
    foreach ($name in $RequiredNames) {
        Assert-Truthy -Condition ($name -in $names) -Message "Missing $Kind entry '$name'."
        if ($name -notin $names) {
            continue
        }
        $path = [string]$Container.$name
        Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($path)) -Message "$Kind '$name' has an empty path."
        if ([string]::IsNullOrWhiteSpace($path)) {
            continue
        }
        $resolved = Resolve-FullPath $path
        Assert-Truthy -Condition (Test-Path -LiteralPath $resolved -PathType Leaf) -Message "$Kind '$name' was not found: $resolved"
        if (Test-Path -LiteralPath $resolved -PathType Leaf) {
            $length = (Get-Item -LiteralPath $resolved).Length
            Assert-Truthy -Condition ($length -ge $MinBytes) -Message "$Kind '$name' is too small: $length bytes."
        }
    }
}

$resolvedReportPath = Resolve-FullPath $ReportPath
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
    throw "Android TV smoke report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.generatedAt)) -Message "Missing generatedAt."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.deviceId)) -Message "Missing deviceId."
if (-not [string]::IsNullOrWhiteSpace($RequiredDeviceId)) {
    $deviceIdIsRedacted = ([string]$report.deviceId -eq "<redacted>") -or ([bool]$report.deviceIdRedacted)
    if (-not $deviceIdIsRedacted) {
        Assert-Truthy -Condition ([string]$report.deviceId -eq $RequiredDeviceId) -Message "Report deviceId '$($report.deviceId)' did not match '$RequiredDeviceId'."
    }
}
Assert-Truthy -Condition ([string]$report.deviceId -notmatch '^\d+\.\d+\.\d+\.\d+:\d+$') -Message "Report should redact raw adb host:port device ids."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.apkPath)) -Message "Missing apkPath."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.remoteFixtureRoot)) -Message "Missing remoteFixtureRoot."
Assert-Truthy -Condition ([string]$report.remoteFixtureRoot -match '^/sdcard/Movies/MiruPlayTvSmoke-') -Message "remoteFixtureRoot should point at the pushed Android TV fixture."

$requiredScreenshots = @(
    "library",
    "libraryDpadPoster",
    "details",
    "detailsEpisodeFocus",
    "player",
    "libraryReturn",
    "settingsSourceEdit",
    "settingsSourceDeleteFocus",
    "settingsPlayback",
    "settingsScan"
)
if (-not [bool]$report.keepAppData) {
    $requiredScreenshots += @(
        "settings",
        "settingsSources",
        "settingsSourceCard",
        "settingsSourceDeleted"
    )
}
$requiredXml = @(
    "library",
    "details",
    "detailsEpisodeFocus",
    "player",
    "detailsReturn",
    "libraryReturn",
    "settings",
    "settingsSources",
    "settingsSourceCard",
    "settingsSourcesReturn",
    "settingsSourceEdit",
    "settingsSourceDeleteFocus",
    "settingsSourceDeleted",
    "settingsPlayback",
    "settingsCloudDrive",
    "settingsScan",
    "settingsMetadata"
)

Assert-FileEvidence -Container $report.screenshots -RequiredNames $requiredScreenshots -Kind "screenshot" -MinBytes $MinScreenshotBytes
Assert-FileEvidence -Container $report.xml -RequiredNames $requiredXml -Kind "XML dump" -MinBytes $MinXmlBytes

$assertions = Get-JsonArray $report.assertions
Assert-Truthy -Condition ($assertions.Count -ge 15) -Message "Expected at least 15 Android TV smoke assertions."
$requiredAssertionNeedles = @(
    "Library contains Explore",
    "opens Details",
    "first episode row",
    "opens Player",
    "no playback failure",
    "Android Back returns",
    "opens Settings",
    "media sources panel",
    "source card",
    "delete button",
    "removes the",
    "Playback, CloudDrive, Scan, and Metadata"
)
foreach ($needle in $requiredAssertionNeedles) {
    $matches = @($assertions | Where-Object { ([string]$_).Contains($needle) })
    Assert-Truthy -Condition ($matches.Count -gt 0) -Message "Missing assertion containing '$needle'."
}

if ($reportText -match '(?i)(Bearer\s+[A-Za-z0-9._-]+|Authorization:|token=[^&\s"''<>|]+|访问令牌[:：]\s*[A-Za-z0-9._~+/=-]+|smbPassword|cloudDriveToken|10\.\d+\.\d+\.\d+(?::\d+)?|192\.168\.\d+\.\d+(?::\d+)?|172\.(?:1[6-9]|2\d|3[0-1])\.\d+\.\d+(?::\d+)?)') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive credential or private network material."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "Android TV smoke report validation failed:`n$summary"
}

Write-Host "Android TV smoke report validation passed: $resolvedReportPath"
Write-Host "Device: $($report.deviceId)"
Write-Host "Screenshots: $($requiredScreenshots.Count); XML dumps: $($requiredXml.Count); assertions: $($assertions.Count)"
