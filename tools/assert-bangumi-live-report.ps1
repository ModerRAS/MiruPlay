[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [int]$MinResults = 1,
    [int]$MinRegularEpisodes = 1,
    [string]$ExpectedTitle = "",
    [string]$RequiredSubjectId = ""
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

function Test-TextContains {
    param(
        [string[]]$Values,
        [string]$Needle
    )
    if ([string]::IsNullOrWhiteSpace($Needle)) {
        return $true
    }
    foreach ($value in $Values) {
        if (-not [string]::IsNullOrWhiteSpace($value) -and $value.IndexOf($Needle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $true
        }
    }
    return $false
}

$resolvedReportPath = Resolve-FullPath $ReportPath
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
    throw "Bangumi live smoke report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.generatedAtUtc)) -Message "Missing generatedAtUtc."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.query)) -Message "Missing query."
Assert-Truthy -Condition (($report.searchElapsedMs -as [long]) -ge 0) -Message "searchElapsedMs must be non-negative."
Assert-Truthy -Condition (($report.resultCount -as [int]) -ge $MinResults) -Message "resultCount is below required minimum $MinResults."

$topResults = Get-JsonArray $report.topResults
Assert-Truthy -Condition ($topResults.Count -gt 0) -Message "topResults should not be empty."
foreach ($result in $topResults) {
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.animeId)) -Message "A top result is missing animeId."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.title)) -Message "A top result is missing title."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.displayTitle)) -Message "A top result is missing displayTitle."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.matchedTitle)) -Message "A top result is missing matchedTitle."
    Assert-Truthy -Condition (($result.confidence -as [double]) -gt 0) -Message "A top result confidence must be positive."
}

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.subjectId)) -Message "Missing subjectId."
if (-not [string]::IsNullOrWhiteSpace($RequiredSubjectId)) {
    Assert-Truthy -Condition ([string]$report.subjectId -eq $RequiredSubjectId) -Message "subjectId '$($report.subjectId)' did not match '$RequiredSubjectId'."
}
Assert-Truthy -Condition (($report.detailsElapsedMs -as [long]) -ge 0) -Message "detailsElapsedMs must be non-negative."
Assert-Truthy -Condition (($report.episodeElapsedMs -as [long]) -ge 0) -Message "episodeElapsedMs must be non-negative."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.detailTitle) -or -not [string]::IsNullOrWhiteSpace([string]$report.detailTitleCn)) -Message "Missing detail title."
Assert-Truthy -Condition (($report.detailEpisodeCount -as [int]) -ge $MinRegularEpisodes) -Message "detailEpisodeCount is below required minimum $MinRegularEpisodes."
Assert-Truthy -Condition (($report.regularEpisodeCount -as [int]) -ge $MinRegularEpisodes) -Message "regularEpisodeCount is below required minimum $MinRegularEpisodes."

if (-not [string]::IsNullOrWhiteSpace($ExpectedTitle)) {
    $titleEvidence = New-Object 'System.Collections.Generic.List[string]'
    $titleEvidence.Add([string]$report.detailTitle) | Out-Null
    $titleEvidence.Add([string]$report.detailTitleCn) | Out-Null
    foreach ($result in $topResults) {
        $titleEvidence.Add([string]$result.title) | Out-Null
        $titleEvidence.Add([string]$result.titleCn) | Out-Null
        $titleEvidence.Add([string]$result.displayTitle) | Out-Null
        $titleEvidence.Add([string]$result.matchedTitle) | Out-Null
    }
    Assert-Truthy -Condition (Test-TextContains -Values $titleEvidence.ToArray() -Needle $ExpectedTitle) -Message "Expected title '$ExpectedTitle' was not found in report title evidence."
}

if ($reportText -match '(?i)Authorization|Bearer |accessToken|apiToken') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive credential material."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "Bangumi live smoke report validation failed:`n$summary"
}

Write-Host "Bangumi live smoke report validation passed: $resolvedReportPath"
Write-Host "Query: $($report.query); results: $($report.resultCount); subject: $($report.subjectId); episodes: $($report.regularEpisodeCount)"
