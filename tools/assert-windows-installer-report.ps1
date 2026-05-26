[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [ValidateSet("msi", "exe", "")]
    [string]$RequiredInstallerType = "",
    [string]$RequiredAppVersion = "",
    [switch]$RequireSigned,
    [switch]$RequireUnsigned,
    [long]$MinSizeBytes = 1048576
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

function Get-FileSha256 {
    param([string]$Path)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            $hash = $sha.ComputeHash($stream)
            return -join ($hash | ForEach-Object { $_.ToString("x2") })
        } finally {
            $stream.Dispose()
        }
    } finally {
        $sha.Dispose()
    }
}

if ($RequireSigned -and $RequireUnsigned) {
    throw "-RequireSigned and -RequireUnsigned cannot both be set."
}

$resolvedReportPath = Resolve-FullPath $ReportPath
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
    throw "Windows installer report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json

Assert-Truthy -Condition ([string]$report.status -eq "ok") -Message "status must be ok."
$installerType = ([string]$report.installerType).ToLowerInvariant()
Assert-Truthy -Condition ($installerType -in @("msi", "exe")) -Message "installerType must be msi or exe."
if (-not [string]::IsNullOrWhiteSpace($RequiredInstallerType)) {
    Assert-Truthy -Condition ($installerType -eq $RequiredInstallerType.ToLowerInvariant()) -Message "installerType '$installerType' did not match required type '$RequiredInstallerType'."
}

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.appVersion)) -Message "Missing appVersion."
if (-not [string]::IsNullOrWhiteSpace($RequiredAppVersion)) {
    Assert-Truthy -Condition ([string]$report.appVersion -eq $RequiredAppVersion) -Message "appVersion '$($report.appVersion)' did not match required version '$RequiredAppVersion'."
}

$signatureMode = ([string]$report.signatureMode).ToLowerInvariant()
Assert-Truthy -Condition ($signatureMode -in @("signed", "unsigned")) -Message "signatureMode must be signed or unsigned."
if ($RequireSigned) {
    Assert-Truthy -Condition ($signatureMode -eq "signed") -Message "Expected a signed installer report."
}
if ($RequireUnsigned) {
    Assert-Truthy -Condition ($signatureMode -eq "unsigned") -Message "Expected an unsigned installer report."
}

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.installerPath)) -Message "Missing installerPath."
$installerPath = if (-not [string]::IsNullOrWhiteSpace([string]$report.installerPath)) {
    Resolve-FullPath ([string]$report.installerPath)
} else {
    ""
}
Assert-Truthy -Condition ([System.IO.Path]::IsPathRooted($installerPath)) -Message "installerPath must be absolute."
if (-not [string]::IsNullOrWhiteSpace($installerPath)) {
    Assert-Truthy -Condition (([System.IO.Path]::GetExtension($installerPath)).TrimStart('.').ToLowerInvariant() -eq $installerType) -Message "installerPath extension does not match installerType."
    Assert-Truthy -Condition (Test-Path -LiteralPath $installerPath -PathType Leaf) -Message "installerPath does not point to an existing file: $installerPath"
}

$sizeBytes = $report.sizeBytes -as [long]
Assert-Truthy -Condition ($sizeBytes -ge $MinSizeBytes) -Message "sizeBytes must be at least $MinSizeBytes."

$sha256 = ([string]$report.sha256).ToLowerInvariant()
Assert-Truthy -Condition ($sha256 -match '^[0-9a-f]{64}$') -Message "sha256 must be a 64-character hex digest."
if (-not [string]::IsNullOrWhiteSpace($installerPath) -and (Test-Path -LiteralPath $installerPath -PathType Leaf)) {
    $fileInfo = Get-Item -LiteralPath $installerPath
    Assert-Truthy -Condition ($fileInfo.Length -eq $sizeBytes) -Message "Installer size $($fileInfo.Length) did not match report size $sizeBytes."
    Assert-Truthy -Condition ((Get-FileSha256 -Path $installerPath) -eq $sha256) -Message "Installer SHA256 did not match report sha256."
}

if ($reportText -match '(?i)windowsInstallerCertPassword|Authorization|Bearer |BEGIN PRIVATE KEY') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive credential material."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "Windows installer report validation failed:`n$summary"
}

Write-Host "Windows installer report validation passed: $resolvedReportPath"
Write-Host "Installer: $installerPath"
Write-Host "Type: $installerType; signing: $signatureMode; size: $sizeBytes bytes"
Write-Host "SHA256: $sha256"
