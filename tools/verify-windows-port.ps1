[CmdletBinding()]
param(
    [string]$Gradle = "",
    [switch]$SkipGradle,
    [switch]$SkipAndroidBuild,
    [switch]$Gui,
    [switch]$Behavior,
    [ValidateSet("smoke", "full")]
    [string]$BehaviorTags = "smoke",
    [switch]$RealLibrary,
    [string]$RealLibraryRoot = "D:\Software\dufs",
    [switch]$AndroidTv,
    [string]$AndroidDeviceId = "",
    [switch]$KeepAndroidAppData,
    [switch]$Smb,
    [switch]$MpvRuntime,
    [switch]$PackagedMpvRuntime,
    [switch]$NativeAppImage,
    [switch]$WindowsInstaller,
    [ValidateSet("msi", "exe")]
    [string]$WindowsInstallerType = "msi",
    [switch]$SignWindowsInstaller,
    [string]$WindowsInstallerCertPath = "",
    [string]$WindowsInstallerCertPassword = "",
    [string]$WindowsInstallerSignTool = "",
    [string]$WindowsInstallerTimestampUrl = "",
    [string]$WindowsPackageVersion = "",
    [string]$WindowsInstallerUpgradeUuid = "",
    [string]$MpvRuntimeSource = "runtime\mpv",
    [string]$RequiredRifeBackends = "NVIDIA,DIRECTML",
    [switch]$Rife,
    [ValidateSet("NVIDIA", "DIRECTML", "STANDARD", "ALL")]
    [string]$RifeBackend = "DIRECTML",
    [string]$RequiredRifeReportBackends = "",
    [switch]$AllowRifeFailures,
    [switch]$CloudDrive,
    [string]$CloudDriveEndpoint = "",
    [string]$CloudDriveToken = "",
    [string]$CloudDrivePath = "/",
    [switch]$RequireCloudDriveOfflinePermission,
    [switch]$CloudRssDryRun,
    [switch]$CloudRssLiveSubmit,
    [switch]$CloudRssOrganize,
    [switch]$CloudRssEvidenceBundle,
    [switch]$ConfirmCloudRssLiveSubmit,
    [switch]$ConfirmCloudRssOrganize,
    [string]$CloudRssEndpoint = "",
    [string]$CloudRssToken = "",
    [string]$CloudRssUrl = "",
    [string]$CloudRssInbox = "/Downloads",
    [string]$CloudRssLibrary = "/Library",
    [string]$CloudRssFilter = "",
    [int]$CloudRssSubmitLimit = 1,
    [switch]$RequireCloudRssCandidates,
    [switch]$CloudRssScheduler,
    [switch]$SkipCloudRssScheduler,
    [int]$CloudRssSchedulerDurationMs = 2000,
    [int]$CloudRssSchedulerCheckIntervalMs = 250,
    [int]$CloudRssSchedulerRunAfterChecks = 2,
    [switch]$ExternalPrereqAudit,
    [string]$ExternalPrereqReportPath = "build\windows-port-audit\external-prereqs.json",
    [switch]$CompletionAudit,
    [switch]$AllowUnsignedCompletionInstaller,
    [string]$CompletionAuditReportPath = "build\windows-port-audit\completion-audit.json"
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
$liveSubmitConfirmation = "I_UNDERSTAND_THIS_SUBMITS_REAL_CLOUDDRIVE_DOWNLOADS"
$liveOrganizeConfirmation = "I_UNDERSTAND_THIS_MOVES_REAL_CLOUDDRIVE_FILES"

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
        throw "$FilePath $(Format-CommandArgumentsForLog -Arguments $Arguments) failed with exit code $LASTEXITCODE."
    }
}

function Format-CommandArgumentsForLog {
    param([string[]]$Arguments = @())

    $secretNames = @(
        "windowsInstallerCertPassword",
        "CloudDriveEndpoint",
        "CloudDriveToken",
        "CloudRssEndpoint",
        "CloudRssToken",
        "CloudRssUrl",
        "cloudDriveEndpoint",
        "cloudDriveToken",
        "cloudDriveRssEndpoint",
        "cloudDriveRssToken",
        "cloudDriveRssUrl",
        "smbPassword"
    )

    $redacted = New-Object 'System.Collections.Generic.List[string]'
    for ($i = 0; $i -lt $Arguments.Count; $i++) {
        $argument = $Arguments[$i]
        $matchedSeparatedSecret = $false
        foreach ($name in $secretNames) {
            if ($argument -match "^-P$name=") {
                $redacted.Add("-P$name=<redacted>") | Out-Null
                $matchedSeparatedSecret = $true
                break
            }
            if ($argument -match "^-$name$") {
                $redacted.Add("-$name") | Out-Null
                if ($i + 1 -lt $Arguments.Count) {
                    $redacted.Add("<redacted>") | Out-Null
                    $i += 1
                }
                $matchedSeparatedSecret = $true
                break
            }
        }
        if (-not $matchedSeparatedSecret) {
            $redacted.Add($argument) | Out-Null
        }
    }
    return ($redacted.ToArray() -join ' ')
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

function Invoke-CompletionToolScript {
    param(
        [string]$ScriptName,
        [string[]]$Arguments = @()
    )

    $scriptPath = Join-Path $toolsRoot $ScriptName
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        throw "Tool script not found: $scriptPath"
    }
    $argumentSplat = @{}
    for ($index = 0; $index -lt $Arguments.Count; $index++) {
        $argumentName = $Arguments[$index]
        if (-not $argumentName.StartsWith("-")) {
            throw "Completion assertion argument must be named: $argumentName"
        }
        $key = $argumentName.TrimStart("-")
        if (($index + 1) -lt $Arguments.Count -and -not $Arguments[$index + 1].StartsWith("-")) {
            $argumentSplat[$key] = $Arguments[$index + 1]
            $index++
        } else {
            $argumentSplat[$key] = $true
        }
    }
    try {
        & $scriptPath @argumentSplat
    } catch {
        $details = $_.Exception.Message
        if ([string]::IsNullOrWhiteSpace($details)) {
            $details = [string]$_
        }
        throw "$ScriptName failed.`n$details"
    }
}

function Invoke-CloudRssSmokeAndAssert {
    param(
        [string]$StepName,
        [bool]$LiveSubmitEnabled,
        [bool]$OrganizeEnabled,
        [string]$RssEndpoint,
        [string]$RssToken
    )

    Invoke-Step -Name $StepName -Action {
        if ([string]::IsNullOrWhiteSpace($RssEndpoint) -or [string]::IsNullOrWhiteSpace($RssToken) -or [string]::IsNullOrWhiteSpace($CloudRssUrl)) {
            throw "CloudDrive RSS smoke requires endpoint, token, and -CloudRssUrl. You can pass endpoint/token with -CloudRssEndpoint/-CloudRssToken or reuse -CloudDriveEndpoint/-CloudDriveToken."
        }
        if ($LiveSubmitEnabled -and -not $ConfirmCloudRssLiveSubmit) {
            throw "CloudDrive RSS live submit requires -ConfirmCloudRssLiveSubmit because it submits real offline downloads."
        }
        if ($OrganizeEnabled -and -not $ConfirmCloudRssOrganize) {
            throw "CloudDrive RSS organize requires -ConfirmCloudRssOrganize because it moves real CloudDrive files."
        }

        $reportName = if ($LiveSubmitEnabled) {
            "live-submit-report.json"
        } elseif ($OrganizeEnabled) {
            "organize-report.json"
        } else {
            "dry-run-report.json"
        }
        $reportPath = Join-Path $repoRoot "build\cloud-rss-smoke\$reportName"
        $taskName = if ($LiveSubmitEnabled) { ":sync-engine-desktop:smokeCloudDriveRssLiveSubmit" } else { ":sync-engine-desktop:smokeCloudDriveRssDryRun" }
        $gradleArgs = @(
            $taskName,
            "-PcloudDriveEndpoint=$RssEndpoint",
            "-PcloudDriveToken=$RssToken",
            "-PcloudDriveRssUrl=$CloudRssUrl",
            "-PcloudDriveInbox=$CloudRssInbox",
            "-PcloudDriveLibrary=$CloudRssLibrary",
            "-PcloudDriveRssReportPath=$reportPath"
        )
        if (-not [string]::IsNullOrWhiteSpace($CloudRssFilter)) {
            $gradleArgs += "-PcloudDriveRssFilter=$CloudRssFilter"
        }
        if ($LiveSubmitEnabled) {
            $gradleArgs += "-PcloudDriveRssSubmitConfirmation=$liveSubmitConfirmation"
            $gradleArgs += "-PcloudDriveRssSubmitLimit=$CloudRssSubmitLimit"
        }
        if ($OrganizeEnabled) {
            $gradleArgs += "-PcloudDriveRssOrganize=true"
            $gradleArgs += "-PcloudDriveRssOrganizeConfirmation=$liveOrganizeConfirmation"
        }
        Invoke-Gradle -Arguments $gradleArgs

        $assertArgs = @(
            "-ReportPath",
            $reportPath,
            "-RequiredInbox",
            $CloudRssInbox,
            "-RequiredLibrary",
            $CloudRssLibrary
        )
        if ($RequireCloudRssCandidates -or $LiveSubmitEnabled) {
            $assertArgs += "-RequireCandidates"
        }
        if ($LiveSubmitEnabled) {
            $assertArgs += "-RequireLiveSubmit"
        }
        if ($OrganizeEnabled) {
            $assertArgs += "-RequireOrganize"
        }
        if ($RequireCloudDriveOfflinePermission -or $LiveSubmitEnabled) {
            $assertArgs += "-RequireOfflinePermission"
        }
        Invoke-ToolScript -ScriptName "assert-cloud-rss-report.ps1" -Arguments $assertArgs
    }
}

function Resolve-RepoRelativePath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Get-CompletionLatestReportPath {
    param(
        [string]$PointerPath,
        [string]$EvidenceName
    )

    $resolvedPointerPath = Resolve-RepoRelativePath -Path $PointerPath
    if (-not (Test-Path -LiteralPath $resolvedPointerPath -PathType Leaf)) {
        throw "$EvidenceName latest-report pointer was not found: $resolvedPointerPath"
    }
    $reportPath = [System.IO.File]::ReadAllText($resolvedPointerPath, [System.Text.Encoding]::UTF8).Trim()
    if ([string]::IsNullOrWhiteSpace($reportPath)) {
        throw "$EvidenceName latest-report pointer is empty: $resolvedPointerPath"
    }
    return Resolve-RepoRelativePath -Path $reportPath
}

function Add-CompletionProblem {
    param(
        [System.Collections.Generic.List[object]]$Problems,
        [string]$Name,
        [string]$Message,
        [string]$Guidance
    )

    $Problems.Add([pscustomobject]@{
        Name = $Name
        Message = $Message
        Guidance = $Guidance
    }) | Out-Null
}

function Invoke-CompletionEvidenceCheck {
    param(
        [System.Collections.Generic.List[object]]$Problems,
        [System.Collections.Generic.List[object]]$Results,
        [string]$Name,
        [string]$Guidance,
        [scriptblock]$Action
    )

    try {
        & $Action
        $Results.Add([pscustomobject]@{
            Name = $Name
            Status = "PASS"
            Message = ""
            Guidance = $Guidance
        }) | Out-Null
        Write-Host "Completion evidence present: $Name"
    } catch {
        $message = $_.Exception.Message
        $Results.Add([pscustomobject]@{
            Name = $Name
            Status = "FAIL"
            Message = $message
            Guidance = $Guidance
        }) | Out-Null
        Add-CompletionProblem -Problems $Problems -Name $Name -Message $message -Guidance $Guidance
    }
}

function Invoke-WindowsPortCompletionAudit {
    $problems = New-Object 'System.Collections.Generic.List[object]'
    $results = New-Object 'System.Collections.Generic.List[object]'
    $requiredRifeBackends = if ([string]::IsNullOrWhiteSpace($RequiredRifeReportBackends)) {
        $RequiredRifeBackends
    } else {
        $RequiredRifeReportBackends
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "Packaged WebUI smoke report" -Guidance "Run .\tools\verify-windows-port.ps1 without -SkipGradle, then keep desktop-app\build\web-control-smoke\desktop-web-control-smoke.json with the release evidence." -Action {
        Invoke-CompletionToolScript -ScriptName "assert-desktop-web-control-smoke-report.ps1" -Arguments @(
            "-ReportPath",
            (Join-Path $repoRoot "desktop-app\build\web-control-smoke\desktop-web-control-smoke.json")
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "Bangumi live scraper report" -Guidance "Run .\gradlew.bat :scraper-desktop:test :scraper-desktop:smokeBangumiLive -PbangumiSmokeReportPath=build\bangumi-smoke\live-report.json, then assert it with tools\assert-bangumi-live-report.ps1." -Action {
        Invoke-CompletionToolScript -ScriptName "assert-bangumi-live-report.ps1" -Arguments @(
            "-ReportPath",
            (Join-Path $repoRoot "build\bangumi-smoke\live-report.json")
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "Full desktop behavior report" -Guidance "Run .\tools\verify-windows-port.ps1 -Behavior -BehaviorTags full on Windows and keep build\desktop-behavior\latest-report.txt plus the referenced report/screenshots." -Action {
        $behaviorReportPath = Get-CompletionLatestReportPath -PointerPath "build\desktop-behavior\latest-report.txt" -EvidenceName "Desktop behavior"
        Invoke-CompletionToolScript -ScriptName "assert-desktop-behavior-report.ps1" -Arguments @(
            "-ReportPath",
            $behaviorReportPath,
            "-RequiredTags",
            "full",
            "-RequiredScenarios",
            "desktop-full",
            "-RequiredSteps",
            "source-management,local-source,webdav-source,bangumi-metadata,keyboard-focus-cloud-rss,mpv-launch"
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "Android TV device smoke report" -Guidance "Run .\tools\verify-windows-port.ps1 -AndroidTv -AndroidDeviceId <android-tv-device-id> against the TV target and keep build\android-tv-qa\latest-report.txt plus screenshots/XML. Add -KeepAndroidAppData when the target already contains data that must not be cleared." -Action {
        $androidTvReportPath = Get-CompletionLatestReportPath -PointerPath "build\android-tv-qa\latest-report.txt" -EvidenceName "Android TV"
        Invoke-CompletionToolScript -ScriptName "assert-android-tv-smoke-report.ps1" -Arguments @(
            "-ReportPath",
            $androidTvReportPath
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "Prepared mpv/RIFE runtime payload" -Guidance "Prepare runtime\mpv with tools\prepare-mpv-runtime.ps1 or supply -MpvRuntimeSource, then run -MpvRuntime -PackagedMpvRuntime -NativeAppImage." -Action {
        $runtimeRoot = Resolve-RepoRelativePath -Path $MpvRuntimeSource
        foreach ($entry in @(
            @((Join-Path $runtimeRoot "mpv.exe"), "Leaf"),
            @((Join-Path $runtimeRoot "portable_config"), "Container"),
            @((Join-Path $runtimeRoot "runtime-manifest.json"), "Leaf")
        )) {
            $path = [string]$entry[0]
            $pathType = [string]$entry[1]
            if (-not (Test-Path -LiteralPath $path -PathType $pathType)) {
                throw "Missing runtime evidence: $path"
            }
        }
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "Target-host RIFE matrix report" -Guidance "Run .\tools\verify-windows-port.ps1 -Rife -RifeBackend ALL -RequiredRifeReportBackends $requiredRifeBackends on target Windows hardware with a prepared runtime." -Action {
        Invoke-CompletionToolScript -ScriptName "assert-mpv-rife-report.ps1" -Arguments @(
            "-ReportPath",
            (Join-Path $repoRoot "build\mpv-smoke\rife-matrix-report.json"),
            "-RequiredBackends",
            $requiredRifeBackends,
            "-RequireRuntimeManifest"
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "CloudDrive2 live report" -Guidance "Run .\tools\verify-windows-port.ps1 -CloudDrive -CloudDriveEndpoint <url> -CloudDriveToken <token> -CloudDrivePath $CloudDrivePath -RequireCloudDriveOfflinePermission against a real test server." -Action {
        Invoke-CompletionToolScript -ScriptName "assert-cloud-drive-report.ps1" -Arguments @(
            "-ReportPath",
            (Join-Path $repoRoot "build\cloud-drive-smoke\cloud-drive-report.json"),
            "-RequiredPath",
            $CloudDrivePath,
            "-RequireOfflinePermission"
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "CloudDrive RSS dry-run report" -Guidance "Run .\tools\verify-windows-port.ps1 -CloudRssDryRun with endpoint/token/RSS URL/inbox/library and -RequireCloudRssCandidates." -Action {
        Invoke-CompletionToolScript -ScriptName "assert-cloud-rss-report.ps1" -Arguments @(
            "-ReportPath",
            (Join-Path $repoRoot "build\cloud-rss-smoke\dry-run-report.json"),
            "-RequiredInbox",
            $CloudRssInbox,
            "-RequiredLibrary",
            $CloudRssLibrary,
            "-RequireCandidates",
            "-RequireOfflinePermission"
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "CloudDrive RSS live-submit report" -Guidance "Run .\tools\verify-windows-port.ps1 -CloudRssLiveSubmit -ConfirmCloudRssLiveSubmit with endpoint/token/RSS URL/inbox/library." -Action {
        Invoke-CompletionToolScript -ScriptName "assert-cloud-rss-report.ps1" -Arguments @(
            "-ReportPath",
            (Join-Path $repoRoot "build\cloud-rss-smoke\live-submit-report.json"),
            "-RequiredInbox",
            $CloudRssInbox,
            "-RequiredLibrary",
            $CloudRssLibrary,
            "-RequireCandidates",
            "-RequireLiveSubmit",
            "-RequireOfflinePermission"
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "CloudDrive RSS organize report" -Guidance "Run .\tools\verify-windows-port.ps1 -CloudRssOrganize -ConfirmCloudRssOrganize with endpoint/token/RSS URL/inbox/library after live-submit evidence exists." -Action {
        Invoke-CompletionToolScript -ScriptName "assert-cloud-rss-report.ps1" -Arguments @(
            "-ReportPath",
            (Join-Path $repoRoot "build\cloud-rss-smoke\organize-report.json"),
            "-RequiredInbox",
            $CloudRssInbox,
            "-RequiredLibrary",
            $CloudRssLibrary,
            "-RequireOrganize",
            "-RequireOfflinePermission"
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "CloudDrive RSS scheduler report" -Guidance "Run .\tools\verify-windows-port.ps1 without -SkipCloudRssScheduler and keep build\cloud-rss-smoke\scheduler-report.json." -Action {
        Invoke-CompletionToolScript -ScriptName "assert-cloud-rss-scheduler-report.ps1" -Arguments @(
            "-ReportPath",
            (Join-Path $repoRoot "build\cloud-rss-smoke\scheduler-report.json"),
            "-MinRunCount",
            "1",
            "-MinChecksObserved",
            "$CloudRssSchedulerRunAfterChecks"
        )
    }

    Invoke-CompletionEvidenceCheck -Problems $problems -Results $results -Name "Windows installer report" -Guidance "Run .\tools\verify-windows-port.ps1 -WindowsInstaller -SignWindowsInstaller with WiX, signtool, and release signing inputs. Use -AllowUnsignedCompletionInstaller only for local QA audits." -Action {
        $assertArgs = @(
            "-ReportPath",
            (Join-Path $repoRoot "desktop-app\build\jpackage\smoke\windows-installer-smoke.json"),
            "-RequiredInstallerType",
            $WindowsInstallerType
        )
        if (-not [string]::IsNullOrWhiteSpace($WindowsPackageVersion)) {
            $assertArgs += "-RequiredAppVersion"
            $assertArgs += $WindowsPackageVersion
        }
        if ($AllowUnsignedCompletionInstaller) {
            $assertArgs += "-RequireUnsigned"
        } else {
            $assertArgs += "-RequireSigned"
        }
        Invoke-CompletionToolScript -ScriptName "assert-windows-installer-report.ps1" -Arguments $assertArgs
    }

    $auditStatus = if ($problems.Count -gt 0) { "failed" } else { "passed" }
    $resolvedCompletionAuditReportPath = Resolve-RepoRelativePath -Path $CompletionAuditReportPath
    $completionAuditReportDirectory = Split-Path -Parent $resolvedCompletionAuditReportPath
    if (-not (Test-Path -LiteralPath $completionAuditReportDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $completionAuditReportDirectory -Force | Out-Null
    }
    $evidenceResults = @($results.ToArray())
    $requiresSignedInstaller = -not [bool]$AllowUnsignedCompletionInstaller
    [pscustomobject]@{
        generatedAtUtc = [DateTime]::UtcNow.ToString("o")
        status = $auditStatus
        requiredRifeBackends = $requiredRifeBackends
        mpvRuntimeSource = (Resolve-RepoRelativePath -Path $MpvRuntimeSource)
        windowsInstallerType = $WindowsInstallerType
        requiresSignedInstaller = $requiresSignedInstaller
        evidence = $evidenceResults
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resolvedCompletionAuditReportPath -Encoding UTF8
    Write-Host "Wrote completion audit report: $resolvedCompletionAuditReportPath"

    if ($problems.Count -gt 0) {
        Write-Host ""
        Write-Host "Windows port completion audit failed. Missing or invalid evidence:"
        foreach ($problem in $problems) {
            Write-Host (" - {0}: {1}" -f $problem.Name, $problem.Message)
            Write-Host ("   Next: {0}" -f $problem.Guidance)
        }
        throw "Windows port completion audit failed: $($problems.Count) required evidence item(s) are missing or invalid."
    }

    Write-Host "Windows port completion audit passed."
}

$defaultGradleTasks = @(
    "checkDesktopComposeOnly",
    "checkDesktopPresenterSeparation",
    "checkUiPaletteDrift",
    ":core:model:test",
    ":ui-tv:testDebugUnitTest",
    ":media-source-api:test",
    ":metadata-core:test",
    ":repository-api:test",
    ":cloud-drive-api:test",
    ":sync-engine-shared:test",
    ":metadata:test",
    ":scraper-core:test",
    ":scraper:test",
    ":sync-engine:test",
    ":web-control:test",
    ":media-source-desktop:test",
    ":scanner-desktop:test",
    ":repository-desktop:test",
    ":scraper-desktop:test",
    ":player-mpv:test",
    ":cloud-drive-desktop:test",
    ":sync-engine-desktop:test",
    ":desktop-app:test",
    ":desktop-app:installDist",
    ":desktop-app:desktopWebControlSmoke",
    "-PbundleMpvRuntime=false"
)
if (-not $SkipAndroidBuild) {
    $defaultGradleTasks = @(":app:assembleDebug") + $defaultGradleTasks
}

Push-Location $repoRoot
try {
    if ([string]::IsNullOrWhiteSpace($AndroidDeviceId)) {
        $AndroidDeviceId = $env:MIRUPLAY_ANDROID_TV_DEVICE_ID
    }

    Use-Jdk21
    if ($CloudRssScheduler) {
        Write-Host "CloudDrive RSS scheduler smoke now runs by default; -CloudRssScheduler is retained for compatibility."
    }
    if ($CloudRssEvidenceBundle) {
        Write-Host "CloudDrive RSS evidence bundle mode enabled: dry-run + live-submit + organize reports will run as one checklist."
    }

    if (-not $SkipGradle) {
        Invoke-Step -Name "Safe Gradle gate" -Action {
            Invoke-Gradle -Arguments $defaultGradleTasks
        }
        Invoke-Step -Name "desktop WebUI smoke report assertion" -Action {
            $webControlReportPath = Join-Path $repoRoot "desktop-app\build\web-control-smoke\desktop-web-control-smoke.json"
            Invoke-ToolScript -ScriptName "assert-desktop-web-control-smoke-report.ps1" -Arguments @(
                "-ReportPath",
                $webControlReportPath
            )
        }
    } else {
        Write-Host "Skipping safe Gradle gate because -SkipGradle was supplied."
    }

    if ($Behavior) {
        Invoke-Step -Name "desktop behavior smoke" -Action {
            Invoke-Gradle -Arguments @(
                ":desktop-app:desktopBehaviorTest",
                "-PdesktopBehaviorTags=$BehaviorTags",
                "-PbundleMpvRuntime=false"
            )
            $behaviorLatestReport = Join-Path $repoRoot "build\desktop-behavior\latest-report.txt"
            if (-not (Test-Path -LiteralPath $behaviorLatestReport -PathType Leaf)) {
                throw "Desktop behavior smoke did not write latest report pointer: $behaviorLatestReport"
            }
            $behaviorReportPath = [System.IO.File]::ReadAllText($behaviorLatestReport, [System.Text.Encoding]::UTF8).Trim()
            if ($BehaviorTags -eq "full") {
                $requiredScenarios = @("desktop-full")
                $requiredSteps = @("source-management", "local-source", "webdav-source", "bangumi-metadata", "keyboard-focus-cloud-rss", "mpv-launch")
            } else {
                $requiredScenarios = @("desktop-smoke")
                $requiredSteps = @("local-source", "keyboard-focus")
            }
            Invoke-ToolScript -ScriptName "assert-desktop-behavior-report.ps1" -Arguments (
                @(
                    "-ReportPath",
                    $behaviorReportPath,
                    "-RequiredTags",
                    $BehaviorTags,
                    "-RequiredScenarios",
                    ($requiredScenarios -join ","),
                    "-RequiredSteps",
                    ($requiredSteps -join ",")
                )
            )
        }
    } else {
        Write-Host "Desktop behavior smoke skipped. Run with -Behavior to match the Windows CI JSON-driven behavior gate."
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

    if ($NativeAppImage) {
        Invoke-Step -Name "native app image runtime smoke" -Action {
            Invoke-Gradle -Arguments @(
                ":desktop-app:smokeNativeAppImageRuntime",
                "-PmpvRuntimeSource=$MpvRuntimeSource",
                "-PrequireMpvRuntime=true",
                "-PrequiredRifeBackends=$RequiredRifeBackends"
            )
        }
    }

    if ($WindowsInstaller) {
        Invoke-Step -Name "Windows installer smoke" -Action {
            $installerArgs = @(
                ":desktop-app:smokeWindowsInstaller",
                "-PmpvRuntimeSource=$MpvRuntimeSource",
                "-PrequireMpvRuntime=true",
                "-PrequiredRifeBackends=$RequiredRifeBackends",
                "-PwindowsInstallerType=$WindowsInstallerType",
                "-PrequireWindowsInstallerToolchain=true"
            )
            if ($SignWindowsInstaller) {
                $installerArgs += "-PsignWindowsInstaller=true"
            }
            if (-not [string]::IsNullOrWhiteSpace($WindowsInstallerCertPath)) {
                $installerArgs += "-PwindowsInstallerCertPath=$WindowsInstallerCertPath"
            }
            if (-not [string]::IsNullOrWhiteSpace($WindowsInstallerCertPassword)) {
                $installerArgs += "-PwindowsInstallerCertPassword=$WindowsInstallerCertPassword"
            }
            if (-not [string]::IsNullOrWhiteSpace($WindowsInstallerSignTool)) {
                $installerArgs += "-PwindowsInstallerSignTool=$WindowsInstallerSignTool"
            }
            if (-not [string]::IsNullOrWhiteSpace($WindowsInstallerTimestampUrl)) {
                $installerArgs += "-PwindowsInstallerTimestampUrl=$WindowsInstallerTimestampUrl"
            }
            if (-not [string]::IsNullOrWhiteSpace($WindowsPackageVersion)) {
                $installerArgs += "-PwindowsPackageVersion=$WindowsPackageVersion"
            }
            if (-not [string]::IsNullOrWhiteSpace($WindowsInstallerUpgradeUuid)) {
                $installerArgs += "-PwindowsInstallerUpgradeUuid=$WindowsInstallerUpgradeUuid"
            }
            Invoke-Gradle -Arguments $installerArgs

            $installerReportPath = Join-Path $repoRoot "desktop-app\build\jpackage\smoke\windows-installer-smoke.json"
            $assertArgs = @(
                "-ReportPath",
                $installerReportPath,
                "-RequiredInstallerType",
                $WindowsInstallerType
            )
            if (-not [string]::IsNullOrWhiteSpace($WindowsPackageVersion)) {
                $assertArgs += "-RequiredAppVersion"
                $assertArgs += $WindowsPackageVersion
            }
            if ($SignWindowsInstaller) {
                $assertArgs += "-RequireSigned"
            } else {
                $assertArgs += "-RequireUnsigned"
            }
            Invoke-ToolScript -ScriptName "assert-windows-installer-report.ps1" -Arguments $assertArgs
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
        if ([string]::IsNullOrWhiteSpace($AndroidDeviceId)) {
            throw "Pass -AndroidDeviceId or set MIRUPLAY_ANDROID_TV_DEVICE_ID before running -AndroidTv."
        }
        Invoke-Step -Name "Android TV emulator smoke" -Action {
            Invoke-Native -FilePath "adb" -Arguments @("connect", $AndroidDeviceId)
            $androidTvSmokeArgs = @(
                "-DeviceId",
                $AndroidDeviceId
            )
            if ($KeepAndroidAppData) {
                $androidTvSmokeArgs += "-KeepAppData"
            }
            Invoke-ToolScript -ScriptName "smoke-android-tv-ui.ps1" -Arguments $androidTvSmokeArgs
            $androidTvLatestReport = Join-Path $repoRoot "build\android-tv-qa\latest-report.txt"
            if (-not (Test-Path -LiteralPath $androidTvLatestReport -PathType Leaf)) {
                throw "Android TV smoke did not write latest report pointer: $androidTvLatestReport"
            }
            $androidTvReportPath = [System.IO.File]::ReadAllText($androidTvLatestReport, [System.Text.Encoding]::UTF8).Trim()
            Invoke-ToolScript -ScriptName "assert-android-tv-smoke-report.ps1" -Arguments @(
                "-ReportPath",
                $androidTvReportPath,
                "-RequiredDeviceId",
                $AndroidDeviceId
            )
        }
    }

    if ($Rife) {
        Invoke-Step -Name "RIFE target-hardware smoke" -Action {
            $reportName = if ($RifeBackend -eq "ALL") { "rife-matrix-report.json" } else { "rife-$($RifeBackend.ToLowerInvariant())-report.json" }
            $reportPath = Join-Path $repoRoot "build\mpv-smoke\$reportName"
            $rifeArgs = @(
                "-RuntimeRoot",
                $MpvRuntimeSource,
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
                $requiredReportBackends,
                "-RequireRuntimeManifest"
            )
            if ($AllowRifeFailures) {
                $assertArgs += "-AllowFailures"
            }
            Invoke-ToolScript -ScriptName "assert-mpv-rife-report.ps1" -Arguments $assertArgs
        }
    } else {
        Write-Host "RIFE smoke skipped. Run with -Rife only on target hardware expected to support interpolation."
    }

    if ($CloudDrive) {
        Invoke-Step -Name "CloudDrive2 live smoke" -Action {
            if ([string]::IsNullOrWhiteSpace($CloudDriveEndpoint) -or [string]::IsNullOrWhiteSpace($CloudDriveToken)) {
                throw "CloudDrive live smoke requires -CloudDriveEndpoint and -CloudDriveToken."
            }
            $reportPath = Join-Path $repoRoot "build\cloud-drive-smoke\cloud-drive-report.json"
            Invoke-Gradle -Arguments @(
                ":cloud-drive-desktop:smokeCloudDrive2",
                "-PcloudDriveEndpoint=$CloudDriveEndpoint",
                "-PcloudDriveToken=$CloudDriveToken",
                "-PcloudDrivePath=$CloudDrivePath",
                "-PcloudDriveReportPath=$reportPath"
            )
            $assertArgs = @(
                "-ReportPath",
                $reportPath,
                "-RequiredPath",
                $CloudDrivePath
            )
            if ($RequireCloudDriveOfflinePermission) {
                $assertArgs += "-RequireOfflinePermission"
            }
            Invoke-ToolScript -ScriptName "assert-cloud-drive-report.ps1" -Arguments $assertArgs
        }
    } else {
        Write-Host "CloudDrive2 live smoke skipped. Run with -CloudDrive and explicit endpoint/token only against a real test server."
    }

    $rssEndpoint = if ([string]::IsNullOrWhiteSpace($CloudRssEndpoint)) { $CloudDriveEndpoint } else { $CloudRssEndpoint }
    $rssToken = if ([string]::IsNullOrWhiteSpace($CloudRssToken)) { $CloudDriveToken } else { $CloudRssToken }

    if ($CloudRssEvidenceBundle) {
        Invoke-CloudRssSmokeAndAssert -StepName "CloudDrive RSS dry-run smoke" -LiveSubmitEnabled $false -OrganizeEnabled $false -RssEndpoint $rssEndpoint -RssToken $rssToken
        Invoke-CloudRssSmokeAndAssert -StepName "CloudDrive RSS live submit smoke" -LiveSubmitEnabled $true -OrganizeEnabled $false -RssEndpoint $rssEndpoint -RssToken $rssToken
        Invoke-CloudRssSmokeAndAssert -StepName "CloudDrive RSS organize smoke" -LiveSubmitEnabled $false -OrganizeEnabled $true -RssEndpoint $rssEndpoint -RssToken $rssToken
    } elseif ($CloudRssDryRun -or $CloudRssLiveSubmit -or $CloudRssOrganize) {
        $cloudRssStepName = if ($CloudRssLiveSubmit) {
            "CloudDrive RSS live submit smoke"
        } elseif ($CloudRssOrganize) {
            "CloudDrive RSS organize smoke"
        } else {
            "CloudDrive RSS dry-run smoke"
        }
        Invoke-CloudRssSmokeAndAssert -StepName $cloudRssStepName -LiveSubmitEnabled ([bool]$CloudRssLiveSubmit) -OrganizeEnabled ([bool]$CloudRssOrganize) -RssEndpoint $rssEndpoint -RssToken $rssToken
    } else {
        Write-Host "CloudDrive RSS dry-run/live/organize smoke skipped. Run with -CloudRssDryRun, -CloudRssLiveSubmit, or -CloudRssOrganize and explicit endpoint/token/RSS URL."
    }

    if (-not $SkipCloudRssScheduler) {
        Invoke-Step -Name "CloudDrive RSS scheduler smoke" -Action {
            $reportPath = Join-Path $repoRoot "build\cloud-rss-smoke\scheduler-report.json"
            Invoke-Gradle -Arguments @(
                ":sync-engine-desktop:smokeCloudDriveRssScheduler",
                "-PcloudDriveRssSchedulerDurationMs=$CloudRssSchedulerDurationMs",
                "-PcloudDriveRssSchedulerCheckIntervalMs=$CloudRssSchedulerCheckIntervalMs",
                "-PcloudDriveRssSchedulerRunAfterChecks=$CloudRssSchedulerRunAfterChecks",
                "-PcloudDriveRssSchedulerReportPath=$reportPath"
            )
            Invoke-ToolScript -ScriptName "assert-cloud-rss-scheduler-report.ps1" -Arguments @(
                "-ReportPath",
                $reportPath,
                "-MinRunCount",
                "1",
                "-MinChecksObserved",
                "$CloudRssSchedulerRunAfterChecks"
            )
        }
    } else {
        Write-Host "CloudDrive RSS scheduler smoke skipped because -SkipCloudRssScheduler was supplied."
    }

    if ($ExternalPrereqAudit) {
        Invoke-Step -Name "Windows port external prerequisite audit" -Action {
            $externalPrereqArgs = @(
                "-MpvRuntimeSource",
                $MpvRuntimeSource,
                "-RequiredRifeBackends",
                $RequiredRifeBackends,
                "-CloudDrivePath",
                $CloudDrivePath,
                "-CloudRssInbox",
                $CloudRssInbox,
                "-CloudRssLibrary",
                $CloudRssLibrary,
                "-WindowsInstallerType",
                $WindowsInstallerType,
                "-ReportPath",
                $ExternalPrereqReportPath
            )
            if (-not [string]::IsNullOrWhiteSpace($CloudDriveEndpoint)) {
                $externalPrereqArgs += "-CloudDriveEndpoint"
                $externalPrereqArgs += $CloudDriveEndpoint
            }
            if (-not [string]::IsNullOrWhiteSpace($CloudDriveToken)) {
                $externalPrereqArgs += "-CloudDriveToken"
                $externalPrereqArgs += $CloudDriveToken
            }
            if (-not [string]::IsNullOrWhiteSpace($CloudRssEndpoint)) {
                $externalPrereqArgs += "-CloudRssEndpoint"
                $externalPrereqArgs += $CloudRssEndpoint
            }
            if (-not [string]::IsNullOrWhiteSpace($CloudRssToken)) {
                $externalPrereqArgs += "-CloudRssToken"
                $externalPrereqArgs += $CloudRssToken
            }
            if (-not [string]::IsNullOrWhiteSpace($CloudRssUrl)) {
                $externalPrereqArgs += "-CloudRssUrl"
                $externalPrereqArgs += $CloudRssUrl
            }
            if (-not [string]::IsNullOrWhiteSpace($WindowsInstallerCertPath)) {
                $externalPrereqArgs += "-WindowsInstallerCertPath"
                $externalPrereqArgs += $WindowsInstallerCertPath
            }
            if (-not [string]::IsNullOrWhiteSpace($WindowsInstallerSignTool)) {
                $externalPrereqArgs += "-WindowsInstallerSignTool"
                $externalPrereqArgs += $WindowsInstallerSignTool
            }
            if ($AllowUnsignedCompletionInstaller) {
                $externalPrereqArgs += "-AllowUnsignedInstaller"
            }
            Invoke-ToolScript -ScriptName "check-windows-port-external-prereqs.ps1" -Arguments $externalPrereqArgs
        }
    } else {
        Write-Host "Windows port external prerequisite audit skipped. Run with -ExternalPrereqAudit to write build\windows-port-audit\external-prereqs.json."
    }

    if ($CompletionAudit) {
        Invoke-Step -Name "Windows port completion evidence audit" -Action {
            Invoke-WindowsPortCompletionAudit
        }
    } else {
        Write-Host "Windows port completion evidence audit skipped. Run with -CompletionAudit to prove the full release evidence set is present."
    }
} finally {
    Pop-Location
    Write-StepSummary
}
