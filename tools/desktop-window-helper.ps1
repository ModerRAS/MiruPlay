$script:MiruPlayDesktopWindowTitlePatterns = @(
    '*MiruPlay Desktop*',
    '*MiruPlay 桌面版*'
)

function Test-IsMiruPlayDesktopWindow {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process) {
        return $false
    }
    if ($Process.MainWindowHandle -eq 0) {
        return $false
    }

    $title = $Process.MainWindowTitle
    if (-not [string]::IsNullOrWhiteSpace($title)) {
        foreach ($pattern in $script:MiruPlayDesktopWindowTitlePatterns) {
            if ($title -like $pattern) {
                return $true
            }
        }
    }

    return $Process.ProcessName -eq 'java' -and $title -like '*MiruPlay*'
}

function Get-MiruPlayDesktopWindowProcess {
    Get-Process |
        Where-Object { Test-IsMiruPlayDesktopWindow $_ } |
        Select-Object -First 1
}

function Get-MiruPlayDesktopWindowProcesses {
    Get-Process |
        Where-Object { Test-IsMiruPlayDesktopWindow $_ }
}

function Wait-MiruPlayDesktopWindowProcess {
    param(
        [int]$TimeoutSeconds = 30,
        [string]$FailureMessage = ''
    )

    $effectiveFailureMessage = if ([string]::IsNullOrWhiteSpace($FailureMessage)) {
        "MiruPlay Desktop window did not appear within $TimeoutSeconds seconds."
    } else {
        $FailureMessage
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $process = Get-MiruPlayDesktopWindowProcess
        if ($process) {
            return $process
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)

    throw $effectiveFailureMessage
}

function Stop-MiruPlayDesktopProcessIfRunning {
    param(
        [System.Diagnostics.Process]$Process,
        [switch]$CloseMainWindow,
        [int]$CloseWaitMilliseconds = 700
    )

    if ($null -eq $Process) {
        return
    }

    try {
        $processId = $Process.Id
    } catch {
        return
    }

    $runningProcess = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $runningProcess) {
        return
    }

    if ($CloseMainWindow -and $runningProcess.MainWindowHandle -ne 0) {
        $runningProcess.CloseMainWindow() | Out-Null
        Start-Sleep -Milliseconds $CloseWaitMilliseconds
        $runningProcess = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $runningProcess) {
            return
        }
    }

    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}

function Close-MiruPlayDesktopWindowProcessIfRunning {
    param([int]$CloseWaitMilliseconds = 700)

    $windowProcess = Get-MiruPlayDesktopWindowProcess
    Stop-MiruPlayDesktopProcessIfRunning `
        -Process $windowProcess `
        -CloseMainWindow `
        -CloseWaitMilliseconds $CloseWaitMilliseconds
}
