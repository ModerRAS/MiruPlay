[CmdletBinding()]
param(
    [string]$AppScript = "",
    [string]$OutputRoot = "",
    [string]$LibraryRoot = "",
    [switch]$KeepOpen
)

$arguments = @{
    Scenario = "desktop-smoke"
    Step = "local-source"
}
if (-not [string]::IsNullOrWhiteSpace($AppScript)) { $arguments.AppScript = $AppScript }
if (-not [string]::IsNullOrWhiteSpace($OutputRoot)) { $arguments.OutputRoot = $OutputRoot }
if (-not [string]::IsNullOrWhiteSpace($LibraryRoot)) { $arguments.LibraryRoot = $LibraryRoot }
if ($KeepOpen) { $arguments.KeepOpen = $true }

& (Join-Path $PSScriptRoot "run-desktop-behavior.ps1") @arguments
