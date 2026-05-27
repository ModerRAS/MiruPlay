[CmdletBinding()]
param(
    [string]$MpvRuntimeSource = "runtime\mpv",
    [string]$RequiredRifeBackends = "NVIDIA,DIRECTML",
    [string]$CloudDriveEndpoint = "",
    [string]$CloudDriveToken = "",
    [string]$CloudDrivePath = "/",
    [string]$CloudRssEndpoint = "",
    [string]$CloudRssToken = "",
    [string]$CloudRssUrl = "",
    [string]$CloudRssInbox = "/Downloads",
    [string]$CloudRssLibrary = "/Library",
    [ValidateSet("msi", "exe")]
    [string]$WindowsInstallerType = "msi",
    [string]$WindowsInstallerCertPath = "",
    [string]$WindowsInstallerSignTool = "",
    [switch]$AllowUnsignedInstaller,
    [string]$ReportPath = "build\windows-port-audit\external-prereqs.json"
)

$ErrorActionPreference = "Stop"

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot ".."))
$toolsRoot = Join-Path $repoRoot "tools"

$checks = New-Object 'System.Collections.Generic.List[object]'

function Resolve-RepoRelativePath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Split-BackendList {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return @()
    }
    return @(
        $Value -split '[,;\s]+' |
            ForEach-Object { $_.Trim().ToUpperInvariant() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Select-Object -Unique
    )
}

function Get-BackendScriptName {
    param([string]$Backend)

    switch ($Backend.ToUpperInvariant()) {
        "NVIDIA" { return "MEMC_RIFE_NV.vpy" }
        "DIRECTML" { return "MEMC_RIFE_DML.vpy" }
        "STANDARD" { return "MEMC_RIFE_STD.vpy" }
        default { return "" }
    }
}

function Add-Check {
    param(
        [string]$Name,
        [string]$Category,
        [bool]$Passed,
        [string]$Message,
        [string]$Guidance,
        [hashtable]$Evidence = @{}
    )

    $checks.Add([pscustomobject]@{
        name = $Name
        category = $Category
        status = if ($Passed) { "PASS" } else { "FAIL" }
        message = $Message
        guidance = $Guidance
        evidence = [pscustomobject]$Evidence
    }) | Out-Null
}

function Add-PathCheck {
    param(
        [string]$Name,
        [string]$Category,
        [string]$Path,
        [ValidateSet("Leaf", "Container")]
        [string]$PathType,
        [string]$Guidance
    )

    $exists = Test-Path -LiteralPath $Path -PathType $PathType
    Add-Check `
        -Name $Name `
        -Category $Category `
        -Passed $exists `
        -Message $(if ($exists) { "" } else { "Missing $PathType evidence: $Path" }) `
        -Guidance $Guidance `
        -Evidence @{
            path = $Path
            expectedPathType = $PathType
            exists = $exists
        }
}

function Get-ProvidedFlag {
    param([string]$Value)

    return -not [string]::IsNullOrWhiteSpace($Value)
}

function Invoke-AssertScript {
    param(
        [string]$ScriptName,
        [string[]]$Arguments
    )

    $scriptPath = Join-Path $toolsRoot $ScriptName
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        return "Assert script was not found: $scriptPath"
    }

    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        return ($output | Out-String).Trim()
    }
    return ""
}

function Add-ReportCheck {
    param(
        [string]$Name,
        [string]$Category,
        [string]$Path,
        [string]$Guidance,
        [string]$AssertScript = "",
        [string[]]$AssertArguments = @()
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-Check `
            -Name $Name `
            -Category $Category `
            -Passed $false `
            -Message "Report was not found: $Path" `
            -Guidance $Guidance `
            -Evidence @{ path = $Path; exists = $false }
        return
    }

    $assertMessage = ""
    if (-not [string]::IsNullOrWhiteSpace($AssertScript)) {
        $assertMessage = Invoke-AssertScript -ScriptName $AssertScript -Arguments $AssertArguments
    }
    $passed = [string]::IsNullOrWhiteSpace($assertMessage)
    Add-Check `
        -Name $Name `
        -Category $Category `
        -Passed $passed `
        -Message $(if ($passed) { "" } else { $assertMessage }) `
        -Guidance $Guidance `
        -Evidence @{ path = $Path; exists = $true; assertion = $AssertScript }
}

function Add-InputCheck {
    param(
        [string]$Name,
        [string]$Category,
        [hashtable]$Inputs,
        [string]$Guidance
    )

    $missing = @(
        $Inputs.Keys |
            Where-Object { -not [bool]$Inputs[$_] } |
            Sort-Object
    )
    Add-Check `
        -Name $Name `
        -Category $Category `
        -Passed ($missing.Count -eq 0) `
        -Message $(if ($missing.Count -eq 0) { "" } else { "Missing input(s): $($missing -join ', ')" }) `
        -Guidance $Guidance `
        -Evidence @{ inputsProvided = [pscustomobject]$Inputs }
}

function Test-CommandAvailable {
    param([string]$CommandName)

    return $null -ne (Get-Command $CommandName -ErrorAction SilentlyContinue)
}

function Find-SignTool {
    param([string]$ExplicitPath)

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        $resolved = Resolve-RepoRelativePath -Path $ExplicitPath
        return [pscustomobject]@{
            provided = $true
            available = (Test-Path -LiteralPath $resolved -PathType Leaf)
            source = "explicit"
        }
    }

    return [pscustomobject]@{
        provided = $false
        available = (Test-CommandAvailable -CommandName "signtool.exe")
        source = "PATH"
    }
}

function Assert-ReportIsRedacted {
    param([string]$Path)

    $reportText = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    if ($reportText -match '(?i)Authorization|Bearer |passkey=|cloudDriveToken"\s*:|"CloudDriveToken"\s*:|cloudRssToken"\s*:|"CloudRssToken"\s*:|windowsInstallerCertPassword|BEGIN PRIVATE KEY') {
        throw "External prerequisite report appears to contain sensitive credential material: $Path"
    }
}

function Test-RuntimeManifest {
    param(
        [string]$RuntimeRoot,
        [string[]]$RequiredBackends
    )

    $manifestPath = Join-Path $RuntimeRoot "runtime-manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        Add-Check `
            -Name "Runtime manifest consistency" `
            -Category "mpv-runtime" `
            -Passed $false `
            -Message "runtime-manifest.json was not found: $manifestPath" `
            -Guidance "Prepare runtime\mpv with tools\prepare-mpv-runtime.ps1 so the payload records required RIFE backends and package-relative files." `
            -Evidence @{ path = $manifestPath; exists = $false }
        return
    }

    $failures = New-Object 'System.Collections.Generic.List[string]'
    try {
        $manifest = [System.IO.File]::ReadAllText($manifestPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
    } catch {
        $failures.Add("Manifest JSON could not be parsed: $($_.Exception.Message)") | Out-Null
        $manifest = $null
    }

    $allowedBackends = @("NVIDIA", "DIRECTML", "STANDARD")
    $manifestBackends = @()
    $manifestFiles = @()
    if ($null -ne $manifest) {
        $manifestBackends = @(
            @($manifest.requiredRifeBackends) |
                ForEach-Object { ([string]$_).Trim().ToUpperInvariant() } |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                Select-Object -Unique
        )
        foreach ($backend in $manifestBackends) {
            if ($backend -notin $allowedBackends) {
                $failures.Add("Manifest contains unknown RIFE backend: $backend") | Out-Null
            }
        }
        foreach ($backend in $RequiredBackends) {
            if ($backend -notin $manifestBackends) {
                $failures.Add("Manifest does not declare required RIFE backend: $backend") | Out-Null
            }
        }

        $manifestFiles = @(
            @($manifest.files) |
                ForEach-Object { [string]$_ } |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        )
        foreach ($entry in $manifestFiles) {
            $normalized = $entry.Replace('\', '/')
            $withoutSlash = $normalized.TrimEnd('/')
            if ([System.IO.Path]::IsPathRooted($normalized) -or $normalized.Contains("..")) {
                $failures.Add("Manifest files contains invalid package-relative entry: $entry") | Out-Null
                continue
            }
            $candidate = Join-Path $RuntimeRoot ($withoutSlash.Replace('/', '\'))
            $isDirectory = $normalized.EndsWith("/")
            if ($isDirectory) {
                if (-not (Test-Path -LiteralPath $candidate -PathType Container)) {
                    $failures.Add("Manifest directory entry is missing: $entry") | Out-Null
                }
            } else {
                if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
                    $failures.Add("Manifest file entry is missing: $entry") | Out-Null
                }
            }
        }
    }

    Add-Check `
        -Name "Runtime manifest consistency" `
        -Category "mpv-runtime" `
        -Passed ($failures.Count -eq 0) `
        -Message $(if ($failures.Count -eq 0) { "" } else { $failures -join "; " }) `
        -Guidance "Regenerate runtime-manifest.json with tools\prepare-mpv-runtime.ps1 after the mpv/RIFE payload is prepared." `
        -Evidence @{
            path = $manifestPath
            exists = $true
            requiredRifeBackends = $RequiredBackends
            manifestRifeBackends = $manifestBackends
            manifestFileCount = $manifestFiles.Count
        }
}

$requiredBackends = Split-BackendList -Value $RequiredRifeBackends
$runtimeRoot = Resolve-RepoRelativePath -Path $MpvRuntimeSource
$cloudRssEffectiveEndpoint = if ([string]::IsNullOrWhiteSpace($CloudRssEndpoint)) { $CloudDriveEndpoint } else { $CloudRssEndpoint }
$cloudRssEffectiveToken = if ([string]::IsNullOrWhiteSpace($CloudRssToken)) { $CloudDriveToken } else { $CloudRssToken }

Add-PathCheck `
    -Name "mpv runtime root" `
    -Category "mpv-runtime" `
    -Path $runtimeRoot `
    -PathType "Container" `
    -Guidance "Prepare runtime\mpv locally or pass -MpvRuntimeSource to point at a prepared runtime payload."
Add-PathCheck `
    -Name "mpv.exe payload" `
    -Category "mpv-runtime" `
    -Path (Join-Path $runtimeRoot "mpv.exe") `
    -PathType "Leaf" `
    -Guidance "Run tools\prepare-mpv-runtime.ps1 with the mpv_PlayKit base payload before running runtime, RIFE, or installer gates."
Add-PathCheck `
    -Name "portable_config payload" `
    -Category "mpv-runtime" `
    -Path (Join-Path $runtimeRoot "portable_config") `
    -PathType "Container" `
    -Guidance "Overlay a complete mpv_PlayKit portable_config directory before claiming bundled runtime evidence."
Add-PathCheck `
    -Name "runtime-manifest.json payload" `
    -Category "mpv-runtime" `
    -Path (Join-Path $runtimeRoot "runtime-manifest.json") `
    -PathType "Leaf" `
    -Guidance "Use tools\prepare-mpv-runtime.ps1 so runtime provenance and required backend files are recorded."

foreach ($backend in $requiredBackends) {
    $scriptName = Get-BackendScriptName -Backend $backend
    if ([string]::IsNullOrWhiteSpace($scriptName)) {
        Add-Check `
            -Name "RIFE backend script $backend" `
            -Category "mpv-runtime" `
            -Passed $false `
            -Message "Unknown required RIFE backend: $backend" `
            -Guidance "Use NVIDIA, DIRECTML, or STANDARD in -RequiredRifeBackends." `
            -Evidence @{ backend = $backend }
    } else {
        Add-PathCheck `
            -Name "RIFE backend script $backend" `
            -Category "mpv-runtime" `
            -Path (Join-Path $runtimeRoot "portable_config\vs\$scriptName") `
            -PathType "Leaf" `
            -Guidance "Prepare or overlay the RIFE backend payload that contains portable_config\vs\$scriptName."
    }
}
Test-RuntimeManifest -RuntimeRoot $runtimeRoot -RequiredBackends $requiredBackends

$rifeReportPath = Resolve-RepoRelativePath -Path "build\mpv-smoke\rife-matrix-report.json"
Add-ReportCheck `
    -Name "Target-host RIFE matrix report" `
    -Category "rife-target" `
    -Path $rifeReportPath `
    -Guidance "Run tools\verify-windows-port.ps1 -Rife -RifeBackend ALL -RequiredRifeReportBackends $($requiredBackends -join ',') on target Windows hardware." `
    -AssertScript "assert-mpv-rife-report.ps1" `
    -AssertArguments @(
        "-ReportPath",
        $rifeReportPath,
        "-RequiredBackends",
        ($requiredBackends -join ","),
        "-RequireRuntimeManifest"
    )

Add-InputCheck `
    -Name "CloudDrive2 live smoke inputs" `
    -Category "cloud-drive" `
    -Inputs @{
        endpoint = (Get-ProvidedFlag -Value $CloudDriveEndpoint)
        token = (Get-ProvidedFlag -Value $CloudDriveToken)
        path = (Get-ProvidedFlag -Value $CloudDrivePath)
    } `
    -Guidance "Pass -CloudDriveEndpoint, -CloudDriveToken, and -CloudDrivePath only for a real CloudDrive2 test server."
$cloudDriveReportPath = Resolve-RepoRelativePath -Path "build\cloud-drive-smoke\cloud-drive-report.json"
Add-ReportCheck `
    -Name "CloudDrive2 live report" `
    -Category "cloud-drive" `
    -Path $cloudDriveReportPath `
    -Guidance "Run tools\verify-windows-port.ps1 -CloudDrive against a real server with offline-download permission." `
    -AssertScript "assert-cloud-drive-report.ps1" `
    -AssertArguments @(
        "-ReportPath",
        $cloudDriveReportPath,
        "-RequiredPath",
        $CloudDrivePath,
        "-RequireOfflinePermission"
    )

Add-InputCheck `
    -Name "CloudDrive RSS smoke inputs" `
    -Category "cloud-rss" `
    -Inputs @{
        endpoint = (Get-ProvidedFlag -Value $cloudRssEffectiveEndpoint)
        token = (Get-ProvidedFlag -Value $cloudRssEffectiveToken)
        rssUrl = (Get-ProvidedFlag -Value $CloudRssUrl)
        inbox = (Get-ProvidedFlag -Value $CloudRssInbox)
        library = (Get-ProvidedFlag -Value $CloudRssLibrary)
    } `
    -Guidance "Pass endpoint/token/RSS/inbox/library for real dry-run, live-submit, and organize RSS evidence."

$cloudRssReports = @(
    @{
        name = "CloudDrive RSS dry-run report"
        path = "build\cloud-rss-smoke\dry-run-report.json"
        args = @("-RequireCandidates", "-RequireOfflinePermission")
        guidance = "Run tools\verify-windows-port.ps1 -CloudRssDryRun with -RequireCloudRssCandidates."
    },
    @{
        name = "CloudDrive RSS live-submit report"
        path = "build\cloud-rss-smoke\live-submit-report.json"
        args = @("-RequireCandidates", "-RequireLiveSubmit", "-RequireOfflinePermission")
        guidance = "Run tools\verify-windows-port.ps1 -CloudRssLiveSubmit -ConfirmCloudRssLiveSubmit against the real test inbox."
    },
    @{
        name = "CloudDrive RSS organize report"
        path = "build\cloud-rss-smoke\organize-report.json"
        args = @("-RequireOrganize", "-RequireOfflinePermission")
        guidance = "Run tools\verify-windows-port.ps1 -CloudRssOrganize -ConfirmCloudRssOrganize after live-submit evidence exists."
    }
)
foreach ($report in $cloudRssReports) {
    $resolvedPath = Resolve-RepoRelativePath -Path ([string]$report.path)
    Add-ReportCheck `
        -Name ([string]$report.name) `
        -Category "cloud-rss" `
        -Path $resolvedPath `
        -Guidance ([string]$report.guidance) `
        -AssertScript "assert-cloud-rss-report.ps1" `
        -AssertArguments (@(
            "-ReportPath",
            $resolvedPath,
            "-RequiredInbox",
            $CloudRssInbox,
            "-RequiredLibrary",
            $CloudRssLibrary
        ) + [string[]]$report.args)
}

$wixCandleAvailable = Test-CommandAvailable -CommandName "candle.exe"
$wixLightAvailable = Test-CommandAvailable -CommandName "light.exe"
Add-Check `
    -Name "WiX installer toolchain" `
    -Category "windows-installer" `
    -Passed ($wixCandleAvailable -and $wixLightAvailable) `
    -Message $(if ($wixCandleAvailable -and $wixLightAvailable) { "" } else { "candle.exe and light.exe must both be available on PATH." }) `
    -Guidance "Install WiX Toolset and ensure candle.exe/light.exe are on PATH before running -WindowsInstaller." `
    -Evidence @{
        installerType = $WindowsInstallerType
        candleOnPath = $wixCandleAvailable
        lightOnPath = $wixLightAvailable
    }

if (-not $AllowUnsignedInstaller) {
    $signTool = Find-SignTool -ExplicitPath $WindowsInstallerSignTool
    $certProvided = -not [string]::IsNullOrWhiteSpace($WindowsInstallerCertPath)
    $certExists = $false
    if ($certProvided) {
        $certExists = Test-Path -LiteralPath (Resolve-RepoRelativePath -Path $WindowsInstallerCertPath) -PathType Leaf
    }
    Add-Check `
        -Name "Windows installer signing inputs" `
        -Category "windows-installer" `
        -Passed ([bool]$signTool.available -and $certProvided -and $certExists) `
        -Message $(if ([bool]$signTool.available -and $certProvided -and $certExists) { "" } else { "Signed release evidence requires signtool.exe and an existing PFX certificate path." }) `
        -Guidance "Pass -WindowsInstallerSignTool and -WindowsInstallerCertPath for signed release evidence, or use -AllowUnsignedInstaller only for local QA." `
        -Evidence @{
            signtoolAvailable = [bool]$signTool.available
            signtoolSource = [string]$signTool.source
            certPathProvided = $certProvided
            certPathExists = $certExists
        }
}

$installerReportPath = Resolve-RepoRelativePath -Path "desktop-app\build\jpackage\smoke\windows-installer-smoke.json"
$installerAssertArguments = @(
    "-ReportPath",
    $installerReportPath,
    "-RequiredInstallerType",
    $WindowsInstallerType
)
if ($AllowUnsignedInstaller) {
    $installerAssertArguments += "-RequireUnsigned"
} else {
    $installerAssertArguments += "-RequireSigned"
}
$installerAssertArguments += "-RequireBundledMpvRuntime"
Add-ReportCheck `
    -Name "Windows installer report" `
    -Category "windows-installer" `
    -Path $installerReportPath `
    -Guidance "Run tools\verify-windows-port.ps1 -WindowsInstaller -SignWindowsInstaller with WiX, signtool, release signing inputs, and a bundled-runtime installer strategy." `
    -AssertScript "assert-windows-installer-report.ps1" `
    -AssertArguments $installerAssertArguments

$failCount = @($checks | Where-Object { $_.status -ne "PASS" }).Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
$resolvedReportPath = Resolve-RepoRelativePath -Path $ReportPath
$reportDirectory = Split-Path -Parent $resolvedReportPath
if (-not [string]::IsNullOrWhiteSpace($reportDirectory) -and -not (Test-Path -LiteralPath $reportDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
}

$report = [pscustomobject]@{
    schemaVersion = 1
    name = "windows-port-external-prereq-audit"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    status = if ($failCount -eq 0) { "ready" } else { "blocked" }
    requiredRifeBackends = $requiredBackends
    mpvRuntimeSource = $runtimeRoot
    windowsInstallerType = $WindowsInstallerType
    requiresSignedInstaller = -not [bool]$AllowUnsignedInstaller
    summary = [pscustomobject]@{
        passCount = $passCount
        failCount = $failCount
    }
    checks = @($checks.ToArray())
}
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resolvedReportPath -Encoding UTF8
Assert-ReportIsRedacted -Path $resolvedReportPath

Write-Host "Wrote external prerequisite audit report: $resolvedReportPath"
Write-Host "External prerequisite status: $($report.status) ($passCount pass, $failCount fail)"
if ($failCount -gt 0) {
    foreach ($check in ($checks | Where-Object { $_.status -ne "PASS" })) {
        Write-Host (" - {0}: {1}" -f $check.name, $check.message)
        Write-Host ("   Next: {0}" -f $check.guidance)
    }
    throw "Windows port external prerequisites are incomplete: $failCount check(s) failed."
}

Write-Host "Windows port external prerequisites are ready."
