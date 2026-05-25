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