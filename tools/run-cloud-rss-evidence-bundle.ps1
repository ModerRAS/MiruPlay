param(
    [Parameter(Mandatory=$true)][string]$Endpoint,
    [Parameter(Mandatory=$true)][string]$Token,
    [Parameter(Mandatory=$true)][string]$RssUrl,
    [string]$Inbox = '/Downloads',
    [string]$Library = '/Library',
    [int]$SubmitLimit = 1,
    [string]$Filter = ''
)

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$verify = Join-Path $repoRoot 'tools\verify-windows-port.ps1'

$args = @(
    '-NoProfile',
    '-ExecutionPolicy',
    'Bypass',
    '-File',
    $verify,
    '-SkipGradle',
    '-SkipCloudRssScheduler',
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
    '-RequireCloudDriveOfflinePermission',
    '-RequireCloudRssCandidates'
)
if (-not [string]::IsNullOrWhiteSpace($Filter)) {
    $args += @('-CloudRssFilter', $Filter)
}

Write-Warning 'This evidence bundle runs live-submit and organize smokes. It submits real CloudDrive offline downloads and can move real CloudDrive files.'
Write-Warning 'It also requires the CloudDrive token to allow offline downloads, matching the strict completion audit.'
Write-Warning 'This is Cloud/RSS evidence only. Run tools\verify-windows-port.ps1 -CompletionAudit after all Windows release evidence is collected.'

& powershell.exe @args
exit $LASTEXITCODE
