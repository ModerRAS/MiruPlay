$script:DesktopSmokeDefaultPollMilliseconds = 300

function Resolve-DesktopSmokeFullPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Read-DesktopSmokeStoreState {
    param(
        [string]$Path,
        [switch]$IgnoreParseErrors
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        if ($IgnoreParseErrors) {
            return $null
        }
        throw
    }
}

function Wait-DesktopSmokeStoreState {
    param(
        [string]$Path,
        [scriptblock]$Predicate,
        [string]$Description,
        [int]$TimeoutSeconds = 20,
        [int]$PollMilliseconds = $script:DesktopSmokeDefaultPollMilliseconds,
        [switch]$IgnoreParseErrors
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = Read-DesktopSmokeStoreState -Path $Path -IgnoreParseErrors:$IgnoreParseErrors
        if ($state -and (& $Predicate $state)) {
            return $state
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Description in $Path."
}
