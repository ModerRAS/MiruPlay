[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [string]$RequiredBackends = "DIRECTML",
    [switch]$AllowFailures
)

$ErrorActionPreference = "Stop"

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Split-BackendList {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return @()
    }
    return @(
        $Value -split '[,;\s]+' |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            ForEach-Object { $_.Trim().ToUpperInvariant() } |
            Select-Object -Unique
    )
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
    throw "RIFE report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json
$required = Split-BackendList -Value $RequiredBackends
$allowed = @("NVIDIA", "DIRECTML", "STANDARD")
$unknownRequired = @($required | Where-Object { $_ -notin $allowed })
Assert-Truthy -Condition ($unknownRequired.Count -eq 0) -Message "Unknown required backend(s): $($unknownRequired -join ', ')"

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.GeneratedAtUtc)) -Message "Missing GeneratedAtUtc."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.RuntimeRoot)) -Message "Missing RuntimeRoot."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.MpvPath)) -Message "Missing MpvPath."
Assert-Truthy -Condition ([string]$report.MpvVersion -match '^mpv ') -Message "MpvVersion does not look like an mpv --version line."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.ConfigDirectory)) -Message "Missing ConfigDirectory."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.ClipPath)) -Message "Missing ClipPath."
Assert-Truthy -Condition (($report.Clip.Width -as [int]) -gt 0) -Message "Clip.Width must be positive."
Assert-Truthy -Condition (($report.Clip.Height -as [int]) -gt 0) -Message "Clip.Height must be positive."
Assert-Truthy -Condition (($report.Clip.Frames -as [int]) -gt 0) -Message "Clip.Frames must be positive."

Assert-Truthy -Condition ($null -ne $report.Host) -Message "Missing Host diagnostics."
if ($null -ne $report.Host) {
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.Host.PowerShellVersion)) -Message "Missing Host.PowerShellVersion."
    Assert-Truthy -Condition ($null -ne $report.Host.OperatingSystem) -Message "Missing Host.OperatingSystem."
    Assert-Truthy -Condition ((Get-JsonArray $report.Host.Processors).Count -gt 0) -Message "Host.Processors must contain at least one entry."
    Assert-Truthy -Condition ((Get-JsonArray $report.Host.VideoControllers).Count -gt 0) -Message "Host.VideoControllers must contain at least one entry."
}

$results = Get-JsonArray $report.Results
Assert-Truthy -Condition ($results.Count -gt 0) -Message "Results must contain at least one backend result."
$resultsByBackend = @{}
foreach ($result in $results) {
    $backend = ([string]$result.Backend).Trim().ToUpperInvariant()
    Assert-Truthy -Condition ($backend -in $allowed) -Message "Unknown result backend: $($result.Backend)"
    Assert-Truthy -Condition ([string]$result.Status -in @("PASS", "FAIL")) -Message "Backend $backend has invalid Status: $($result.Status)"
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.Message)) -Message "Backend $backend is missing Message."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.LogPath)) -Message "Backend $backend is missing LogPath."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.StartedAtUtc)) -Message "Backend $backend is missing StartedAtUtc."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.FinishedAtUtc)) -Message "Backend $backend is missing FinishedAtUtc."
    if ([string]$result.Status -eq "PASS") {
        Assert-Truthy -Condition (($result.ExitCode -as [int]) -eq 0) -Message "Backend $backend passed but ExitCode is not 0."
    }
    if (-not [string]::IsNullOrWhiteSpace($backend)) {
        $resultsByBackend[$backend] = $result
    }
}

foreach ($backend in $required) {
    Assert-Truthy -Condition ($resultsByBackend.ContainsKey($backend)) -Message "Required backend $backend was not present in Results."
    if ($resultsByBackend.ContainsKey($backend) -and -not $AllowFailures) {
        Assert-Truthy -Condition ([string]$resultsByBackend[$backend].Status -eq "PASS") -Message "Required backend $backend did not PASS."
    }
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "RIFE report validation failed:`n$summary"
}

Write-Host "RIFE report validation passed: $resolvedReportPath"
Write-Host "Required backend(s): $(if ($required.Count -gt 0) { $required -join ', ' } else { '(none)' })"
Write-Host "Allow failures: $([bool]$AllowFailures)"
$results | Format-Table Backend, Status, Message -AutoSize | Out-String | Write-Host
