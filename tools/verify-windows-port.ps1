[CmdletBinding()]
param(
    [string]$Gradle = "",
    [switch]$SkipGradle,
    [switch]$SkipAndroidBuild,
    [switch]$Gui,
    [switch]$RealLibrary,
    [string]$RealLibraryRoot = "D:\Software\dufs",
    [switch]$AndroidTv,
    [string]$AndroidDeviceId = "10.137.32.118:5555",
    [switch]$Smb,
    [switch]$MpvRuntime,
    [switch]$PackagedMpvRuntime,
    [string]$MpvRuntimeSource = "runtime\mpv",
    [string]$RequiredRifeBackends = "NVIDIA,DIRECTML",
    [switch]$Rife,
    [ValidateSet("NVIDIA", "DIRECTML", "STANDARD", "ALL")]
    [string]$RifeBackend = "DIRECTML",
    [string]$RequiredRifeReportBackends = "",
    [switch]$AllowRifeFailures
)

$ErrorActionPreference = "Stop"

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot ".."))
$toolsRoot = Join-Path $repoRoot "tools"
if ([string]::IsNullOrWhiteSpace($Gradle)) {
    $Gradle = Join-Path $repoRoot "gradlew.bat"
}

# Build the approved SMB smoke path without non-ASCII source literals.
$temporaryFilesSegment = -join @(
    [char]0x4E34,
    [char]0x65F6,
    [char]0x6587,
    [char]0x4EF6
)
$testSegment = -join @(
    [char]0x6D4B,
    [char]0x8BD5
)
$approvedSmbShareTestPath = "\\smb.ynz.local\share\$temporaryFilesSegment\$testSegment"
$approvedSmbBaseUrl = "smb://smb.ynz.local/share/$temporaryFilesSegment/$testSegment"
$stepResults = New-Object 'System.Collections.Generic.List[object]'

function Get-JavaMajorVersion {
    param([string]$JavaHome)

    if ([string]::IsNullOrWhiteSpace($JavaHome)) {
        return $null
    }
    $javaExe = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
        return $null
    }

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $javaExe
    $processInfo.Arguments = "-version"
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $process = [System.Diagnostics.Process]::Start($processInfo)
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $versionText = (@($standardOutput, $standardError) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join "`n"
    if ($versionText -match 'version "(\d+)(?:\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[2]) {
            return [int]$Matches[2]
        }
        return $major
    }
    return $null
}

function Get-Jdk21HomeCandidates {
    $candidates = New-Object 'System.Collections.Generic.List[string]'
    foreach ($path in @($env:JAVA21_HOME, $env:JDK21_HOME, $env:JAVA_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            $candidates.Add($path) | Out-Null
        }
    }

    $userProfilePath = [Environment]::GetFolderPath("UserProfile")
    if (-not [string]::IsNullOrWhiteSpace($userProfilePath)) {
        foreach ($relative in @(
            "scoop\apps\temurin21-jdk\current",
            "scoop\apps\openjdk21\current",
            ".jdks\temurin-21"
        )) {
            $candidates.Add((Join-Path $userProfilePath $relative)) | Out-Null
        }
    }

    foreach ($root in @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Microsoft"
    )) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match "21" } |
                ForEach-Object { $candidates.Add($_.FullName) | Out-Null }
        }
    }

    return @($candidates | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
}

function Use-Jdk21 {
    $currentMajor = Get-JavaMajorVersion -JavaHome $env:JAVA_HOME
    if ($currentMajor -eq 21) {
        Write-Host "Using JDK 21: $env:JAVA_HOME"
        return
    }

    foreach ($candidate in Get-Jdk21HomeCandidates) {
        $major = Get-JavaMajorVersion -JavaHome $candidate
        if ($major -eq 21) {
            $env:JAVA_HOME = [System.IO.Path]::GetFullPath($candidate)
            $env:Path = (Join-Path $env:JAVA_HOME "bin") + [System.IO.Path]::PathSeparator + $env:Path
            Write-Host "Using JDK 21: $env:JAVA_HOME"
            return
        }
    }

    $currentText = if ($currentMajor) { "JDK $currentMajor" } else { "no valid JAVA_HOME" }
    throw "MiruPlay verification requires JDK 21, but the current shell has $currentText. Set JAVA21_HOME, JDK21_HOME, or JAVA_HOME to a JDK 21 installation."
}

function Format-Duration {
    param([TimeSpan]$Duration)
    if ($Duration.TotalMinutes -ge 1) {
        return "{0:n1}m" -f $Duration.TotalMinutes
    }
    return "{0:n1}s" -f $Duration.TotalSeconds
}

function Add-StepResult {
    param(
        [string]$Name,
        [string]$Status,
        [TimeSpan]$Duration
    )

    $stepResults.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Duration = $Duration
    }) | Out-Null
}

function Write-StepSummary {
    if ($stepResults.Count -eq 0) {
        return
    }

    Write-Host ""
    Write-Host "Verification summary:"
    foreach ($result in $stepResults) {
        Write-Host (" - {0}: {1} ({2})" -f $result.Status, $result.Name, (Format-Duration $result.Duration))
    }
}

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host ""
    Write-Host "==> $Name"
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        & $Action
        $stopwatch.Stop()
        Add-StepResult -Name $Name -Status "PASS" -Duration $stopwatch.Elapsed
        Write-Host ("PASS: {0} ({1})" -f $Name, (Format-Duration $stopwatch.Elapsed))
    } catch {
        $stopwatch.Stop()
        Add-StepResult -Name $Name -Status "FAIL" -Duration $stopwatch.Elapsed
        Write-Host ("FAIL: {0} ({1})" -f $Name, (Format-Duration $stopwatch.Elapsed))
        throw
    }
}

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Invoke-Gradle {
    param([string[]]$Arguments)
    Invoke-Native -FilePath $Gradle -Arguments $Arguments
}

function Invoke-ToolScript {
    param(
        [string]$ScriptName,
        [string[]]$Arguments = @()
    )

    $scriptPath = Join-Path $toolsRoot $ScriptName
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        throw "Tool script not found: $scriptPath"
    }
    Invoke-Native -FilePath "powershell.exe" -Arguments (@(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $scriptPath
    ) + $Arguments)
}

$defaultGradleTasks = @(
    "checkDesktopComposeOnly",
    "checkDesktopPresenterSeparation",
    "checkUiPaletteDrift",
    ":core:model:test",
    ":repository-api:test",
    ":cloud-drive-api:test",
    ":sync-engine-shared:test",
    ":media-source-desktop:test",
    ":scanner-desktop:test",
    ":repository-desktop:test",
    ":scraper-desktop:test",
    ":player-mpv:test",
    ":cloud-drive-desktop:test",
    ":sync-engine-desktop:test",
    ":desktop-app:test",
    ":desktop-app:installDist",
    "-PbundleMpvRuntime=false"
)
if (-not $SkipAndroidBuild) {
    $defaultGradleTasks = @(":app:assembleDebug") + $defaultGradleTasks
}

Push-Location $repoRoot
try {
    Use-Jdk21

    if (-not $SkipGradle) {
        Invoke-Step -Name "Safe Gradle gate" -Action {
            Invoke-Gradle -Arguments $defaultGradleTasks
        }
    } else {
        Write-Host "Skipping safe Gradle gate because -SkipGradle was supplied."
    }

    if ($MpvRuntime) {
        Invoke-Step -Name "mpv runtime smoke" -Action {
            Invoke-Gradle -Arguments @(
                ":desktop-app:smokeMpvRuntime",
                "-PmpvRuntimeSource=$MpvRuntimeSource",
                "-PrequireMpvRuntime=true",
                "-PrequiredRifeBackends=$RequiredRifeBackends"
            )
        }
    }

    if ($PackagedMpvRuntime) {
        Invoke-Step -Name "packaged mpv runtime smoke" -Action {
            Invoke-Gradle -Arguments @(
                ":desktop-app:smokePackagedMpvRuntime",
                "-PmpvRuntimeSource=$MpvRuntimeSource",
                "-PrequireMpvRuntime=true",
                "-PrequiredRifeBackends=$RequiredRifeBackends"
            )
        }
    }

    if ($Gui) {
        Invoke-Step -Name "desktop screenshot QA" -Action {
            Invoke-ToolScript -ScriptName "capture-desktop-ui.ps1"
        }
        Invoke-Step -Name "desktop keyboard focus GUI smoke" -Action {
            Invoke-ToolScript -ScriptName "smoke-desktop-keyboard-focus-ui.ps1"
        }
        Invoke-Step -Name "desktop local source GUI smoke" -Action {
            Invoke-ToolScript -ScriptName "smoke-desktop-local-source-ui.ps1"
        }
        Invoke-Step -Name "desktop source management GUI smoke" -Action {
            Invoke-ToolScript -ScriptName "smoke-desktop-source-management-ui.ps1"
        }
        Invoke-Step -Name "desktop WebDAV GUI smoke" -Action {
            Invoke-ToolScript -ScriptName "smoke-desktop-webdav-source-ui.ps1"
        }
        Invoke-Step -Name "desktop Bangumi GUI smoke" -Action {
            Invoke-ToolScript -ScriptName "smoke-desktop-bangumi-metadata-ui.ps1"
        }
        Invoke-Step -Name "desktop mpv launch GUI smoke" -Action {
            Invoke-ToolScript -ScriptName "smoke-desktop-mpv-launch-ui.ps1"
        }
    }

    if ($RealLibrary) {
        Invoke-Step -Name "real local library GUI smoke" -Action {
            Invoke-ToolScript -ScriptName "smoke-desktop-local-source-ui.ps1" -Arguments @(
                "-LibraryRoot",
                $RealLibraryRoot
            )
        }
    }

    if ($Smb) {
        Invoke-Step -Name "approved SMB GUI smoke" -Action {
            Invoke-ToolScript -ScriptName "smoke-desktop-smb-source-ui.ps1" -Arguments @(
                "-ShareTestPath",
                $approvedSmbShareTestPath,
                "-SmbBaseUrl",
                $approvedSmbBaseUrl
            )
        }
    } else {
        Write-Host "SMB smoke skipped. It only runs with -Smb and is restricted to the approved test directory."
    }

    if ($AndroidTv) {
        Invoke-Step -Name "Android TV emulator smoke" -Action {
            Invoke-Native -FilePath "adb" -Arguments @("connect", $AndroidDeviceId)
            Invoke-ToolScript -ScriptName "smoke-android-tv-ui.ps1" -Arguments @(
                "-DeviceId",
                $AndroidDeviceId
            )
        }
    }

    if ($Rife) {
        Invoke-Step -Name "RIFE target-hardware smoke" -Action {
            $reportName = if ($RifeBackend -eq "ALL") { "rife-matrix-report.json" } else { "rife-$($RifeBackend.ToLowerInvariant())-report.json" }
            $reportPath = Join-Path $repoRoot "build\mpv-smoke\$reportName"
            $rifeArgs = @(
                "-Backend",
                $RifeBackend,
                "-ReportPath",
                $reportPath
            )
            if ($AllowRifeFailures) {
                $rifeArgs += "-AllowFailures"
            }
            Invoke-ToolScript -ScriptName "smoke-mpv-rife.ps1" -Arguments $rifeArgs

            $requiredReportBackends = if ([string]::IsNullOrWhiteSpace($RequiredRifeReportBackends)) {
                if ($RifeBackend -eq "ALL") {
                    $RequiredRifeBackends
                } else {
                    $RifeBackend
                }
            } else {
                $RequiredRifeReportBackends
            }
            $assertArgs = @(
                "-ReportPath",
                $reportPath,
                "-RequiredBackends",
                $requiredReportBackends
            )
            if ($AllowRifeFailures) {
                $assertArgs += "-AllowFailures"
            }
            Invoke-ToolScript -ScriptName "assert-mpv-rife-report.ps1" -Arguments $assertArgs
        }
    } else {
        Write-Host "RIFE smoke skipped. Run with -Rife only on target hardware expected to support interpolation."
    }
} finally {
    Pop-Location
    Write-StepSummary
}
