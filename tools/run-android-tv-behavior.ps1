[CmdletBinding()]
param(
    [string[]]$Tag = @("full"),
    [string]$Scenario = "",
    [string]$DeviceId = "",
    [string]$ApkPath = "",
    [string]$OutputRoot = "",
    [switch]$SkipInstall,
    [switch]$KeepAppData,
    [switch]$SkipConnect,
    [switch]$IncludeDeviceIdInReport,
    [switch]$AllowNoMatchingScenarios
)

$ErrorActionPreference = "Stop"

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
$repoRoot = Split-Path -Parent $scriptRoot
$behaviorRoot = Join-Path $scriptRoot "android-tv-behavior"
$scenarioRoot = Join-Path $behaviorRoot "scenarios"
$smokeScript = Join-Path $scriptRoot "smoke-android-tv-ui.ps1"
$controlsPath = Join-Path $behaviorRoot "controls.json"
$fixturesPath = Join-Path $behaviorRoot "fixtures.json"

function Resolve-BehaviorFullPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Get-BehaviorTags {
    param([string[]]$Values)

    $normalized = @()
    foreach ($value in $Values) {
        if ([string]::IsNullOrWhiteSpace($value)) {
            continue
        }
        $normalized += $value.Split(',', ';', ' ', "`n", "`t") |
            ForEach-Object { $_.Trim().TrimStart('@').ToLowerInvariant() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    }

    if ($normalized.Count -eq 0) {
        return @("full")
    }
    return @($normalized | Select-Object -Unique)
}

function Read-BehaviorJson {
    param([string]$Path)

    try {
        return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "Unable to parse behavior JSON at ${Path}: $($_.Exception.Message)"
    }
}

function Test-ScenarioMatches {
    param(
        [object]$ScenarioObject,
        [string]$ScenarioName,
        [string[]]$Tags
    )

    if (-not [string]::IsNullOrWhiteSpace($ScenarioName)) {
        return $ScenarioObject.name -eq $ScenarioName
    }

    $scenarioTags = @($ScenarioObject.tags | ForEach-Object { $_.ToString().TrimStart('@').ToLowerInvariant() })
    foreach ($tag in $Tags) {
        if ($scenarioTags -contains $tag) {
            return $true
        }
    }
    return $false
}

function Get-JsonArray {
    param($Value)
    if ($null -eq $Value) {
        return @()
    }
    return ,@($Value)
}

function ConvertTo-RedactedText {
    param([AllowNull()][string]$Text)
    if ($null -eq $Text) {
        return ""
    }

    $value = $Text
    $value = [regex]::Replace($value, '(?i)(token=)[^&\s"''<>|]+', '$1[redacted]')
    $value = [regex]::Replace($value, '(?i)(访问令牌[:：]\s*)[A-Za-z0-9._~+/=-]+', '$1[redacted]')
    $value = [regex]::Replace($value, '(?i)(\b(?:Authorization|Bearer)\s*[:=]?\s*)(?:Basic|Bearer)?\s*[A-Za-z0-9._~+/=-]+', '$1[redacted]')
    $value = [regex]::Replace($value, '(?i)(\b(?:access[_-]?token|api[_-]?key|token|password|passwd|secret)\s*[:=]\s*)[^\s,;''"<>|]+', '$1[redacted]')
    $value = [regex]::Replace($value, '(?i)(https?://)[^/@\s"''<>|]+@', '$1[redacted]@')
    $value = [regex]::Replace($value, '(?<!\d)(10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3})(?!\d)', '[private-ip]')
    return $value
}

function Get-StepArtifactKeys {
    param([string]$StepId)

    switch ($StepId) {
        "library-scan" {
            return @{
                Screenshots = @("library", "libraryDpadPoster")
                Xml = @("library")
            }
        }
        "detail-player-back" {
            return @{
                Screenshots = @("details", "detailsEpisodeFocus", "player", "libraryReturn")
                Xml = @("details", "detailsEpisodeFocus", "player", "detailsReturn", "libraryReturn")
            }
        }
        "settings-source" {
            return @{
                Screenshots = @("settings", "settingsSources", "settingsSourceCard", "settingsSourceEdit", "settingsSourceDeleteFocus", "settingsSourceDeleted")
                Xml = @("settings", "settingsSources", "settingsSourceCard", "settingsSourcesReturn", "settingsSourceEdit", "settingsSourceDeleteFocus", "settingsSourceDeleted")
            }
        }
        "settings-panels" {
            return @{
                Screenshots = @("settingsPlayback", "settingsScan")
                Xml = @("settingsPlayback", "settingsCloudDrive", "settingsScan", "settingsMetadata")
            }
        }
        default {
            return @{
                Screenshots = @()
                Xml = @()
            }
        }
    }
}

function Add-ArtifactsForStep {
    param(
        [System.Collections.IDictionary]$Artifacts,
        [object]$SmokeReport,
        [string[]]$ScreenshotKeys,
        [string[]]$XmlKeys
    )

    foreach ($key in $ScreenshotKeys) {
        if ($SmokeReport.screenshots.PSObject.Properties.Name -contains $key) {
            $Artifacts["screenshot.$key"] = [string]$SmokeReport.screenshots.$key
        }
    }
    foreach ($key in $XmlKeys) {
        if ($SmokeReport.xml.PSObject.Properties.Name -contains $key) {
            $Artifacts["xml.$key"] = [string]$SmokeReport.xml.$key
        }
    }
}

function Write-BehaviorReport {
    param(
        [string]$ReportPath,
        [object]$Report
    )

    $Report | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}

if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    $DeviceId = $env:MIRUPLAY_ANDROID_TV_DEVICE_ID
}
if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    throw "Pass -DeviceId or set MIRUPLAY_ANDROID_TV_DEVICE_ID before running Android TV behavior tests."
}

if (-not (Test-Path -LiteralPath $smokeScript -PathType Leaf)) {
    throw "Android TV smoke driver was not found: $smokeScript"
}
if (-not (Test-Path -LiteralPath $scenarioRoot -PathType Container)) {
    throw "Android TV behavior scenario directory was not found: $scenarioRoot"
}

$resolvedApkPath = if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    Resolve-BehaviorFullPath "app\build\outputs\apk\debug\app-debug.apk"
} else {
    Resolve-BehaviorFullPath $ApkPath
}
$resolvedOutputRoot = if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    Resolve-BehaviorFullPath "build\android-tv-behavior"
} else {
    Resolve-BehaviorFullPath $OutputRoot
}
$requestedTags = Get-BehaviorTags -Values $Tag

$scenarios = @(Get-ChildItem -LiteralPath $scenarioRoot -Filter "*.behavior.json" -File |
    Sort-Object Name |
    ForEach-Object { Read-BehaviorJson -Path $_.FullName } |
    Where-Object { Test-ScenarioMatches -ScenarioObject $_ -ScenarioName $Scenario -Tags $requestedTags })

if ($scenarios.Count -eq 0) {
    $message = if ([string]::IsNullOrWhiteSpace($Scenario)) {
        "No Android TV behavior scenarios matched tag(s): $($requestedTags -join ', ')."
    } else {
        "No Android TV behavior scenario matched name: $Scenario."
    }
    if ($AllowNoMatchingScenarios) {
        Write-Warning $message
        return
    }
    throw $message
}

New-Item -ItemType Directory -Path $resolvedOutputRoot -Force | Out-Null
$runName = "run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$runDir = Join-Path $resolvedOutputRoot $runName
New-Item -ItemType Directory -Path $runDir -Force | Out-Null

$report = [ordered]@{
    schemaVersion = 1
    name = "android-tv-behavior"
    status = "passed"
    requestedTags = $requestedTags
    requestedScenario = $Scenario
    startedAt = [DateTimeOffset]::Now.ToString("o")
    runDirectory = $runDir
    deviceId = if ($IncludeDeviceIdInReport) { $DeviceId } else { "<redacted>" }
    deviceIdRedacted = -not [bool]$IncludeDeviceIdInReport
    apkPath = $resolvedApkPath
    controls = $controlsPath
    fixtures = $fixturesPath
    scenarioCount = $scenarios.Count
    scenarios = @()
}

$reportPath = Join-Path $runDir "report.json"
$runErrorMessage = $null
try {
    foreach ($scenarioObject in $scenarios) {
        $scenarioDir = Join-Path $runDir $scenarioObject.name
        $smokeOutputRoot = Join-Path $scenarioDir "driver"
        New-Item -ItemType Directory -Path $smokeOutputRoot -Force | Out-Null

        $scenarioStartedAt = [DateTimeOffset]::Now
        $scenarioResult = [ordered]@{
            name = $scenarioObject.name
            title = $scenarioObject.title
            tags = @($scenarioObject.tags)
            requires = @($scenarioObject.requires)
            given = @($scenarioObject.given)
            when = @($scenarioObject.when)
            then = @($scenarioObject.then)
            status = "passed"
            startedAt = $scenarioStartedAt.ToString("o")
            outputRoot = $scenarioDir
            driver = "smoke-android-tv-ui.ps1"
            steps = @()
        }

        Write-Host "Running Android TV behavior scenario: $($scenarioObject.name)"
        if (-not $SkipConnect -and $DeviceId.Contains(":")) {
            & adb connect $DeviceId *> $null
            if ($LASTEXITCODE -ne 0) {
                throw "adb connect failed for the configured Android TV target."
            }
            Write-Host "adb connect completed for configured Android TV target."
        }

        $smokeArguments = @{
            DeviceId = $DeviceId
            ApkPath = $resolvedApkPath
            OutputRoot = $smokeOutputRoot
        }
        if ($SkipInstall) {
            $smokeArguments.SkipInstall = $true
        }
        if ($KeepAppData) {
            $smokeArguments.KeepAppData = $true
        }
        if ($IncludeDeviceIdInReport) {
            $smokeArguments.IncludeDeviceIdInReport = $true
        }

        $driverStartedAt = [DateTimeOffset]::Now
        $driverStatus = "passed"
        $driverErrorMessage = $null
        $driverOutput = @()
        try {
            $driverOutput = @(& $smokeScript @smokeArguments 2>&1)
        } catch {
            $driverStatus = "failed"
            $driverErrorMessage = ConvertTo-RedactedText -Text $_.Exception.Message
            $driverOutput += $_
        }
        $driverFinishedAt = [DateTimeOffset]::Now
        $driverLogPath = Join-Path $scenarioDir "driver-output.log"
        $driverOutput | ForEach-Object { ConvertTo-RedactedText -Text $_.ToString() } | Set-Content -LiteralPath $driverLogPath -Encoding UTF8

        $latestSmokePointer = Join-Path $smokeOutputRoot "latest-report.txt"
        $smokeReportPath = ""
        $smokeReport = $null
        if (Test-Path -LiteralPath $latestSmokePointer -PathType Leaf) {
            $smokeReportPath = [System.IO.File]::ReadAllText($latestSmokePointer, [System.Text.Encoding]::UTF8).Trim()
        }
        if (-not [string]::IsNullOrWhiteSpace($smokeReportPath) -and (Test-Path -LiteralPath $smokeReportPath -PathType Leaf)) {
            $smokeReport = Get-Content -LiteralPath $smokeReportPath -Raw -Encoding UTF8 | ConvertFrom-Json
        }

        if ($driverStatus -ne "passed" -or $null -eq $smokeReport) {
            $scenarioResult.status = "failed"
            $report.status = "failed"
        }

        foreach ($stepDefinition in (Get-JsonArray $scenarioObject.steps)) {
            $stepStatus = if ($scenarioResult.status -eq "passed") { "passed" } else { "failed" }
            $artifacts = [ordered]@{
                driverLog = $driverLogPath
            }
            if (-not [string]::IsNullOrWhiteSpace($smokeReportPath)) {
                $artifacts.smokeReport = $smokeReportPath
            }
            if ($smokeReport) {
                $artifactKeys = Get-StepArtifactKeys -StepId ([string]$stepDefinition.id)
                Add-ArtifactsForStep -Artifacts $artifacts -SmokeReport $smokeReport -ScreenshotKeys $artifactKeys.Screenshots -XmlKeys $artifactKeys.Xml
            }

            $stepResult = [ordered]@{
                id = $stepDefinition.id
                label = $stepDefinition.label
                fixture = $stepDefinition.usesFixture
                driver = $stepDefinition.driver
                status = $stepStatus
                startedAt = $driverStartedAt.ToString("o")
                finishedAt = $driverFinishedAt.ToString("o")
                durationSeconds = [Math]::Round(($driverFinishedAt - $driverStartedAt).TotalSeconds, 3)
                assertions = @($stepDefinition.assertions)
                artifacts = $artifacts
            }
            if ($driverErrorMessage) {
                $stepResult.error = $driverErrorMessage
            }
            $scenarioResult.steps += $stepResult
        }

        if ($smokeReport) {
            $scenarioResult.rawAssertions = @($smokeReport.assertions)
        }
        if ($driverErrorMessage) {
            $scenarioResult.error = $driverErrorMessage
        }

        $scenarioFinishedAt = [DateTimeOffset]::Now
        $scenarioResult.finishedAt = $scenarioFinishedAt.ToString("o")
        $scenarioResult.durationSeconds = [Math]::Round(($scenarioFinishedAt - $scenarioStartedAt).TotalSeconds, 3)
        $report.scenarios += $scenarioResult
        Write-BehaviorReport -ReportPath $reportPath -Report $report

        if ($scenarioResult.status -ne "passed") {
            break
        }
    }
} catch {
    $report.status = "failed"
    $runErrorMessage = $_.Exception.Message
    throw
} finally {
    if ($runErrorMessage) {
        $report.error = $runErrorMessage
    }
    $report.finishedAt = [DateTimeOffset]::Now.ToString("o")
    Write-BehaviorReport -ReportPath $reportPath -Report $report
    $latestReportPath = Join-Path $resolvedOutputRoot "latest-report.txt"
    Set-Content -LiteralPath $latestReportPath -Value $reportPath -Encoding UTF8
}

if ($report.status -ne "passed") {
    throw "Android TV behavior run failed. Report: $reportPath"
}

Write-Output "Android TV behavior report: $reportPath"
