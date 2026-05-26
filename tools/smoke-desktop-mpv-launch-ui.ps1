[CmdletBinding()]
param(
    [string]$AppScript = "",
    [string]$OutputRoot = "",
    [string]$SamplePath = "",
    [switch]$KeepOpen
)

$arguments = @{
    Scenario = "desktop-full"
    Step = "mpv-launch"
}
if (-not [string]::IsNullOrWhiteSpace($AppScript)) { $arguments.AppScript = $AppScript }
if (-not [string]::IsNullOrWhiteSpace($OutputRoot)) { $arguments.OutputRoot = $OutputRoot }
if (-not [string]::IsNullOrWhiteSpace($SamplePath)) { $arguments.SamplePath = $SamplePath }
if ($KeepOpen) { $arguments.KeepOpen = $true }

& (Join-Path $PSScriptRoot "run-desktop-behavior.ps1") @arguments
