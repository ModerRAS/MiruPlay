[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [string[]]$RequiredTags = @("full"),
    [string[]]$RequiredScenarios = @("android-tv-core"),
    [string[]]$RequiredSteps = @("library-scan", "detail-player-back", "settings-source", "settings-panels"),
    [string]$ExpectedDeviceId = "",
    [bool]$AllowRedactedDeviceId = $true,
    [int64]$MinScreenshotBytes = 5000,
    [int64]$MinFileBytes = 1
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

function Get-JsonArray {
    param($Value)
    if ($null -eq $Value) {
        return @()
    }
    return ,@($Value)
}

function Get-JsonPropertyNames {
    param($Value)
    if ($null -eq $Value) {
        return @()
    }
    return @($Value.PSObject.Properties | ForEach-Object { $_.Name })
}

function Expand-RequiredValues {
    param([string[]]$Values)
    return @(
        $Values |
            ForEach-Object { ([string]$_) -split '[,;]' } |
            ForEach-Object { $_.Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Assert-FileExists {
    param(
        [string]$Path,
        [string]$Description,
        [int64]$MinBytes
    )
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($Path)) -Message "$Description has an empty path."
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return
    }
    $resolved = Resolve-FullPath $Path
    Assert-Truthy -Condition (Test-Path -LiteralPath $resolved -PathType Leaf) -Message "$Description was not found: $resolved"
    if (Test-Path -LiteralPath $resolved -PathType Leaf) {
        $length = (Get-Item -LiteralPath $resolved).Length
        Assert-Truthy -Condition ($length -ge $MinBytes) -Message "$Description is too small: $length bytes."
    }
}

function Assert-DirectoryExists {
    param(
        [string]$Path,
        [string]$Description
    )
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($Path)) -Message "$Description has an empty path."
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return
    }
    $resolved = Resolve-FullPath $Path
    Assert-Truthy -Condition (Test-Path -LiteralPath $resolved -PathType Container) -Message "$Description was not found: $resolved"
}

function Assert-ArtifactMap {
    param(
        [object]$Artifacts,
        [string]$StepId
    )

    Assert-Truthy -Condition ($null -ne $Artifacts) -Message "Step '$StepId' is missing artifacts."
    if ($null -eq $Artifacts) {
        return
    }

    foreach ($property in @($Artifacts.PSObject.Properties)) {
        $name = $property.Name
        $path = [string]$property.Value
        if ($name -eq "driverLog" -or $name -eq "smokeReport") {
            Assert-FileExists -Path $path -Description "Artifact '$StepId/$name'" -MinBytes $MinFileBytes
            continue
        }
        if ($name -like "screenshot.*") {
            Assert-FileExists -Path $path -Description "Artifact '$StepId/$name'" -MinBytes $MinScreenshotBytes
            continue
        }
        if ($name -like "xml.*") {
            Assert-FileExists -Path $path -Description "Artifact '$StepId/$name'" -MinBytes $MinFileBytes
            continue
        }
    }
}

$resolvedReportPath = Resolve-FullPath $ReportPath
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
    throw "Android TV behavior report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json
$requiredTags = Expand-RequiredValues -Values $RequiredTags
$requiredScenarios = Expand-RequiredValues -Values $RequiredScenarios
$requiredSteps = Expand-RequiredValues -Values $RequiredSteps

Assert-Truthy -Condition (($report.schemaVersion -as [int]) -eq 1) -Message "schemaVersion must be 1."
Assert-Truthy -Condition ([string]$report.name -eq "android-tv-behavior") -Message "name must be android-tv-behavior."
Assert-Truthy -Condition ([string]$report.status -eq "passed") -Message "status must be passed."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.startedAt)) -Message "Missing startedAt."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.finishedAt)) -Message "Missing finishedAt."
Assert-DirectoryExists -Path ([string]$report.runDirectory) -Description "runDirectory"
Assert-FileExists -Path ([string]$report.controls) -Description "controls" -MinBytes $MinFileBytes
Assert-FileExists -Path ([string]$report.fixtures) -Description "fixtures" -MinBytes $MinFileBytes
Assert-FileExists -Path ([string]$report.apkPath) -Description "apkPath" -MinBytes $MinFileBytes

$requestedTags = Get-JsonArray $report.requestedTags | ForEach-Object { [string]$_ }
foreach ($requiredTag in $requiredTags) {
    Assert-Truthy -Condition ($requiredTag -in $requestedTags) -Message "Missing requested tag '$requiredTag'."
}

if ([string]::IsNullOrWhiteSpace($ExpectedDeviceId)) {
    if (-not $AllowRedactedDeviceId) {
        Assert-Truthy -Condition ([string]$report.deviceId -ne "<redacted>") -Message "Device id is redacted but a concrete id was required."
    }
} elseif ([string]$report.deviceId -ne $ExpectedDeviceId) {
    Assert-Truthy -Condition $false -Message "Report deviceId '$($report.deviceId)' did not match '$ExpectedDeviceId'."
}

Assert-Truthy -Condition ($report.deviceIdRedacted -is [bool]) -Message "Missing deviceIdRedacted flag."
Assert-Truthy -Condition ([string]$report.deviceId -notmatch '^\d+\.\d+\.\d+\.\d+:\d+$') -Message "Device id should not contain a raw adb host:port."

$scenarios = Get-JsonArray $report.scenarios
Assert-Truthy -Condition ($scenarios.Count -ge $requiredScenarios.Count) -Message "Report includes too few scenarios."

$scenarioByName = @{}
$stepById = @{}
foreach ($scenario in $scenarios) {
    $scenarioName = [string]$scenario.name
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($scenarioName)) -Message "A scenario is missing its name."
    Assert-Truthy -Condition ([string]$scenario.status -eq "passed") -Message "Scenario '$scenarioName' did not pass: $($scenario.status)"
    Assert-DirectoryExists -Path ([string]$scenario.outputRoot) -Description "Scenario '$scenarioName' outputRoot"
    $scenarioByName[$scenarioName] = $scenario

    foreach ($step in (Get-JsonArray $scenario.steps)) {
        $stepId = [string]$step.id
        Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($stepId)) -Message "Scenario '$scenarioName' contains a step without id."
        Assert-Truthy -Condition ([string]$step.status -eq "passed") -Message "Step '$scenarioName/$stepId' did not pass: $($step.status)"
        Assert-Truthy -Condition ($step.assertions.Count -ge 1) -Message "Step '$scenarioName/$stepId' should include contract assertions."
        Assert-FileExists -Path ([string]$step.artifacts.driverLog) -Description "Step '$scenarioName/$stepId' driver log" -MinBytes $MinFileBytes
        Assert-ArtifactMap -Artifacts $step.artifacts -StepId $stepId
        $stepById[$stepId] = $step
    }
}

foreach ($requiredScenario in $requiredScenarios) {
    Assert-Truthy -Condition ($scenarioByName.ContainsKey($requiredScenario)) -Message "Missing required scenario '$requiredScenario'."
}

foreach ($requiredStep in $requiredSteps) {
    Assert-Truthy -Condition ($stepById.ContainsKey($requiredStep)) -Message "Missing required step '$requiredStep'."
}

if ($reportText -match '(?i)(Bearer\s+[A-Za-z0-9._-]+|Authorization:|token=[^&\s"''<>|]+|访问令牌[:：]\s*[A-Za-z0-9._~+/=-]+|smbPassword|cloudDriveToken|10\.\d+\.\d+\.\d+(?::\d+)?|192\.168\.\d+\.\d+(?::\d+)?|172\.(?:1[6-9]|2\d|3[0-1])\.\d+\.\d+(?::\d+)?)') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive credential or private network material."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "Android TV behavior report validation failed:`n$summary"
}

Write-Host "Android TV behavior report validation passed: $resolvedReportPath"
Write-Host "Scenarios: $($scenarios.Count); required steps: $($requiredSteps -join ', ')"
