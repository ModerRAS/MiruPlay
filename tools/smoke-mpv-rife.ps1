[CmdletBinding()]
param(
    [string]$RuntimeRoot = (Join-Path $PSScriptRoot "..\runtime\mpv"),
    [ValidateSet("NVIDIA", "DIRECTML", "STANDARD", "ALL")]
    [string]$Backend = "DIRECTML",
    [string]$ClipPath = (Join-Path $PSScriptRoot "..\build\mpv-smoke\clip-1440x810-2f.y4m"),
    [int]$Width = 1440,
    [int]$Height = 810,
    [int]$Frames = 2,
    [string]$ReportPath = "",
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
    $process = Start-Process -FilePath $MpvPath -ArgumentList $arguments -PassThru -WindowStyle Hidden
    if (-not $process.WaitForExit(60000)) {
        Stop-Process -Id $process.Id -Force
        return [pscustomobject]@{
            Backend = $BackendName
            Status = "FAIL"
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
        Host = Get-RifeHostDiagnostics
        Results = @($results)
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resolvedReportPath -Encoding UTF8
    Write-Host "Wrote RIFE smoke report: $resolvedReportPath"
}

$failed = @($results | Where-Object { $_.Status -ne "PASS" })
if ($failed.Count -gt 0 -and -not $AllowFailures) {
    throw "RIFE smoke failed for: $($failed.Backend -join ', ')"
}
