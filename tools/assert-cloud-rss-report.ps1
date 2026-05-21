[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [string]$RequiredInbox = "",
    [string]$RequiredLibrary = "",
    [switch]$RequireCandidates,
    [switch]$RequireLiveSubmit,
    [switch]$RequireOrganize,
    [switch]$RequireOfflinePermission
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
    throw "CloudDrive RSS report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.generatedAtUtc)) -Message "Missing generatedAtUtc."
Assert-Truthy -Condition ([string]$report.endpoint -match '^https?://') -Message "Endpoint must be an HTTP(S) URL."
Assert-Truthy -Condition ([string]$report.rssUrl -match '^(https?|file)[:/]') -Message "rssUrl must look like a URL."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.inboxPath)) -Message "Missing inboxPath."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.libraryPath)) -Message "Missing libraryPath."
if (-not [string]::IsNullOrWhiteSpace($RequiredInbox)) {
    Assert-Truthy -Condition ([string]$report.inboxPath -eq $RequiredInbox) -Message "Report inboxPath '$($report.inboxPath)' did not match required inbox '$RequiredInbox'."
}
if (-not [string]::IsNullOrWhiteSpace($RequiredLibrary)) {
    Assert-Truthy -Condition ([string]$report.libraryPath -eq $RequiredLibrary) -Message "Report libraryPath '$($report.libraryPath)' did not match required library '$RequiredLibrary'."
}
Assert-Truthy -Condition ([string]$report.inboxPath -ne [string]$report.libraryPath) -Message "inboxPath and libraryPath must be different."

$feedItemCount = $report.feedItemCount -as [int]
$candidateCount = $report.candidateCount -as [int]
$skippedByFilterCount = $report.skippedByFilterCount -as [int]
$missingSubmissionCount = $report.missingSubmissionCount -as [int]
$magnetCandidateCount = $report.magnetCandidateCount -as [int]
$torrentCandidateCount = $report.torrentCandidateCount -as [int]
$otherCandidateCount = $report.otherCandidateCount -as [int]

foreach ($entry in @(
    @("feedItemCount", $feedItemCount),
    @("candidateCount", $candidateCount),
    @("skippedByFilterCount", $skippedByFilterCount),
    @("missingSubmissionCount", $missingSubmissionCount),
    @("magnetCandidateCount", $magnetCandidateCount),
    @("torrentCandidateCount", $torrentCandidateCount),
    @("otherCandidateCount", $otherCandidateCount)
)) {
    Assert-Truthy -Condition ($entry[1] -ge 0) -Message "$($entry[0]) must be non-negative."
}
Assert-Truthy -Condition ($candidateCount + $skippedByFilterCount + $missingSubmissionCount -le $feedItemCount) -Message "Candidate, skipped, and missing counts cannot exceed feedItemCount."
Assert-Truthy -Condition ($magnetCandidateCount + $torrentCandidateCount + $otherCandidateCount -eq $candidateCount) -Message "Submission type counts must equal candidateCount."
if ($RequireCandidates) {
    Assert-Truthy -Condition ($candidateCount -gt 0) -Message "Expected at least one would-submit candidate."
}

Assert-Truthy -Condition ($null -ne $report.liveSubmit) -Message "Missing liveSubmit."
if ($null -ne $report.liveSubmit) {
    Assert-Truthy -Condition ($report.liveSubmit.enabled -is [bool]) -Message "liveSubmit.enabled must be a boolean."
    $submitAttempted = $report.liveSubmit.attemptedCount -as [int]
    $submitSucceeded = $report.liveSubmit.succeededCount -as [int]
    $preparedTorrent = $report.liveSubmit.preparedTorrentCount -as [int]
    Assert-Truthy -Condition ($submitAttempted -ge 0) -Message "liveSubmit.attemptedCount must be non-negative."
    Assert-Truthy -Condition ($submitSucceeded -ge 0) -Message "liveSubmit.succeededCount must be non-negative."
    Assert-Truthy -Condition ($preparedTorrent -ge 0) -Message "liveSubmit.preparedTorrentCount must be non-negative."
    Assert-Truthy -Condition ($submitSucceeded -le $submitAttempted) -Message "liveSubmit.succeededCount cannot exceed attemptedCount."
    Assert-Truthy -Condition ($preparedTorrent -le $submitAttempted) -Message "liveSubmit.preparedTorrentCount cannot exceed attemptedCount."
    if ($RequireLiveSubmit) {
        Assert-Truthy -Condition ([bool]$report.liveSubmit.enabled) -Message "Expected liveSubmit.enabled to be true."
        Assert-Truthy -Condition ($submitAttempted -gt 0) -Message "Expected at least one live submit attempt."
        Assert-Truthy -Condition ($submitSucceeded -eq $submitAttempted) -Message "Expected all live submit attempts to succeed."
        Assert-Truthy -Condition ($null -ne $report.liveSubmit.postSubmitInboxItemCount) -Message "Expected postSubmitInboxItemCount for live submit evidence."
    }
}

Assert-Truthy -Condition ($null -ne $report.organize) -Message "Missing organize."
if ($null -ne $report.organize) {
    Assert-Truthy -Condition ($report.organize.enabled -is [bool]) -Message "organize.enabled must be a boolean."
    $movedCount = $report.organize.movedCount -as [int]
    Assert-Truthy -Condition ($movedCount -ge 0) -Message "organize.movedCount must be non-negative."
    if ($RequireOrganize) {
        Assert-Truthy -Condition ([bool]$report.organize.enabled) -Message "Expected organize.enabled to be true."
        Assert-Truthy -Condition ($movedCount -gt 0) -Message "Expected organize.movedCount to prove at least one file was moved."
        Assert-Truthy -Condition ($null -ne $report.organize.postOrganizeInboxItemCount) -Message "Expected postOrganizeInboxItemCount for organize evidence."
        Assert-Truthy -Condition ($null -ne $report.organize.postOrganizeLibraryItemCount) -Message "Expected postOrganizeLibraryItemCount for organize evidence."
    }
}

Assert-Truthy -Condition ($null -ne $report.tokenInfo) -Message "Missing tokenInfo."
if ($null -ne $report.tokenInfo) {
    Assert-Truthy -Condition ($null -ne $report.tokenInfo.permissions) -Message "Missing tokenInfo.permissions."
    if ($null -ne $report.tokenInfo.permissions) {
        foreach ($name in @(
            "allowList",
            "allowCreateFolder",
            "allowCreateFile",
            "allowWrite",
            "allowMove",
            "allowAddOfflineDownload"
        )) {
            $value = $report.tokenInfo.permissions.$name
            Assert-Truthy -Condition ($value -is [bool]) -Message "Permission $name must be a boolean."
        }
        if ($RequireOfflinePermission -or $RequireLiveSubmit) {
            Assert-Truthy -Condition ([bool]$report.tokenInfo.permissions.allowAddOfflineDownload) -Message "Token does not allow offline downloads."
        }
    }
}

$previewItems = Get-JsonArray $report.previewItems
Assert-Truthy -Condition ($previewItems.Count -le $feedItemCount) -Message "previewItems cannot exceed feedItemCount."
foreach ($item in $previewItems) {
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$item.title)) -Message "Preview item is missing title."
    Assert-Truthy -Condition ([string]$item.status -in @("WOULD_SUBMIT", "SKIPPED_FILTER", "MISSING_SUBMISSION")) -Message "Preview item has invalid status: $($item.status)"
    Assert-Truthy -Condition ([string]$item.submissionType -in @("MAGNET", "TORRENT", "OTHER", "NONE")) -Message "Preview item has invalid submissionType: $($item.submissionType)"
}

if ($reportText -match '(?i)cloudDriveToken|Authorization|Bearer ') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive token or authorization text."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "CloudDrive RSS report validation failed:`n$summary"
}

Write-Host "CloudDrive RSS report validation passed: $resolvedReportPath"
Write-Host "Inbox: $($report.inboxPath)"
Write-Host "Library: $($report.libraryPath)"
Write-Host "Feed items: $feedItemCount; candidates: $candidateCount"
Write-Host "Live submit required: $([bool]$RequireLiveSubmit); organize required: $([bool]$RequireOrganize)"
