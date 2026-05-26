[CmdletBinding()]
param(
    [string]$RuntimeRoot = "",
    [ValidateSet("NVIDIA", "DIRECTML", "STANDARD", "ALL")]
    [string]$Backend = "DIRECTML",
    [string]$ClipPath = "",
    [int]$Width = 1440,
    [int]$Height = 810,
    [int]$Frames = 2,
    [string]$ReportPath = "",
    [switch]$AllowFailures
)

$ErrorActionPreference = "Stop"

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
if ([string]::IsNullOrWhiteSpace($RuntimeRoot)) {
    $RuntimeRoot = Join-Path $scriptRoot "..\runtime\mpv"
}
if ([string]::IsNullOrWhiteSpace($ClipPath)) {
    $ClipPath = Join-Path $scriptRoot "..\build\mpv-smoke\clip-1440x810-2f.y4m"
}

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function New-Y4mSmokeClip {
    param(
        [string]$Path,
        [int]$ClipWidth,
        [int]$ClipHeight,
        [int]$FrameCount
    )

    $directory = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    $lumaBytes = $ClipWidth * $ClipHeight
    $chromaBytes = [int]($lumaBytes / 4)
    $luma = [byte[]]::new($lumaBytes)
    $chroma = [byte[]]::new($chromaBytes)
    for ($i = 0; $i -lt $luma.Length; $i++) {
        $luma[$i] = 16
    }
    for ($i = 0; $i -lt $chroma.Length; $i++) {
        $chroma[$i] = 128
    }

    $file = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $header = [System.Text.Encoding]::ASCII.GetBytes("YUV4MPEG2 W$ClipWidth H$ClipHeight F24000:1001 Ip A0:0 C420jpeg`n")
        $frameHeader = [System.Text.Encoding]::ASCII.GetBytes("FRAME`n")
        $file.Write($header, 0, $header.Length)
        for ($frame = 0; $frame -lt $FrameCount; $frame++) {
            $file.Write($frameHeader, 0, $frameHeader.Length)
            $file.Write($luma, 0, $luma.Length)
            $file.Write($chroma, 0, $chroma.Length)
            $file.Write($chroma, 0, $chroma.Length)
        }
    } finally {
        $file.Dispose()
    }
}

function Get-RifeScriptName {
    param([string]$BackendName)
    switch ($BackendName) {
        "NVIDIA" { "MEMC_RIFE_NV.vpy" }
        "DIRECTML" { "MEMC_RIFE_DML.vpy" }
        "STANDARD" { "MEMC_RIFE_STD.vpy" }
        default { throw "Unknown RIFE backend: $BackendName" }
    }
}

function Test-WindowsDrivePrefix {
    param([string]$Value)
    return $Value.Length -ge 2 -and $Value[1] -eq ":" -and [string]$Value[0] -match '^[A-Za-z]$'
}

function Normalize-RuntimeManifestEntry {
    param([string]$Entry)

    if ([string]::IsNullOrWhiteSpace($Entry)) {
        return $null
    }

    $normalized = $Entry.Trim().Replace("\", "/")
    $directoryEntry = $normalized.EndsWith("/")
    if ($directoryEntry) {
        $normalized = $normalized.TrimEnd("/")
    }

    if (
        $normalized.Length -eq 0 -or
        $normalized.StartsWith("/") -or
        (Test-WindowsDrivePrefix -Value $normalized)
    ) {
        return $null
    }

    $segments = @($normalized -split "/")
    foreach ($segment in $segments) {
        if ([string]::IsNullOrWhiteSpace($segment) -or $segment -eq "." -or $segment -eq "..") {
            return $null
        }
    }

    if ($directoryEntry) {
        return "$($segments -join '/')/"
    }
    return $segments -join "/"
}

function Get-RuntimeManifestStringArray {
    param(
        $Object,
        [string]$PropertyName,
        [System.Collections.Generic.List[string]]$Problems
    )

    $property = $Object.PSObject.Properties[$PropertyName]
    if ($null -eq $property -or $null -eq $property.Value) {
        return @()
    }
    if ($property.Value -is [string]) {
        $Problems.Add("runtime-manifest.json $PropertyName must be an array, not a string.") | Out-Null
        return @()
    }

    $values = @($property.Value)
    $strings = New-Object 'System.Collections.Generic.List[string]'
    foreach ($value in $values) {
        if ($null -eq $value -or $value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) {
            $Problems.Add("runtime-manifest.json $PropertyName contains a non-string or blank entry.") | Out-Null
        } else {
            $strings.Add($value.Trim()) | Out-Null
        }
    }
    return @($strings)
}

function Get-RuntimeManifestEvidence {
    param([string]$RuntimeRoot)

    $manifestPath = Join-Path $RuntimeRoot "runtime-manifest.json"
    $present = Test-Path -LiteralPath $manifestPath -PathType Leaf
    $problems = New-Object 'System.Collections.Generic.List[string]'
    $requiredBackends = @()
    $declaredFiles = @()

    if ($present) {
        $manifest = $null
        try {
            $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        } catch {
            $problems.Add("runtime-manifest.json could not be parsed: $($_.Exception.Message)") | Out-Null
        }

        if ($null -ne $manifest) {
            $requiredBackends = @(
                Get-RuntimeManifestStringArray `
                    -Object $manifest `
                    -PropertyName "requiredRifeBackends" `
                    -Problems $problems |
                    ForEach-Object { $_.Trim().ToUpperInvariant() } |
                    Select-Object -Unique
            )
            foreach ($backend in $requiredBackends) {
                if ($backend -notin @("NVIDIA", "DIRECTML", "STANDARD")) {
                    $problems.Add("runtime-manifest.json requiredRifeBackends contains unknown backend: $backend") | Out-Null
                    continue
                }
                $scriptName = Get-RifeScriptName -BackendName $backend
                $scriptPath = Join-Path $RuntimeRoot "portable_config\vs\$scriptName"
                if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
                    $problems.Add("runtime-manifest.json requiredRifeBackends entry is missing script: portable_config/vs/$scriptName") | Out-Null
                }
            }

            $declaredFiles = @(
                Get-RuntimeManifestStringArray `
                    -Object $manifest `
                    -PropertyName "files" `
                    -Problems $problems
            )
            foreach ($entry in $declaredFiles) {
                $normalizedEntry = Normalize-RuntimeManifestEntry -Entry $entry
                if ($null -eq $normalizedEntry) {
                    $problems.Add("runtime-manifest.json files contains invalid package-relative entry: $entry") | Out-Null
                    continue
                }

                $relativePath = $normalizedEntry.TrimEnd("/").Replace("/", "\")
                $declaredPath = Join-Path $RuntimeRoot $relativePath
                if ($normalizedEntry.EndsWith("/")) {
                    if (-not (Test-Path -LiteralPath $declaredPath -PathType Container)) {
                        $problems.Add("runtime-manifest.json files directory entry is missing: $normalizedEntry") | Out-Null
                    }
                } elseif (-not (Test-Path -LiteralPath $declaredPath -PathType Leaf)) {
                    $problems.Add("runtime-manifest.json files entry is missing: $normalizedEntry") | Out-Null
                }
            }
        }
    }

    return [pscustomobject]@{
        Present = [bool]$present
        Path = $manifestPath
        RequiredRifeBackends = @($requiredBackends)
        Files = @($declaredFiles)
        Problems = @($problems)
    }
}

function Get-CommandOutput {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )

    $command = Get-Command $FilePath -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        return $null
    }
    try {
        return (& $command.Source @Arguments 2>&1) -join "`n"
    } catch {
        return "Failed to run ${FilePath}: $($_.Exception.Message)"
    }
}

function Get-MpvVersionLine {
    param([string]$MpvPath)
    try {
        $version = (& $MpvPath --version 2>&1 | Select-Object -First 1)
        return [string]$version
    } catch {
        return "Failed to run mpv --version: $($_.Exception.Message)"
    }
}

function Get-RifeHostDiagnostics {
    $operatingSystem = $null
    $videoControllers = @()
    $processors = @()
    try {
        $operatingSystem = Get-CimInstance Win32_OperatingSystem |
            Select-Object Caption, Version, BuildNumber, OSArchitecture
    } catch {
        $operatingSystem = [pscustomobject]@{ Error = $_.Exception.Message }
    }
    try {
        $videoControllers = @(Get-CimInstance Win32_VideoController |
            Select-Object Name, DriverVersion, VideoProcessor, AdapterRAM, PNPDeviceID)
    } catch {
        $videoControllers = @([pscustomobject]@{ Error = $_.Exception.Message })
    }
    try {
        $processors = @(Get-CimInstance Win32_Processor |
            Select-Object Name, NumberOfCores, NumberOfLogicalProcessors)
    } catch {
        $processors = @([pscustomobject]@{ Error = $_.Exception.Message })
    }

    return [pscustomobject]@{
        ComputerName = $env:COMPUTERNAME
        UserName = $env:USERNAME
        PowerShellVersion = $PSVersionTable.PSVersion.ToString()
        OperatingSystem = $operatingSystem
        Processors = $processors
        VideoControllers = $videoControllers
        NvidiaSmi = Get-CommandOutput -FilePath "nvidia-smi" -Arguments @(
            "--query-gpu=name,driver_version,memory.total",
            "--format=csv,noheader"
        )
        DirectXDiagnosticTool = if (Get-Command dxdiag.exe -ErrorAction SilentlyContinue) { "available" } else { "not found" }
    }
}

function ConvertTo-WindowsCommandLineArgument {
    param([string]$Argument)

    if ($null -eq $Argument) {
        return '""'
    }
    if ($Argument.Length -eq 0) {
        return '""'
    }
    if ($Argument -notmatch '[\s"]') {
        return $Argument
    }

    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append('"')
    $backslashCount = 0
    foreach ($character in $Argument.ToCharArray()) {
        if ($character -eq '\') {
            $backslashCount += 1
            continue
        }
        if ($character -eq '"') {
            [void]$builder.Append(('\' * (($backslashCount * 2) + 1)) -join "")
            [void]$builder.Append('"')
            $backslashCount = 0
            continue
        }
        if ($backslashCount -gt 0) {
            [void]$builder.Append(('\' * $backslashCount) -join "")
            $backslashCount = 0
        }
        [void]$builder.Append($character)
    }
    if ($backslashCount -gt 0) {
        [void]$builder.Append(('\' * ($backslashCount * 2)) -join "")
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function Start-HiddenProcess {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $FilePath
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
    $argumentListProperty = [System.Diagnostics.ProcessStartInfo].GetProperty("ArgumentList")
    if ($null -ne $argumentListProperty) {
        foreach ($argument in $Arguments) {
            $processInfo.ArgumentList.Add($argument)
        }
    } else {
        $processInfo.Arguments = (@($Arguments) | ForEach-Object {
            ConvertTo-WindowsCommandLineArgument -Argument $_
        }) -join " "
    }
    return [System.Diagnostics.Process]::Start($processInfo)
}

function Invoke-RifeSmokeBackend {
    param(
        [string]$BackendName,
        [string]$MpvPath,
        [string]$ConfigDirectory,
        [string]$SmokeClipPath,
        [int]$FrameCount
    )

    $scriptName = Get-RifeScriptName -BackendName $BackendName
    $scriptPath = Join-Path $ConfigDirectory "vs\$scriptName"
    $logPath = Join-Path (Split-Path -Parent $SmokeClipPath) "rife-$BackendName.log"
    $startedAt = Get-Date

    if (-not (Test-Path -LiteralPath $scriptPath)) {
        return [pscustomobject]@{
            Backend = $BackendName
            Status = "FAIL"
            ScriptName = $scriptName
            ScriptPath = $scriptPath
            Message = "RIFE script not found: $scriptPath"
            LogPath = $logPath
            ExitCode = $null
            StartedAtUtc = $startedAt.ToUniversalTime().ToString("o")
            FinishedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        }
    }

    $arguments = @(
        "--config-dir=$ConfigDirectory",
        "--vo=null",
        "--ao=null",
        "--frames=$FrameCount",
        "--keep-open=no",
        "--idle=no",
        "--force-window=no",
        "--terminal=yes",
        "--msg-level=all=info",
        "--log-file=$logPath",
        "--vf-append=vapoursynth=~~home/vs/$scriptName`:4:auto:",
        $SmokeClipPath
    )

    Write-Host "Running $BackendName RIFE smoke with $MpvPath"
    $process = Start-HiddenProcess -FilePath $MpvPath -Arguments $arguments
    if (-not $process.WaitForExit(60000)) {
        Stop-Process -Id $process.Id -Force
        return [pscustomobject]@{
            Backend = $BackendName
            Status = "FAIL"
            ScriptName = $scriptName
            ScriptPath = $scriptPath
            Message = "Timed out after 60 seconds"
            LogPath = $logPath
            ExitCode = $null
            StartedAtUtc = $startedAt.ToUniversalTime().ToString("o")
            FinishedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        }
    }
    if ($process.ExitCode -ne 0) {
        $tail = ""
        if (Test-Path -LiteralPath $logPath) {
            $tail = (Get-Content -LiteralPath $logPath -Tail 12) -join " | "
        }
        $message = "mpv exited $($process.ExitCode)"
        if ($tail.Length -gt 0) {
            $message = "$message; log tail: $tail"
        }
        return [pscustomobject]@{
            Backend = $BackendName
            Status = "FAIL"
            ScriptName = $scriptName
            ScriptPath = $scriptPath
            Message = $message
            LogPath = $logPath
            ExitCode = $process.ExitCode
            StartedAtUtc = $startedAt.ToUniversalTime().ToString("o")
            FinishedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        }
    }

    return [pscustomobject]@{
        Backend = $BackendName
        Status = "PASS"
        ScriptName = $scriptName
        ScriptPath = $scriptPath
        Message = "RIFE filter initialized and playback completed"
        LogPath = $logPath
        ExitCode = $process.ExitCode
        StartedAtUtc = $startedAt.ToUniversalTime().ToString("o")
        FinishedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    }
}

$resolvedRuntimeRoot = Resolve-FullPath $RuntimeRoot
$resolvedClipPath = Resolve-FullPath $ClipPath
$resolvedReportPath = if ($ReportPath.Trim().Length -gt 0) { Resolve-FullPath $ReportPath } else { "" }
$mpv = Join-Path $resolvedRuntimeRoot "mpv.exe"
$configDir = Join-Path $resolvedRuntimeRoot "portable_config"

if (-not (Test-Path -LiteralPath $mpv)) {
    throw "mpv.exe was not found at $mpv"
}

New-Y4mSmokeClip -Path $resolvedClipPath -ClipWidth $Width -ClipHeight $Height -FrameCount $Frames

$backends = if ($Backend -eq "ALL") {
    @("NVIDIA", "DIRECTML", "STANDARD")
} else {
    @($Backend)
}

$results = foreach ($backendName in $backends) {
    Invoke-RifeSmokeBackend `
        -BackendName $backendName `
        -MpvPath $mpv `
        -ConfigDirectory $configDir `
        -SmokeClipPath $resolvedClipPath `
        -FrameCount $Frames
}

Write-Host ""
Write-Host "RIFE smoke summary for $resolvedRuntimeRoot"
$results | Format-Table Backend, Status, Message, LogPath -AutoSize | Out-String | Write-Host

$finishedAt = Get-Date
if ($resolvedReportPath.Length -gt 0) {
    $reportDirectory = Split-Path -Parent $resolvedReportPath
    if (-not (Test-Path -LiteralPath $reportDirectory)) {
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    }
    [pscustomobject]@{
        GeneratedAtUtc = $finishedAt.ToUniversalTime().ToString("o")
        RuntimeRoot = $resolvedRuntimeRoot
        MpvPath = $mpv
        MpvVersion = Get-MpvVersionLine -MpvPath $mpv
        ConfigDirectory = $configDir
        ClipPath = $resolvedClipPath
        Clip = [pscustomobject]@{
            Width = $Width
            Height = $Height
            Frames = $Frames
        }
        RequestedBackend = $Backend
        AllowFailures = [bool]$AllowFailures
        RuntimeManifest = Get-RuntimeManifestEvidence -RuntimeRoot $resolvedRuntimeRoot
        Host = Get-RifeHostDiagnostics
        Results = @($results)
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resolvedReportPath -Encoding UTF8
    Write-Host "Wrote RIFE smoke report: $resolvedReportPath"
}

$failed = @($results | Where-Object { $_.Status -ne "PASS" })
if ($failed.Count -gt 0 -and -not $AllowFailures) {
    throw "RIFE smoke failed for: $($failed.Backend -join ', ')"
}
