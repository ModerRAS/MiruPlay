[CmdletBinding()]
param(
    [string]$AppScript = "",
    [string]$OutputRoot = "",
    [switch]$KeepOpen
)

$arguments = @{
    Scenario = "desktop-smoke"
    Step = "keyboard-focus"
}
if (-not [string]::IsNullOrWhiteSpace($AppScript)) { $arguments.AppScript = $AppScript }
if (-not [string]::IsNullOrWhiteSpace($OutputRoot)) { $arguments.OutputRoot = $OutputRoot }
if ($KeepOpen) { $arguments.KeepOpen = $true }

& (Join-Path $PSScriptRoot "run-desktop-behavior.ps1") @arguments
