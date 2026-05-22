[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath,
    [string]$RequiredBackends = "DIRECTML",
    [switch]$RequireRuntimeManifest,
    [switch]$AllowFailures
)

$ErrorActionPreference = "Stop"

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Split-BackendList {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return @()
    }
    return @(
        $Value -split '[,;\s]+' |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            ForEach-Object { $_.Trim().ToUpperInvariant() } |
            Select-Object -Unique
    )
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

function Expand-JsonArray {
    param($Value)
    if ($null -eq $Value) {
        return @()
    }
    return @($Value)
}

function Get-RifeScriptName {
    param([string]$BackendName)
    switch ($BackendName) {
        "NVIDIA" { return "MEMC_RIFE_NV.vpy" }
        "DIRECTML" { return "MEMC_RIFE_DML.vpy" }
        "STANDARD" { return "MEMC_RIFE_STD.vpy" }
        default { return "" }
    }
}

$resolvedReportPath = Resolve-FullPath $ReportPath
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
    throw "RIFE report was not found: $resolvedReportPath"
}

$failures = New-Object 'System.Collections.Generic.List[string]'
$reportText = [System.IO.File]::ReadAllText($resolvedReportPath, [System.Text.Encoding]::UTF8)
$report = $reportText | ConvertFrom-Json
$required = Split-BackendList -Value $RequiredBackends
$allowed = @("NVIDIA", "DIRECTML", "STANDARD")
$unknownRequired = @($required | Where-Object { $_ -notin $allowed })
Assert-Truthy -Condition ($unknownRequired.Count -eq 0) -Message "Unknown required backend(s): $($unknownRequired -join ', ')"

Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.GeneratedAtUtc)) -Message "Missing GeneratedAtUtc."
$runtimeRoot = [string]$report.RuntimeRoot
$mpvPath = [string]$report.MpvPath
$configDirectory = [string]$report.ConfigDirectory
$clipPath = [string]$report.ClipPath
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($runtimeRoot)) -Message "Missing RuntimeRoot."
if (-not [string]::IsNullOrWhiteSpace($runtimeRoot)) {
    Assert-Truthy -Condition ([System.IO.Path]::IsPathRooted($runtimeRoot)) -Message "RuntimeRoot must be absolute."
    Assert-Truthy -Condition (Test-Path -LiteralPath $runtimeRoot -PathType Container) -Message "RuntimeRoot does not point to an existing directory: $runtimeRoot"
}
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($mpvPath)) -Message "Missing MpvPath."
if (-not [string]::IsNullOrWhiteSpace($mpvPath)) {
    Assert-Truthy -Condition ([System.IO.Path]::IsPathRooted($mpvPath)) -Message "MpvPath must be absolute."
    Assert-Truthy -Condition (Test-Path -LiteralPath $mpvPath -PathType Leaf) -Message "MpvPath does not point to an existing file: $mpvPath"
}
Assert-Truthy -Condition ([string]$report.MpvVersion -match '^mpv ') -Message "MpvVersion does not look like an mpv --version line."
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($configDirectory)) -Message "Missing ConfigDirectory."
if (-not [string]::IsNullOrWhiteSpace($configDirectory)) {
    Assert-Truthy -Condition ([System.IO.Path]::IsPathRooted($configDirectory)) -Message "ConfigDirectory must be absolute."
    Assert-Truthy -Condition (Test-Path -LiteralPath $configDirectory -PathType Container) -Message "ConfigDirectory does not point to an existing directory: $configDirectory"
}
Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace($clipPath)) -Message "Missing ClipPath."
if (-not [string]::IsNullOrWhiteSpace($clipPath)) {
    Assert-Truthy -Condition ([System.IO.Path]::IsPathRooted($clipPath)) -Message "ClipPath must be absolute."
    Assert-Truthy -Condition (Test-Path -LiteralPath $clipPath -PathType Leaf) -Message "ClipPath does not point to an existing file: $clipPath"
}
Assert-Truthy -Condition (($report.Clip.Width -as [int]) -gt 0) -Message "Clip.Width must be positive."
Assert-Truthy -Condition (($report.Clip.Height -as [int]) -gt 0) -Message "Clip.Height must be positive."
Assert-Truthy -Condition (($report.Clip.Frames -as [int]) -gt 0) -Message "Clip.Frames must be positive."

$runtimeManifestProperty = $report.PSObject.Properties["RuntimeManifest"]
Assert-Truthy -Condition ($null -ne $runtimeManifestProperty -or -not $RequireRuntimeManifest) -Message "Missing RuntimeManifest evidence."
if ($null -ne $runtimeManifestProperty) {
    $runtimeManifest = $runtimeManifestProperty.Value
    $runtimeManifestPresent = [bool]$runtimeManifest.Present
    Assert-Truthy -Condition ($runtimeManifestPresent -or -not $RequireRuntimeManifest) -Message "RuntimeManifest.Present must be true."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$runtimeManifest.Path)) -Message "RuntimeManifest.Path is missing."
    if ($runtimeManifestPresent -and -not [string]::IsNullOrWhiteSpace([string]$runtimeManifest.Path)) {
        Assert-Truthy -Condition ([System.IO.Path]::IsPathRooted([string]$runtimeManifest.Path)) -Message "RuntimeManifest.Path must be absolute."
        Assert-Truthy -Condition (Test-Path -LiteralPath ([string]$runtimeManifest.Path) -PathType Leaf) -Message "RuntimeManifest.Path does not point to an existing file: $($runtimeManifest.Path)"
    }

    if ($runtimeManifestPresent) {
        $runtimeManifestProblems = @(
            Expand-JsonArray $runtimeManifest.Problems |
                ForEach-Object { [string]$_ } |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        )
        Assert-Truthy -Condition ($runtimeManifestProblems.Count -eq 0) -Message "RuntimeManifest.Problems must be empty: $($runtimeManifestProblems -join '; ')"

        $manifestBackends = @(
            Expand-JsonArray $runtimeManifest.RequiredRifeBackends |
                ForEach-Object { ([string]$_).Trim().ToUpperInvariant() } |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                Select-Object -Unique
        )
        $unknownManifestBackends = @($manifestBackends | Where-Object { $_ -notin $allowed })
        Assert-Truthy -Condition ($unknownManifestBackends.Count -eq 0) -Message "RuntimeManifest.RequiredRifeBackends contains unknown backend(s): $($unknownManifestBackends -join ', ')"

        foreach ($backend in $required) {
            Assert-Truthy -Condition ($backend -in $manifestBackends) -Message "RuntimeManifest.RequiredRifeBackends does not include required backend $backend."
        }
    }
}

Assert-Truthy -Condition ($null -ne $report.Host) -Message "Missing Host diagnostics."
if ($null -ne $report.Host) {
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$report.Host.PowerShellVersion)) -Message "Missing Host.PowerShellVersion."
    Assert-Truthy -Condition ($null -ne $report.Host.OperatingSystem) -Message "Missing Host.OperatingSystem."
    Assert-Truthy -Condition ((Get-JsonArray $report.Host.Processors).Count -gt 0) -Message "Host.Processors must contain at least one entry."
    Assert-Truthy -Condition ((Get-JsonArray $report.Host.VideoControllers).Count -gt 0) -Message "Host.VideoControllers must contain at least one entry."
}

$results = Get-JsonArray $report.Results
Assert-Truthy -Condition ($results.Count -gt 0) -Message "Results must contain at least one backend result."
$resultsByBackend = @{}
foreach ($result in $results) {
    $backend = ([string]$result.Backend).Trim().ToUpperInvariant()
    Assert-Truthy -Condition ($backend -in $allowed) -Message "Unknown result backend: $($result.Backend)"
    Assert-Truthy -Condition ([string]$result.Status -in @("PASS", "FAIL")) -Message "Backend $backend has invalid Status: $($result.Status)"
    $expectedScriptName = Get-RifeScriptName -BackendName $backend
    Assert-Truthy -Condition ([string]$result.ScriptName -eq $expectedScriptName) -Message "Backend $backend ScriptName must be $expectedScriptName."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.ScriptPath)) -Message "Backend $backend is missing ScriptPath."
    if (-not [string]::IsNullOrWhiteSpace([string]$result.ScriptPath)) {
        Assert-Truthy -Condition ([System.IO.Path]::IsPathRooted([string]$result.ScriptPath)) -Message "Backend $backend ScriptPath must be absolute."
    }
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.Message)) -Message "Backend $backend is missing Message."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.LogPath)) -Message "Backend $backend is missing LogPath."
    if (-not [string]::IsNullOrWhiteSpace([string]$result.LogPath)) {
        Assert-Truthy -Condition ([System.IO.Path]::IsPathRooted([string]$result.LogPath)) -Message "Backend $backend LogPath must be absolute."
    }
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.StartedAtUtc)) -Message "Backend $backend is missing StartedAtUtc."
    Assert-Truthy -Condition (-not [string]::IsNullOrWhiteSpace([string]$result.FinishedAtUtc)) -Message "Backend $backend is missing FinishedAtUtc."
    if ([string]$result.Status -eq "PASS") {
        Assert-Truthy -Condition (($result.ExitCode -as [int]) -eq 0) -Message "Backend $backend passed but ExitCode is not 0."
        if (-not [string]::IsNullOrWhiteSpace([string]$result.ScriptPath)) {
            Assert-Truthy -Condition (Test-Path -LiteralPath ([string]$result.ScriptPath) -PathType Leaf) -Message "Backend $backend passed but ScriptPath does not point to an existing file: $($result.ScriptPath)"
        }
        if (-not [string]::IsNullOrWhiteSpace([string]$result.LogPath)) {
            Assert-Truthy -Condition (Test-Path -LiteralPath ([string]$result.LogPath) -PathType Leaf) -Message "Backend $backend passed but LogPath does not point to an existing file: $($result.LogPath)"
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($backend)) {
        $resultsByBackend[$backend] = $result
    }
}

foreach ($backend in $required) {
    Assert-Truthy -Condition ($resultsByBackend.ContainsKey($backend)) -Message "Required backend $backend was not present in Results."
    if ($resultsByBackend.ContainsKey($backend) -and -not $AllowFailures) {
        Assert-Truthy -Condition ([string]$resultsByBackend[$backend].Status -eq "PASS") -Message "Required backend $backend did not PASS."
    }
}

if ($failures.Count -gt 0) {
    $summary = ($failures | ForEach-Object { " - $_" }) -join "`n"
    throw "RIFE report validation failed:`n$summary"
}

Write-Host "RIFE report validation passed: $resolvedReportPath"
Write-Host "Required backend(s): $(if ($required.Count -gt 0) { $required -join ', ' } else { '(none)' })"
Write-Host "Allow failures: $([bool]$AllowFailures)"
$results | Format-Table Backend, Status, Message -AutoSize | Out-String | Write-Host
