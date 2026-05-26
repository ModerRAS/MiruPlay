[CmdletBinding()]
param(
    [string]$AppScript,
    [string]$OutputRoot,
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"

$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    $PSScriptRoot
}
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot "..\..\.."))
. (Join-Path $scriptRoot "desktop-window-helper.ps1")
. (Join-Path $scriptRoot "desktop-smoke-common.ps1")
if ([string]::IsNullOrWhiteSpace($AppScript)) {
    $AppScript = Join-Path $repoRoot "desktop-app\build\install\desktop-app\bin\desktop-app.bat"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot "build\desktop-bangumi-metadata-ui"
}
$bangumiFixtureTitle = ([char]0x846C) + ([char]0x9001) + ([char]0x7684) + ([char]0x8299) + ([char]0x8389) + ([char]0x83B2)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
if (-not ("MiruPlayBangumiMetadataSmokeWin32" -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlayBangumiMetadataSmokeWin32 {
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool SetCursorPos(int X, int Y);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, int data, UIntPtr extraInfo);
}
"@
}

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlayBangumiMetadataSmokeWin32+RECT
    if (-not [MiruPlayBangumiMetadataSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
        throw "Unable to read window bounds for process $($Process.Id)."
    }
    return $rect
}

function Invoke-RelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y,
        [int]$DelayMilliseconds = 350
    )

    $rect = Get-WindowRect -Process $Process
    [MiruPlayBangumiMetadataSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
    [MiruPlayBangumiMetadataSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlayBangumiMetadataSmokeWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlayBangumiMetadataSmokeWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds $DelayMilliseconds
}

function Send-AppKeys {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Keys,
        [int]$DelayMilliseconds = 350
    )

    [MiruPlayBangumiMetadataSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 120
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
    Start-Sleep -Milliseconds $DelayMilliseconds
}

function Assert-ScreenshotHasContent {
    param([string]$Path)

    Assert-DesktopSmokeScreenshotQuality -Path $Path
}

function Save-WindowScreenshot {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Path
    )

    $rect = Get-WindowRect -Process $Process
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    if ($width -lt 1100 -or $height -lt 700) {
        throw "Window is smaller than expected for TV-style QA: ${width}x$height"
    }

    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
    Assert-ScreenshotHasContent -Path $Path
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse("127.0.0.1"), 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Start-BangumiFixtureServer {
    param(
        [int]$Port,
        [string]$ReadyFile,
        [string]$RequestLogFile
    )

    Start-Job -Name "MiruPlayBangumiFixture-$Port" -ScriptBlock {
        param(
            [int]$Port,
            [string]$ReadyFile,
            [string]$RequestLogFile
        )

        $ErrorActionPreference = "Stop"
        $titleCn = ([char]0x846C) + ([char]0x9001) + ([char]0x7684) + ([char]0x8299) + ([char]0x8389) + ([char]0x83B2)
        $titleJa = ([char]0x846C) + ([char]0x9001) + ([char]0x306E) + ([char]0x30D5) + ([char]0x30EA) + ([char]0x30FC) + ([char]0x30EC) + ([char]0x30F3)

        function New-JsonBytes {
            param([object]$Payload)

            $json = $Payload | ConvertTo-Json -Depth 12 -Compress
            return [System.Text.Encoding]::UTF8.GetBytes($json)
        }

        function New-SearchResponseBytes {
            New-JsonBytes -Payload @{
                data = @(
                    @{
                        id = 400602
                        name = $titleJa
                        name_cn = $titleCn
                        rating = @{ score = 8.8 }
                        infobox = @(
                            @{ key = "中文名"; value = $titleCn }
                        )
                    }
                )
            }
        }

        function New-SubjectResponseBytes {
            New-JsonBytes -Payload @{
                id = 400602
                name = $titleJa
                name_cn = $titleCn
                summary = "Fixture Bangumi subject used by Windows desktop behavior tests."
                eps = 28
                total_episodes = 28
                date = "2023-09-29"
                rating = @{ score = 8.8 }
                images = @{
                    large = "http://127.0.0.1:$Port/assets/frieren-large.jpg"
                    common = "http://127.0.0.1:$Port/assets/frieren-common.jpg"
                }
                collection = @{ doing = 0 }
                tags = @(
                    @{ name = "Fantasy" },
                    @{ name = "Adventure" }
                )
            }
        }

        function New-EpisodesResponseBytes {
            New-JsonBytes -Payload @{
                total = 1
                data = @(
                    @{
                        id = 40060201
                        type = 0
                        ep = 1
                        sort = 1
                        name = "Fixture Metadata Episode"
                        name_cn = "Fixture Metadata Episode"
                        airdate = "2023-09-29"
                        desc = "Fixture episode returned by the local Bangumi mock."
                        duration_seconds = 1440
                    }
                )
            }
        }

        function Send-Response {
            param(
                [System.Net.Sockets.TcpClient]$Client,
                [string]$Status,
                [byte[]]$Body = [byte[]]::new(0),
                [string]$ContentType = "application/json; charset=utf-8"
            )

            $stream = $Client.GetStream()
            $headerText = "HTTP/1.1 $Status`r`nContent-Length: $($Body.Length)`r`nContent-Type: $ContentType`r`nConnection: close`r`nDate: $([DateTime]::UtcNow.ToString("R"))`r`n`r`n"
            $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($headerText)
            $stream.Write($headerBytes, 0, $headerBytes.Length)
            if ($Body.Length -gt 0) {
                $stream.Write($Body, 0, $Body.Length)
            }
            $stream.Flush()
        }

        function Read-Request {
            param([System.Net.Sockets.TcpClient]$Client)

            $Client.ReceiveTimeout = 10000
            $Client.SendTimeout = 10000
            $stream = $Client.GetStream()
            $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
            $requestLine = $reader.ReadLine()
            if ([string]::IsNullOrWhiteSpace($requestLine)) {
                return $null
            }
            $parts = $requestLine.Split(" ")
            if ($parts.Count -lt 2) {
                return $null
            }

            while ($true) {
                $line = $reader.ReadLine()
                if ($null -eq $line -or $line.Length -eq 0) {
                    break
                }
            }

            return [pscustomobject]@{
                Method = $parts[0].ToUpperInvariant()
                Target = $parts[1]
            }
        }

        function Get-RequestPath {
            param([string]$Target)

            $targetPath = $Target.Split("?")[0]
            if ($targetPath -match "^[a-zA-Z][a-zA-Z0-9+.-]*://") {
                $targetPath = ([Uri]$targetPath).AbsolutePath
            }
            return [Uri]::UnescapeDataString($targetPath)
        }

        function Write-RequestLog {
            param(
                [string]$Method,
                [string]$Target
            )

            Add-Content -LiteralPath $RequestLogFile -Value "$Method|$(Get-RequestPath -Target $Target)|$Target"
        }

        New-Item -ItemType Directory -Path (Split-Path -Parent $ReadyFile) -Force | Out-Null
        New-Item -ItemType Directory -Path (Split-Path -Parent $RequestLogFile) -Force | Out-Null
        Set-Content -LiteralPath $RequestLogFile -Value ""

        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse("127.0.0.1"), $Port)
        $listener.Start()
        Set-Content -LiteralPath $ReadyFile -Value "ready"
        try {
            while ($true) {
                $client = $listener.AcceptTcpClient()
                try {
                    $request = Read-Request -Client $client
                    if ($null -eq $request) {
                        continue
                    }

                    $path = Get-RequestPath -Target $request.Target
                    Write-RequestLog -Method $request.Method -Target $request.Target
                    if ($request.Method -eq "POST" -and $path -eq "/v0/search/subjects") {
                        Send-Response -Client $client -Status "200 OK" -Body (New-SearchResponseBytes)
                    } elseif ($request.Method -eq "GET" -and $path -eq "/v0/subjects/400602") {
                        Send-Response -Client $client -Status "200 OK" -Body (New-SubjectResponseBytes)
                    } elseif ($request.Method -eq "GET" -and $path -eq "/v0/episodes") {
                        Send-Response -Client $client -Status "200 OK" -Body (New-EpisodesResponseBytes)
                    } else {
                        Send-Response -Client $client -Status "404 Not Found" -Body ([System.Text.Encoding]::UTF8.GetBytes('{"error":"not found"}'))
                    }
                } catch {
                    try {
                        Send-Response -Client $client -Status "500 Internal Server Error" -Body ([System.Text.Encoding]::UTF8.GetBytes("{`"error`":`"$($_.Exception.Message)`"}"))
                    } catch {
                    }
                    Add-Content -LiteralPath $RequestLogFile -Value "ERROR|$($_.Exception.Message)"
                } finally {
                    $client.Close()
                }
            }
        } finally {
            $listener.Stop()
        }
    } -ArgumentList $Port, $ReadyFile, $RequestLogFile
}

function Wait-BangumiFixtureServerReady {
    param(
        [object]$Job,
        [string]$ReadyFile,
        [int]$TimeoutSeconds = 10
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (Test-Path -LiteralPath $ReadyFile) {
            return
        }
        if ($Job.State -ne "Running") {
            $output = Receive-Job -Job $Job -Keep | Out-String
            throw "Bangumi fixture server exited before becoming ready. $output"
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)

    $output = Receive-Job -Job $Job -Keep | Out-String
    throw "Timed out waiting for Bangumi fixture server. $output"
}

function Wait-BangumiFixtureRequest {
    param(
        [string]$RequestLogFile,
        [string]$Pattern,
        [string]$Description,
        [int]$TimeoutSeconds = 10
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (Test-Path -LiteralPath $RequestLogFile) {
            $content = Get-Content -LiteralPath $RequestLogFile -Raw
            if ($content -match $Pattern) {
                return
            }
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Description in $RequestLogFile."
}

function Write-InitialStore {
    param(
        [string]$Path,
        [string]$LibraryRoot,
        [string]$EpisodePath
    )

    $json = @{
        nextSourceId = 2
        nextRssSubscriptionId = 1
        nextRssDownloadTaskId = 1
        mediaSources = @(
            @{
                id = 1
                name = "Bangumi Fixture"
                type = "LOCAL"
                connectionInfo = @{
                    path = $LibraryRoot
                }
                isConnected = $true
                lastScanned = 0
            }
        )
        progress = @()
        index = @(
            @{
                sourceId = 1
                path = $EpisodePath
                animeName = $bangumiFixtureTitle
                episodeTitle = "Fixture Metadata Episode"
                plot = "Fixture plot for Bangumi metadata GUI smoke."
                seasonNumber = 1
                episodeNumber = 1
                metadataSource = $null
                metadataId = $null
                metadataTitle = $null
                isDirectory = $false
                fileSize = 24
                lastModified = 0
            }
        )
        indexBatchUndo = @()
        cloudDriveConfig = @{
            endpointUrl = ""
            username = ""
            webDavSourceId = $null
            inboxPath = ""
            libraryPath = ""
            intervalMinutes = 30
            enabled = $false
            lastRunAt = 0
            rssProxyEnabled = $false
            rssProxyHost = ""
            rssProxyPort = 1080
        }
        rssSubscriptions = @()
        rssProcessedItems = @()
        rssDownloadTasks = @()
        cloudDriveToken = $null
        cloudDrivePassword = $null
        bangumiAccessToken = $null
    } | ConvertTo-Json -Depth 12

    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
    Set-Content -LiteralPath $Path -Value $json -Encoding UTF8
}

$resolvedAppScript = Resolve-DesktopSmokeFullPath $AppScript
$resolvedOutputRoot = Resolve-DesktopSmokeFullPath $OutputRoot
if (-not (Test-Path -LiteralPath $resolvedAppScript)) {
    throw "Desktop app launcher was not found at $resolvedAppScript. Run :desktop-app:installDist first."
}
if (Get-MiruPlayDesktopWindowProcess) {
    throw "A MiruPlay Desktop window is already open. Close it before running this isolated smoke test."
}

$runName = "run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$runDir = Join-Path $resolvedOutputRoot $runName
$fixtureDir = Join-Path $runDir "media\Frieren"
$storePath = Join-Path $runDir "store\desktop-store.json"
$episodePath = Join-Path $fixtureDir "Frieren - S01E01.mkv"
$detailsScreenshotPath = Join-Path $runDir "bangumi-details-ready.png"
$focusBangumiScreenshotPath = Join-Path $runDir "bangumi-focus-bangumi.png"
$searchScreenshotPath = Join-Path $runDir "bangumi-search-results.png"
$appliedScreenshotPath = Join-Path $runDir "bangumi-metadata-applied.png"
$clearedScreenshotPath = Join-Path $runDir "bangumi-metadata-cleared.png"
$mockReadyPath = Join-Path $runDir "bangumi-mock-ready.txt"
$mockRequestLogPath = Join-Path $runDir "bangumi-mock-requests.log"
New-Item -ItemType Directory -Path $fixtureDir -Force | Out-Null
Set-Content -LiteralPath $episodePath -Value "fixture metadata video bytes" -Encoding UTF8
Write-InitialStore -Path $storePath -LibraryRoot (Split-Path -Parent $fixtureDir) -EpisodePath $episodePath

$previousStoreEnv = $env:MIRUPLAY_DESKTOP_STORE
$previousBangumiBaseUrlEnv = $env:MIRUPLAY_BANGUMI_BASE_URL
$startedProcess = $null
$bangumiMockJob = $null
try {
    $mockPort = Get-FreeLoopbackPort
    $bangumiMockJob = Start-BangumiFixtureServer -Port $mockPort -ReadyFile $mockReadyPath -RequestLogFile $mockRequestLogPath
    Wait-BangumiFixtureServerReady -Job $bangumiMockJob -ReadyFile $mockReadyPath
    $env:MIRUPLAY_DESKTOP_STORE = $storePath
    $env:MIRUPLAY_BANGUMI_BASE_URL = "http://127.0.0.1:$mockPort/"
    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayDesktopWindowProcess

    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 900
    Save-WindowScreenshot -Process $windowProcess -Path $detailsScreenshotPath

    Send-AppKeys -Process $windowProcess -Keys "{DOWN}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{DOWN}" -DelayMilliseconds 350
    Save-WindowScreenshot -Process $windowProcess -Path $focusBangumiScreenshotPath
    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 350
    Send-AppKeys -Process $windowProcess -Keys "{RIGHT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 3500
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "metadata still clear after search" -Predicate {
        param($state)
        $entry = @($state.index | Where-Object { -not $_.isDirectory })[0]
        $entry.metadataId -eq $null -and $entry.animeName -eq $bangumiFixtureTitle
    }
    Save-WindowScreenshot -Process $windowProcess -Path $searchScreenshotPath

    Send-AppKeys -Process $windowProcess -Keys "{RIGHT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{RIGHT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{LEFT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 900
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "applied Bangumi metadata" -Predicate {
        param($state)
        $entry = @($state.index | Where-Object { -not $_.isDirectory })[0]
        $entry.metadataSource -eq "BANGUMI" -and
            $entry.metadataId -eq "400602" -and
            $entry.metadataTitle -eq $bangumiFixtureTitle
    } -TimeoutSeconds 20
    Save-WindowScreenshot -Process $windowProcess -Path $appliedScreenshotPath

    Send-AppKeys -Process $windowProcess -Keys "{RIGHT}" -DelayMilliseconds 250
    Send-AppKeys -Process $windowProcess -Keys "{ENTER}" -DelayMilliseconds 900
    $state = Wait-DesktopSmokeStoreState -Path $storePath -Description "cleared Bangumi metadata" -Predicate {
        param($state)
        $entry = @($state.index | Where-Object { -not $_.isDirectory })[0]
        $entry.metadataSource -eq $null -and
            $entry.metadataId -eq $null -and
            $entry.metadataTitle -eq $null
    } -TimeoutSeconds 20
    Save-WindowScreenshot -Process $windowProcess -Path $clearedScreenshotPath
    Wait-BangumiFixtureRequest -RequestLogFile $mockRequestLogPath -Pattern "POST\|/v0/search/subjects" -Description "Bangumi search request to local mock"
} finally {
    $env:MIRUPLAY_DESKTOP_STORE = $previousStoreEnv
    if ($null -eq $previousBangumiBaseUrlEnv) {
        Remove-Item Env:\MIRUPLAY_BANGUMI_BASE_URL -ErrorAction SilentlyContinue
    } else {
        $env:MIRUPLAY_BANGUMI_BASE_URL = $previousBangumiBaseUrlEnv
    }
    if (-not $KeepOpen) {
        $windowProcess = Get-MiruPlayDesktopWindowProcess
        if ($windowProcess) {
            $windowProcess.CloseMainWindow() | Out-Null
            Start-Sleep -Milliseconds 700
            if (-not $windowProcess.HasExited) {
                Stop-Process -Id $windowProcess.Id -Force
            }
        }
        if ($startedProcess -and -not $startedProcess.HasExited) {
            Stop-Process -Id $startedProcess.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($bangumiMockJob) {
        Stop-Job -Job $bangumiMockJob -ErrorAction SilentlyContinue
        Receive-Job -Job $bangumiMockJob -ErrorAction SilentlyContinue | Out-Null
        Remove-Job -Job $bangumiMockJob -Force -ErrorAction SilentlyContinue
    }
}

Write-Output "Run directory: $runDir"
Write-Output "Store: $storePath"
Write-Output "Details screenshot: $detailsScreenshotPath"
Write-Output "Bangumi focus screenshot: $focusBangumiScreenshotPath"
Write-Output "Search screenshot: $searchScreenshotPath"
Write-Output "Applied screenshot: $appliedScreenshotPath"
Write-Output "Cleared screenshot: $clearedScreenshotPath"
Write-Output "Bangumi mock request log: $mockRequestLogPath"
