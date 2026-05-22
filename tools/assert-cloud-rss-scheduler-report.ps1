[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [int]$MinRunCount = 1,
    [int]$MinChecksObserved = 1
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

$resolvedReportPath = Resolve-FullPath $ReportPath
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
    throw "CloudDrive RSS scheduler report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.startedAtUtc)) -Message "Missing startedAtUtc."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.finishedAtUtc)) -Message "Missing finishedAtUtc."

$elapsedMillis = $report.elapsedMillis -as [long]
$checkIntervalMillis = $report.checkIntervalMillis -as [long]
$requestedRunAfterChecks = $report.requestedRunAfterChecks -as [int]
$checksObserved = $report.checksObserved -as [int]
$runCount = $report.runCount -as [int]
$lastCheckedAt = $report.lastCheckedAt -as [long]
$lastRunCompletedAt = $report.lastRunCompletedAt -as [long]

Assert-Truthy -Condition ($elapsedMillis -gt 0) -Message "elapsedMillis must be positive."
Assert-Truthy -Condition ($checkIntervalMillis -gt 0) -Message "checkIntervalMillis must be positive."
Assert-Truthy -Condition ($requestedRunAfterChecks -gt 0) -Message "requestedRunAfterChecks must be positive."
Assert-Truthy -Condition ($checksObserved -ge $MinChecksObserved) -Message "checksObserved is below required minimum $MinChecksObserved."
Assert-Truthy -Condition ($checksObserved -ge $requestedRunAfterChecks) -Message "checksObserved must reach requestedRunAfterChecks."
Assert-Truthy -Condition ($runCount -ge $MinRunCount) -Message "runCount is below required minimum $MinRunCount."
Assert-Truthy -Condition ([bool]$report.startReturned) -Message "First scheduler start should return true."
Assert-Truthy -Condition (-not [bool]$report.secondStartReturned) -Message "Second scheduler start should return false."
Assert-Truthy -Condition (-not [bool]$report.finalRunning) -Message "Scheduler should be stopped at the end of the smoke."
Assert-Truthy -Condition ($lastCheckedAt -gt 0) -Message "lastCheckedAt must be positive."
Assert-Truthy -Condition ($lastRunCompletedAt -gt 0) -Message "lastRunCompletedAt must be positive."
Assert-Truthy -Condition ($null -eq $report.lastError -or [string]::IsNullOrWhiteSpace([string]$report.lastError)) -Message "lastError should be empty."
Assert-Truthy -Condition ($null -ne $report.lastSummary) -Message "Missing lastSummary."
if ($null -ne $report.lastSummary) {
    foreach ($entry in @(
        @("submitted", ($report.lastSummary.submitted -as [int])),
        @("skipped", ($report.lastSummary.skipped -as [int])),
        @("failed", ($report.lastSummary.failed -as [int])),
        @("organized", ($report.lastSummary.organized -as [int]))
    )) {
        Assert-Truthy -Condition ($entry[1] -ge 0) -Message "lastSummary.$($entry[0]) must be non-negative."
    }
    Assert-Truthy -Condition (($report.lastSummary.failed -as [int]) -eq 0) -Message "lastSummary.failed should be 0."
}

if ($reportText -match '(?i)cloudDriveToken|Authorization|Bearer ') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive token or authorization text."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "CloudDrive RSS scheduler report validation failed:`n$summary"
}

Write-Host "CloudDrive RSS scheduler report validation passed: $resolvedReportPath"
Write-Host "Elapsed: $elapsedMillis ms; checks: $checksObserved; runs: $runCount"
