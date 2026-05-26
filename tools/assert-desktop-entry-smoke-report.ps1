[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [string]$ExpectedEntryPoint = "",
    [string]$ExpectedWindowTitle = "",
    [string]$ExpectedInitialSection = "",
    [string]$ExpectedRuntimeRoot = "",
    [string]$ExpectedMpvExecutable = "",
    [string]$ExpectedConfigDirectory = ""
)

$ErrorActionPreference = "Stop"

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Normalize-OptionalPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return ""
    }
    return [System.IO.Path]::GetFullPath($Path)
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

function Assert-ExpectedString {
    param(
        [string]$Actual,
        [string]$Expected,
        [string]$Name
    )
    if ([string]::IsNullOrWhiteSpace($Expected)) {
        return
    }
    Assert-Truthy -Condition ($Actual -eq $Expected) -Message "$Name '$Actual' did not match expected '$Expected'."
}

function Assert-ExpectedPath {
    param(
        [string]$Actual,
        [string]$Expected,
        [string]$Name
    )
    if ([string]::IsNullOrWhiteSpace($Expected)) {
        return
    }
    $actualPath = Normalize-OptionalPath $Actual
    $expectedPath = Normalize-OptionalPath $Expected
    Assert-Truthy -Condition ($actualPath -eq $expectedPath) -Message "$Name '$actualPath' did not match expected '$expectedPath'."
}

$resolvedReportPath = Resolve-FullPath $ReportPath
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
    throw "Desktop entry smoke report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json

Assert-Truthy -Condition ([string]$report.status -eq "ok") -Message "status must be ok."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.entryPoint)) -Message "Missing entryPoint."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.windowTitle)) -Message "Missing windowTitle."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.initialSection)) -Message "Missing initialSection."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.runtimeRoot)) -Message "Missing runtimeRoot."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.mpvExecutable)) -Message "Missing mpvExecutable."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.configDirectory)) -Message "Missing configDirectory."

Assert-ExpectedString -Actual ([string]$report.entryPoint) -Expected $ExpectedEntryPoint -Name "entryPoint"
Assert-ExpectedString -Actual ([string]$report.windowTitle) -Expected $ExpectedWindowTitle -Name "windowTitle"
Assert-ExpectedString -Actual ([string]$report.initialSection) -Expected $ExpectedInitialSection -Name "initialSection"
Assert-ExpectedPath -Actual ([string]$report.runtimeRoot) -Expected $ExpectedRuntimeRoot -Name "runtimeRoot"
Assert-ExpectedPath -Actual ([string]$report.mpvExecutable) -Expected $ExpectedMpvExecutable -Name "mpvExecutable"
Assert-ExpectedPath -Actual ([string]$report.configDirectory) -Expected $ExpectedConfigDirectory -Name "configDirectory"

$runtimeRoot = Normalize-OptionalPath ([string]$report.runtimeRoot)
$mpvExecutable = Normalize-OptionalPath ([string]$report.mpvExecutable)
$configDirectory = Normalize-OptionalPath ([string]$report.configDirectory)
Assert-Truthy -Condition (Test-Path -LiteralPath $runtimeRoot -PathType Container) -Message "runtimeRoot does not exist: $runtimeRoot"
Assert-Truthy -Condition (Test-Path -LiteralPath $mpvExecutable -PathType Leaf) -Message "mpvExecutable does not exist: $mpvExecutable"
Assert-Truthy -Condition (Test-Path -LiteralPath $configDirectory -PathType Container) -Message "configDirectory does not exist: $configDirectory"

if ($reportText -match '(?i)Authorization|Bearer |password|secret|token') {
    Assert-Truthy -Condition $false -Message "Report appears to contain sensitive credential material."
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "Desktop entry smoke report validation failed:`n$summary"
}

Write-Host "Desktop entry smoke report validation passed: $resolvedReportPath"
Write-Host "Entry: $($report.entryPoint); section: $($report.initialSection); runtime: $($report.runtimeRoot)"
