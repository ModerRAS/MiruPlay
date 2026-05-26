[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [string[]]$RequiredChecks = @(
        "api_requires_token",
        "static_shell_served",
        "cookie_authorizes_api",
        "sources_redact_secrets",
        "library_exposes_progress",
        "detail_exposes_episode",
        "cloud_drive_summary_served",
        "playback_play_api",
        "playback_command_api"
    ),
    [int]$MinStaticAssetBytes = 100
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

$resolvedReportPath = Resolve-FullPath $ReportPath
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
    throw "Desktop WebUI smoke report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json

Assert-Truthy -Condition (($report.schemaVersion -as [int]) -eq 1) -Message "schemaVersion must be 1."
Assert-Truthy -Condition ([string]$report.name -eq "desktop-web-control-smoke") -Message "name must be desktop-web-control-smoke."
Assert-Truthy -Condition ([string]$report.status -eq "passed") -Message "status must be passed."
Assert-Truthy -Condition (($report.port -as [int]) -gt 0) -Message "port must be positive."
Assert-Truthy -Condition (($report.sourceId -as [long]) -gt 0) -Message "sourceId must be positive."
Assert-Truthy -Condition (($report.staticAssetBytes -as [int]) -ge $MinStaticAssetBytes) -Message "staticAssetBytes is below $MinStaticAssetBytes."
Assert-Truthy -Condition ($null -eq $report.error -or [string]::IsNullOrWhiteSpace([string]$report.error)) -Message "error should be empty."

$checks = Get-JsonArray $report.checks
Assert-Truthy -Condition ($checks.Count -ge $RequiredChecks.Count) -Message "Expected at least $($RequiredChecks.Count) checks."
$checkStatuses = @{}
foreach ($check in $checks) {
    $name = [string]$check.name
    $status = [string]$check.status
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($name)) -Message "A check is missing its name."
    Assert-Truthy -Condition ($status -eq "passed") -Message "Check '$name' did not pass: $status"
    if (-not [string]::IsNullOrWhiteSpace($name)) {
        $checkStatuses[$name] = $status
    }
}

foreach ($requiredCheck in $RequiredChecks) {
    Assert-Truthy -Condition ($checkStatuses.ContainsKey($requiredCheck)) -Message "Missing required check '$requiredCheck'."
    if ($checkStatuses.ContainsKey($requiredCheck)) {
        Assert-Truthy -Condition ($checkStatuses[$requiredCheck] -eq "passed") -Message "Required check '$requiredCheck' did not pass."
    }
}

if ($reportText -match '(?i)webui-smoke-password|Authorization|Bearer |"accessToken"\s*:') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive credential material."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "Desktop WebUI smoke report validation failed:`n$summary"
}

Write-Host "Desktop WebUI smoke report validation passed: $resolvedReportPath"
Write-Host "Checks: $($checks.Count); static asset bytes: $($report.staticAssetBytes)"
