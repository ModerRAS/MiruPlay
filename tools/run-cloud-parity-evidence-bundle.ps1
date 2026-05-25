param(
    [Parameter(Mandatory=$true)][string]$Endpoint,
    [Parameter(Mandatory=$true)][string]$Token,
    [Parameter(Mandatory=$true)][string]$RssUrl,
    [string]$Inbox = '/Downloads',
    [string]$Library = '/Library',
    [int]$SubmitLimit = 1,
    [string]$Filter = '',
    [string]$CloudDrivePath = '',
    [switch]$RequireCloudDriveOfflinePermission
)

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$verify = Join-Path $repoRoot 'tools\verify-windows-port.ps1'

$effectiveCloudDrivePath = if ([string]::IsNullOrWhiteSpace($CloudDrivePath)) {
    $Inbox
} else {
    $CloudDrivePath
}

$args = @(
    '-NoProfile',
    '-ExecutionPolicy',
    'Bypass',
    '-File',
    $verify,
    '-SkipGradle',
    '-SkipCloudRssScheduler',
    '-CloudDrive',
    '-CloudDriveEndpoint',
    $Endpoint,
    '-CloudDriveToken',
    $Token,
    '-CloudDrivePath',
    $effectiveCloudDrivePath,
    '-CloudRssEvidenceBundle',
    '-ConfirmCloudRssLiveSubmit',
    '-ConfirmCloudRssOrganize',
    '-CloudRssEndpoint',
    $Endpoint,
    '-CloudRssToken',
    $Token,
    '-CloudRssUrl',
    $RssUrl,
    '-CloudRssInbox',
    $Inbox,
    '-CloudRssLibrary',
    $Library,
    '-CloudRssSubmitLimit',
    $SubmitLimit.ToString(),
    '-RequireCloudRssCandidates'
)
if (-not [string]::IsNullOrWhiteSpace($Filter)) {
    $args += @('-CloudRssFilter', $Filter)
}
if ($RequireCloudDriveOfflinePermission) {
    $args += '-RequireCloudDriveOfflinePermission'
}

& powershell.exe @args
exit $LASTEXITCODE
