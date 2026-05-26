[CmdletBinding()]
param(
    [string[]]$Tag = @("smoke"),
    [string]$Scenario = "",
    [Alias("Step")]
    [string]$StepId = "",
    [string]$AppScript = "",
    [string]$OutputRoot = "",
    [switch]$KeepOpen,
    [string]$LibraryRoot = "",
    [string]$SamplePath = "",
    [string]$SmbShareTestPath = "",
    [string]$SmbBaseUrl = "",
    [string]$SmbUsername = "",
    [string]$SmbPassword = "",
    [string]$SmbDomain = "",
    [switch]$KeepFixture,
    [switch]$AllowNoMatchingScenarios
)

$ErrorActionPreference = "Stop"

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
$repoRoot = Split-Path -Parent $scriptRoot
$behaviorRoot = Join-Path $scriptRoot "desktop-behavior"
$scenarioRoot = Join-Path $behaviorRoot "scenarios"
$legacyRoot = Join-Path $behaviorRoot "legacy"

. (Join-Path $scriptRoot "desktop-smoke-common.ps1")

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
        return @("smoke")
    }
    return @($normalized | Select-Object -Unique)
}

function Read-BehaviorJson {
    param([string]$Path)

    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -ErrorAction Stop
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

function Assert-ScenarioRequirements {
    param(
        [object]$ScenarioObject,
        [string]$ResolvedAppScript,
        [string]$ResolvedSmbShareTestPath
    )

    $requirements = @($ScenarioObject.requires | ForEach-Object { $_.ToString() })
    $isWindowsHost = [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
    if ($requirements -contains "windows" -and -not $isWindowsHost) {
        throw "Scenario '$($ScenarioObject.name)' requires Windows."
    }
    if ($requirements -contains "desktop-app-installDist" -and -not (Test-Path -LiteralPath $ResolvedAppScript -PathType Leaf)) {
        throw "Desktop app launcher was not found at $ResolvedAppScript. Run :desktop-app:installDist first."
    }
    if ($requirements -contains "approved-smb-share") {
        if ([string]::IsNullOrWhiteSpace($ResolvedSmbShareTestPath)) {
            throw "Scenario '$($ScenarioObject.name)' requires -SmbShareTestPath."
        }
        if (-not (Test-Path -LiteralPath $ResolvedSmbShareTestPath -PathType Container)) {
            throw "SmbShareTestPath does not exist or is not a directory: $ResolvedSmbShareTestPath"
        }
    }
}

function Convert-OutputToArtifactMap {
    param([object[]]$Lines)

    $artifacts = [ordered]@{}
    foreach ($lineObject in $Lines) {
        $line = $lineObject.ToString()
        if ($line -notmatch '^([^:]+):\s+(.+)$') {
            continue
        }

        $label = $Matches[1].Trim()
        $path = $Matches[2].Trim()
        if ([string]::IsNullOrWhiteSpace($label) -or [string]::IsNullOrWhiteSpace($path)) {
            continue
        }
        if ($path -match '^[A-Za-z]:\\|^\\\\') {
            $key = ($label -replace '[^A-Za-z0-9]+', '_').Trim('_').ToLowerInvariant()
            if (-not [string]::IsNullOrWhiteSpace($key)) {
                $artifacts[$key] = $path
            }
        }
    }
    return $artifacts
}

function Invoke-BehaviorLegacyStep {
    param(
        [object]$StepDefinition,
        [string]$StepOutputRoot,
        [string]$ResolvedAppScript,
        [bool]$KeepOpenValue,
        [string]$ResolvedLibraryRoot,
        [string]$ResolvedSamplePath,
        [string]$ResolvedSmbShareTestPath,
        [string]$SmbBaseUrlValue,
        [string]$SmbUsernameValue,
        [string]$SmbPasswordValue,
        [string]$SmbDomainValue,
        [bool]$KeepFixtureValue
    )

    $scriptName = $StepDefinition.runLegacyScript.ToString()
    $scriptPath = Join-Path $legacyRoot $scriptName
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        throw "Legacy behavior script was not found: $scriptPath"
    }

    New-Item -ItemType Directory -Path $StepOutputRoot -Force | Out-Null

    $arguments = @{
        AppScript = $ResolvedAppScript
        OutputRoot = $StepOutputRoot
    }
    if ($KeepOpenValue) {
        $arguments.KeepOpen = $true
    }
    if ($scriptName -eq "smoke-desktop-local-source-ui.ps1" -and -not [string]::IsNullOrWhiteSpace($ResolvedLibraryRoot)) {
        $arguments.LibraryRoot = $ResolvedLibraryRoot
    }
    if ($scriptName -eq "smoke-desktop-mpv-launch-ui.ps1" -and -not [string]::IsNullOrWhiteSpace($ResolvedSamplePath)) {
        $arguments.SamplePath = $ResolvedSamplePath
    }
    if ($scriptName -eq "smoke-desktop-smb-source-ui.ps1") {
        $arguments.ShareTestPath = $ResolvedSmbShareTestPath
        if (-not [string]::IsNullOrWhiteSpace($SmbBaseUrlValue)) {
            $arguments.SmbBaseUrl = $SmbBaseUrlValue
        }
        if (-not [string]::IsNullOrWhiteSpace($SmbUsernameValue)) {
            $arguments.SmbUsername = $SmbUsernameValue
        }
        if (-not [string]::IsNullOrWhiteSpace($SmbPasswordValue)) {
            $arguments.SmbPassword = $SmbPasswordValue
        }
        if (-not [string]::IsNullOrWhiteSpace($SmbDomainValue)) {
            $arguments.SmbDomain = $SmbDomainValue
        }
        if ($KeepFixtureValue) {
            $arguments.KeepFixture = $true
        }
    }

    $startedAt = [DateTimeOffset]::Now
    $status = "passed"
    $errorMessage = $null
    $output = @()
    try {
        $output = @(& $scriptPath @arguments 2>&1)
    } catch {
        $status = "failed"
        $errorMessage = $_.Exception.Message
        $output += $_
    }
    $finishedAt = [DateTimeOffset]::Now

    $logPath = Join-Path $StepOutputRoot "legacy-output.log"
    $output | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath $logPath -Encoding UTF8

    $result = [ordered]@{
        id = $StepDefinition.id
        label = $StepDefinition.label
        fixture = $StepDefinition.usesFixture
        driver = $scriptName
        status = $status
        startedAt = $startedAt.ToString("o")
        finishedAt = $finishedAt.ToString("o")
        durationSeconds = [Math]::Round(($finishedAt - $startedAt).TotalSeconds, 3)
        outputRoot = $StepOutputRoot
        log = $logPath
        artifacts = Convert-OutputToArtifactMap -Lines $output
    }
    if ($errorMessage) {
        $result.error = $errorMessage
    }
    return $result
}

function Write-BehaviorReport {
    param(
        [string]$ReportPath,
        [object]$Report
    )

    $Report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}

$resolvedAppScript = if ([string]::IsNullOrWhiteSpace($AppScript)) {
    Resolve-BehaviorFullPath "desktop-app\build\install\desktop-app\bin\desktop-app.bat"
} else {
    Resolve-BehaviorFullPath $AppScript
}
$resolvedOutputRoot = if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    Resolve-BehaviorFullPath "build\desktop-behavior"
} else {
    Resolve-BehaviorFullPath $OutputRoot
}
$resolvedLibraryRoot = if ([string]::IsNullOrWhiteSpace($LibraryRoot)) {
    ""
} else {
    Resolve-BehaviorFullPath $LibraryRoot
}
$resolvedSamplePath = if ([string]::IsNullOrWhiteSpace($SamplePath)) {
    ""
} else {
    Resolve-BehaviorFullPath $SamplePath
}
$resolvedSmbShareTestPath = if ([string]::IsNullOrWhiteSpace($SmbShareTestPath)) {
    ""
} else {
    Resolve-BehaviorFullPath $SmbShareTestPath
}
$requestedTags = Get-BehaviorTags -Values $Tag

if (-not (Test-Path -LiteralPath $scenarioRoot -PathType Container)) {
    throw "Behavior scenario directory was not found: $scenarioRoot"
}

$controlsPath = Join-Path $behaviorRoot "controls.json"
$fixturesPath = Join-Path $behaviorRoot "fixtures.json"
$controls = Read-BehaviorJson -Path $controlsPath
$fixtures = Read-BehaviorJson -Path $fixturesPath

$scenarios = @(Get-ChildItem -LiteralPath $scenarioRoot -Filter "*.behavior.json" -File |
    Sort-Object Name |
    ForEach-Object { Read-BehaviorJson -Path $_.FullName } |
    Where-Object { Test-ScenarioMatches -ScenarioObject $_ -ScenarioName $Scenario -Tags $requestedTags })

if ($scenarios.Count -eq 0) {
    $message = if ([string]::IsNullOrWhiteSpace($Scenario)) {
        "No desktop behavior scenarios matched tag(s): $($requestedTags -join ', ')."
    } else {
        "No desktop behavior scenario matched name: $Scenario."
    }
    if ($AllowNoMatchingScenarios) {
        Write-Warning $message
        return
    }
    throw $message
}

$runName = "run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$runDir = Join-Path $resolvedOutputRoot $runName
New-Item -ItemType Directory -Path $runDir -Force | Out-Null

$report = [ordered]@{
    schemaVersion = 1
    name = "desktop-behavior"
    status = "passed"
    requestedTags = $requestedTags
    requestedScenario = $Scenario
    requestedStep = $StepId
    startedAt = [DateTimeOffset]::Now.ToString("o")
    runDirectory = $runDir
    appScript = $resolvedAppScript
    controls = $controlsPath
    fixtures = $fixturesPath
    scenarioCount = $scenarios.Count
    scenarios = @()
}

$reportPath = Join-Path $runDir "report.json"
$runErrorMessage = $null
try {
    foreach ($scenarioObject in $scenarios) {
        Assert-ScenarioRequirements -ScenarioObject $scenarioObject -ResolvedAppScript $resolvedAppScript -ResolvedSmbShareTestPath $resolvedSmbShareTestPath

        $scenarioDir = Join-Path $runDir $scenarioObject.name
        New-Item -ItemType Directory -Path $scenarioDir -Force | Out-Null
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
            steps = @()
        }

        Write-Host "Running desktop behavior scenario: $($scenarioObject.name)"
        $scenarioSteps = @($scenarioObject.steps)
        if (-not [string]::IsNullOrWhiteSpace($StepId)) {
            $scenarioSteps = @($scenarioSteps | Where-Object { $_.id -eq $StepId })
            if ($scenarioSteps.Count -eq 0) {
                throw "Scenario '$($scenarioObject.name)' does not contain step '$StepId'."
            }
        }

        foreach ($scenarioStep in $scenarioSteps) {
            if ($null -eq $scenarioStep -or [string]::IsNullOrWhiteSpace($scenarioStep.id) -or [string]::IsNullOrWhiteSpace($scenarioStep.runLegacyScript)) {
                throw "Scenario '$($scenarioObject.name)' contains an invalid step definition."
            }

            $stepOutputRoot = Join-Path $scenarioDir $scenarioStep.id
            Write-Host "  - $($scenarioStep.id): $($scenarioStep.label)"
            $stepResult = Invoke-BehaviorLegacyStep `
                -StepDefinition $scenarioStep `
                -StepOutputRoot $stepOutputRoot `
                -ResolvedAppScript $resolvedAppScript `
                -KeepOpenValue $KeepOpen.IsPresent `
                -ResolvedLibraryRoot $resolvedLibraryRoot `
                -ResolvedSamplePath $resolvedSamplePath `
                -ResolvedSmbShareTestPath $resolvedSmbShareTestPath `
                -SmbBaseUrlValue $SmbBaseUrl `
                -SmbUsernameValue $SmbUsername `
                -SmbPasswordValue $SmbPassword `
                -SmbDomainValue $SmbDomain `
                -KeepFixtureValue $KeepFixture.IsPresent
            $scenarioResult.steps += $stepResult
            if ($stepResult.status -ne "passed") {
                $scenarioResult.status = "failed"
                $report.status = "failed"
                break
            }
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
    New-Item -ItemType Directory -Path $resolvedOutputRoot -Force | Out-Null
    Set-Content -LiteralPath $latestReportPath -Value $reportPath -Encoding UTF8
}

if ($report.status -ne "passed") {
    throw "Desktop behavior run failed. Report: $reportPath"
}

Write-Output "Desktop behavior report: $reportPath"
