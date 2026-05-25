[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [string]$RequiredPath = "",
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
    throw "CloudDrive report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.generatedAtUtc)) -Message "Missing generatedAtUtc."
Assert-Truthy -Condition ([string]$report.endpoint -match '^https?://.+/\.\.\.$') -Message "endpoint must be redacted evidence, not a raw URL."
Assert-Truthy -Condition ($null -ne $report.endpointEvidence) -Message "Missing endpointEvidence."
if ($null -ne $report.endpointEvidence) {
    Assert-Truthy -Condition ([string]$report.endpointEvidence.redacted -eq [string]$report.endpoint) -Message "endpoint must match endpointEvidence.redacted."
    Assert-Truthy -Condition ([string]$report.endpointEvidence.scheme -in @("http", "https")) -Message "endpointEvidence.scheme must be http or https."
    Assert-Truthy -Condition ([string]$report.endpointEvidence.sha256 -match '^[a-f0-9]{64}$') -Message "endpointEvidence.sha256 must be a SHA-256 hex digest."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.endpointEvidence.host)) -Message "endpointEvidence.host is required for HTTP(S) endpoints."
}
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.path)) -Message "Missing path."
if (-not [string]::IsNullOrWhiteSpace($RequiredPath)) {
    Assert-Truthy -Condition ([string]$report.path -eq $RequiredPath) -Message "Report path '$($report.path)' did not match required path '$RequiredPath'."
}

$itemCount = $report.itemCount -as [int]
$directoryCount = $report.directoryCount -as [int]
$fileCount = $report.fileCount -as [int]
Assert-Truthy -Condition ($itemCount -ge 0) -Message "itemCount must be non-negative."
Assert-Truthy -Condition ($directoryCount -ge 0) -Message "directoryCount must be non-negative."
Assert-Truthy -Condition ($fileCount -ge 0) -Message "fileCount must be non-negative."
Assert-Truthy -Condition ($directoryCount + $fileCount -eq $itemCount) -Message "directoryCount + fileCount must equal itemCount."

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
        if ($RequireOfflinePermission) {
            Assert-Truthy -Condition ([bool]$report.tokenInfo.permissions.allowAddOfflineDownload) -Message "Token does not allow offline downloads."
        }
    }
}

$previewItems = Get-JsonArray $report.previewItems
Assert-Truthy -Condition ($previewItems.Count -le $itemCount) -Message "previewItems cannot exceed itemCount."
foreach ($item in $previewItems) {
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$item.name)) -Message "Preview item is missing name."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$item.path)) -Message "Preview item is missing path."
    Assert-Truthy -Condition ($item.isDirectory -is [bool]) -Message "Preview item isDirectory must be a boolean."
    Assert-Truthy -Condition (($item.size -as [long]) -ge 0) -Message "Preview item size must be non-negative."
}

if ($reportText -match '(?i)cloudDriveToken|Authorization|Bearer |@[^"]*:\d+') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive token or authorization text."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "CloudDrive report validation failed:`n$summary"
}

Write-Host "CloudDrive report validation passed: $resolvedReportPath"
Write-Host "Endpoint: $($report.endpoint)"
Write-Host "Path: $($report.path)"
Write-Host "Items: $itemCount ($directoryCount dir, $fileCount file)"
Write-Host "Offline permission required: $([bool]$RequireOfflinePermission)"
