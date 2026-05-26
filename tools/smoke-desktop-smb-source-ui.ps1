[CmdletBinding()]
param(
    [string]$AppScript = "",
    [string]$OutputRoot = "",
    [string]$ShareTestPath = "",
    [string]$SmbBaseUrl = "",
    [string]$SmbUsername = $env:MIRUPLAY_SMB_SMOKE_USERNAME,
    [string]$SmbPassword = $env:MIRUPLAY_SMB_SMOKE_PASSWORD,
    [string]$SmbDomain = $env:MIRUPLAY_SMB_SMOKE_DOMAIN,
    [switch]$KeepFixture,
    [switch]$KeepOpen
)

$arguments = @{
    Scenario = "desktop-external-smb"
    Step = "smb-source"
    SmbShareTestPath = $ShareTestPath
}
if (-not [string]::IsNullOrWhiteSpace($AppScript)) { $arguments.AppScript = $AppScript }
if (-not [string]::IsNullOrWhiteSpace($OutputRoot)) { $arguments.OutputRoot = $OutputRoot }
if (-not [string]::IsNullOrWhiteSpace($SmbBaseUrl)) { $arguments.SmbBaseUrl = $SmbBaseUrl }
if (-not [string]::IsNullOrWhiteSpace($SmbUsername)) { $arguments.SmbUsername = $SmbUsername }
if (-not [string]::IsNullOrWhiteSpace($SmbPassword)) { $arguments.SmbPassword = $SmbPassword }
if (-not [string]::IsNullOrWhiteSpace($SmbDomain)) { $arguments.SmbDomain = $SmbDomain }
if ($KeepFixture) { $arguments.KeepFixture = $true }
if ($KeepOpen) { $arguments.KeepOpen = $true }

& (Join-Path $PSScriptRoot "run-desktop-behavior.ps1") @arguments
