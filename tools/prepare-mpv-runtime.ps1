[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [string]$OverlaySource = '',

    [string]$Destination = (Join-Path $PSScriptRoot '..\runtime\mpv'),

    [string]$RequiredRifeBackends = 'NVIDIA,DIRECTML',

    [string]$ExpectedSha256 = '',

    [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Resolve-RuntimeRoot {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-Path -LiteralPath (Join-Path $Path 'mpv.exe') -PathType Leaf) {
        return (Resolve-Path -LiteralPath $Path).Path
    }

    $children = Get-ChildItem -LiteralPath $Path -Directory
    if ($children.Count -eq 1 -and (Test-Path -LiteralPath (Join-Path $children[0].FullName 'mpv.exe') -PathType Leaf)) {
        return $children[0].FullName
    }

    return (Resolve-Path -LiteralPath $Path).Path
}

function Resolve-OverlayRoot {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-Path -LiteralPath (Join-Path $Path 'portable_config') -PathType Container) {
        return (Resolve-Path -LiteralPath $Path).Path
    }

    $children = Get-ChildItem -LiteralPath $Path -Directory
    if ($children.Count -eq 1 -and (Test-Path -LiteralPath (Join-Path $children[0].FullName 'portable_config') -PathType Container)) {
        return $children[0].FullName
    }

    return (Resolve-Path -LiteralPath $Path).Path
}

function Expand-RuntimeArchive {
    param([Parameter(Mandatory = $true)][string]$ArchivePath)

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("miruplay-mpv-runtime-{0}" -f ([guid]::NewGuid().ToString('N')))
    New-Item -ItemType Directory -Path $tempRoot | Out-Null

    $sevenZip = (Get-Command 7z.exe -ErrorAction Stop).Source
    & $sevenZip x $ArchivePath "-o$tempRoot" -y | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to extract runtime archive: $ArchivePath"
    }

    return $tempRoot
}

function Assert-SourceHash {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Expected
    )

    if ([string]::IsNullOrWhiteSpace($Expected)) {
        return
    }
    $entries = $Expected -split '[;\r\n]+' |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }

    if ($entries | Where-Object { $_ -like '*=*' }) {
        $baseDirectory = if ((Get-Item -LiteralPath $Path).PSIsContainer) {
            (Resolve-Path -LiteralPath $Path).Path
        } else {
            Split-Path -Parent (Resolve-Path -LiteralPath $Path).Path
        }
        foreach ($entry in $entries) {
            $parts = $entry -split '=', 2
            if ($parts.Count -ne 2) {
                throw "Expected SHA256 entry must be filename=sha256: $entry"
            }
            $candidatePath = Join-Path $baseDirectory $parts[0].Trim()
            if (-not (Test-Path -LiteralPath $candidatePath -PathType Leaf)) {
                throw "Expected SHA256 file does not exist: $candidatePath"
            }
            Assert-FileHash -Path $candidatePath -Expected $parts[1]
        }
        return
    }

    if ((Get-Item -LiteralPath $Path).PSIsContainer) {
        throw "-ExpectedSha256 can only be used when -Source points to an archive file."
    }

    Assert-FileHash -Path $Path -Expected $Expected
}

function Assert-FileHash {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Expected
    )

    $normalizedExpected = $Expected.Trim().ToLowerInvariant().Replace('sha256:', '')
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $normalizedExpected) {
        throw "SHA256 mismatch for $Path. Expected $normalizedExpected but got $actual."
    }
}

function Get-RequiredRifeBackends {
    param([string]$Value)

    $Value -split '[,;\s]+' |
        ForEach-Object { $_.Trim().ToUpperInvariant() } |
        Where-Object { $_ }
}

function Get-BackendScriptName {
    param([Parameter(Mandatory = $true)][string]$Backend)

    switch ($Backend.ToUpperInvariant()) {
        'NVIDIA' { 'MEMC_RIFE_NV.vpy' }
        'DIRECTML' { 'MEMC_RIFE_DML.vpy' }
        'STANDARD' { 'MEMC_RIFE_STD.vpy' }
        default { throw "Unknown required RIFE backend: $Backend" }
    }
}

function Assert-RuntimePayload {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [string[]]$Backends
    )

    $missing = @()

    if (-not (Test-Path -LiteralPath (Join-Path $Root 'mpv.exe') -PathType Leaf)) {
        $missing += 'mpv.exe'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $Root 'portable_config') -PathType Container)) {
        $missing += 'portable_config/'
    }

    if ($Backends.Count -gt 0) {
        if (-not (Test-Path -LiteralPath (Join-Path $Root 'portable_config\vs') -PathType Container)) {
            $missing += 'portable_config/vs/'
        }

        foreach ($backend in $Backends) {
            $scriptName = Get-BackendScriptName -Backend $backend
            $scriptPath = Join-Path $Root "portable_config\vs\$scriptName"
            if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
                $missing += "portable_config/vs/$scriptName"
            }
        }
    }

    if ($missing.Count -gt 0) {
        throw "Runtime payload is incomplete at $Root. Missing:`n - $($missing -join "`n - ")"
    }
}

function Assert-RifeBackends {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [string[]]$Backends
    )

    if ($Backends.Count -eq 0) {
        return
    }

    $missing = @()
    if (-not (Test-Path -LiteralPath (Join-Path $Root 'portable_config\vs') -PathType Container)) {
        $missing += 'portable_config/vs/'
    }

    foreach ($backend in $Backends) {
        $scriptName = Get-BackendScriptName -Backend $backend
        $scriptPath = Join-Path $Root "portable_config\vs\$scriptName"
        if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
            $missing += "portable_config/vs/$scriptName"
        }
    }

    if ($missing.Count -gt 0) {
        throw "RIFE overlay is incomplete at $Root. Missing:`n - $($missing -join "`n - ")"
    }
}

function Copy-RuntimePayload {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Target,
        [switch]$Merge,
        [switch]$Overwrite
    )

    $targetFullPath = [System.IO.Path]::GetFullPath($Target)
    $targetRoot = [System.IO.Path]::GetPathRoot($targetFullPath)
    if ($targetFullPath.TrimEnd('\', '/') -eq $targetRoot.TrimEnd('\', '/')) {
        throw "Refusing to overwrite filesystem root: $targetFullPath"
    }

    if ((Test-Path -LiteralPath $Target) -and -not $Overwrite -and -not $Merge) {
        throw "Destination already exists: $Target. Use -Force to overwrite it."
    }

    if ((Test-Path -LiteralPath $Target) -and -not $Merge) {
        Remove-Item -LiteralPath $Target -Recurse -Force
    }

    if (-not (Test-Path -LiteralPath $Target)) {
        New-Item -ItemType Directory -Path $Target | Out-Null
    }
    Get-ChildItem -LiteralPath $Root -Force |
        Copy-Item -Destination $Target -Recurse -Force
}

if (-not (Test-Path -LiteralPath $Source)) {
    throw "Source does not exist: $Source"
}

Assert-SourceHash -Path $Source -Expected $ExpectedSha256

$requiredBackends = Get-RequiredRifeBackends -Value $RequiredRifeBackends
$workingRoot = if ((Get-Item -LiteralPath $Source).PSIsContainer) {
    Resolve-RuntimeRoot -Path $Source
} else {
    Resolve-RuntimeRoot -Path (Expand-RuntimeArchive -ArchivePath $Source)
}

Assert-RuntimePayload -Root $workingRoot -Backends (@())
Copy-RuntimePayload -Root $workingRoot -Target $Destination -Overwrite:$Force

if (-not [string]::IsNullOrWhiteSpace($OverlaySource)) {
    if (-not (Test-Path -LiteralPath $OverlaySource)) {
        throw "Overlay source does not exist: $OverlaySource"
    }
    $overlayRoot = if ((Get-Item -LiteralPath $OverlaySource).PSIsContainer) {
        Resolve-OverlayRoot -Path $OverlaySource
    } else {
        Resolve-OverlayRoot -Path (Expand-RuntimeArchive -ArchivePath $OverlaySource)
    }

    Assert-RifeBackends -Root $overlayRoot -Backends $requiredBackends
    Copy-RuntimePayload -Root $overlayRoot -Target $Destination -Merge
}

Assert-RuntimePayload -Root $Destination -Backends $requiredBackends

$manifest = [ordered]@{
    source = (Resolve-Path -LiteralPath $Source).Path
    overlaySource = if ([string]::IsNullOrWhiteSpace($OverlaySource)) { $null } else { (Resolve-Path -LiteralPath $OverlaySource).Path }
    runtimeRoot = (Resolve-Path -LiteralPath $Destination).Path
    requiredRifeBackends = $requiredBackends
    verifiedAt = (Get-Date).ToString('o')
    files = @(
        'mpv.exe'
        'portable_config/'
    ) + ($requiredBackends | ForEach-Object { 'portable_config/vs/' + (Get-BackendScriptName -Backend $_) })
}

$manifestPath = Join-Path $Destination 'runtime-manifest.json'
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
Write-Host "Prepared runtime payload at $Destination"
Write-Host "Manifest: $manifestPath"
