[CmdletBinding()]
param(
    [string]$AppScript = (Join-Path $PSScriptRoot "..\desktop-app\build\install\desktop-app\bin\desktop-app.bat"),
    [string]$OutputRoot = (Join-Path $PSScriptRoot "..\build\desktop-webdav-source-ui"),
    [switch]$KeepOpen
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
if (-not ("MiruPlayWebDavSourceSmokeWin32" -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class MiruPlayWebDavSourceSmokeWin32 {
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

function Resolve-FullPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Get-MiruPlayWindowProcess {
    Get-Process |
        Where-Object { $_.MainWindowTitle -like "*MiruPlay Desktop*" -and $_.MainWindowHandle -ne 0 } |
        Select-Object -First 1
}

function Wait-MiruPlayWindow {
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $process = Get-MiruPlayWindowProcess
        if ($process) {
            return $process
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)

    throw "MiruPlay Desktop window did not appear within 30 seconds."
}

function Get-WindowRect {
    param([System.Diagnostics.Process]$Process)
    $rect = New-Object MiruPlayWebDavSourceSmokeWin32+RECT
    if (-not [MiruPlayWebDavSourceSmokeWin32]::GetWindowRect($Process.MainWindowHandle, [ref]$rect)) {
        throw "Unable to read window bounds for process $($Process.Id)."
    }
    return $rect
}

function Invoke-RelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y
    )

    $rect = Get-WindowRect -Process $Process
    [MiruPlayWebDavSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 150
    [MiruPlayWebDavSourceSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    [MiruPlayWebDavSourceSmokeWin32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [MiruPlayWebDavSourceSmokeWin32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 350
}

function Invoke-RelativeMouseWheel {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X = 650,
        [int]$Y = 500,
        [int]$Notches,
        [int]$DeltaPerNotch = 420
    )

    $rect = Get-WindowRect -Process $Process
    [MiruPlayWebDavSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    [MiruPlayWebDavSourceSmokeWin32]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
    Start-Sleep -Milliseconds 150
    $direction = if ($Notches -lt 0) { -1 } else { 1 }
    for ($i = 0; $i -lt [Math]::Abs($Notches); $i++) {
        [MiruPlayWebDavSourceSmokeWin32]::mouse_event(0x0800, 0, 0, $direction * $DeltaPerNotch, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
    }
    Start-Sleep -Milliseconds 500
}

function Send-AppKeys {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Keys,
        [int]$DelayMilliseconds = 350
    )

    [MiruPlayWebDavSourceSmokeWin32]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 120
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
    Start-Sleep -Milliseconds $DelayMilliseconds
}

function Set-FocusedText {
    param([string]$Text)
    Set-Clipboard -Value $Text
    [System.Windows.Forms.SendKeys]::SendWait("^a")
    Start-Sleep -Milliseconds 80
    [System.Windows.Forms.SendKeys]::SendWait("^v")
    Start-Sleep -Milliseconds 350
}

function Get-FocusedText {
    $before = Get-Clipboard -Raw -ErrorAction SilentlyContinue
    try {
        Set-Clipboard -Value "__MIRUPLAY_EMPTY_SELECTION__"
        [System.Windows.Forms.SendKeys]::SendWait("^a")
        Start-Sleep -Milliseconds 80
        [System.Windows.Forms.SendKeys]::SendWait("^c")
        Start-Sleep -Milliseconds 200
        $text = (Get-Clipboard -Raw -ErrorAction SilentlyContinue).Trim()
        if ($text -eq "__MIRUPLAY_EMPTY_SELECTION__") {
            return ""
        }
        return $text
    } finally {
        if ($null -ne $before) {
            Set-Clipboard -Value $before
        }
    }
}

function Set-TextByRelativeClick {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$X,
        [int]$Y,
        [string]$Text,
        [string]$Description,
        [int]$Attempts = 3
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        Invoke-RelativeClick -Process $Process -X $X -Y $Y
        Set-FocusedText -Text $Text
        $actual = Get-FocusedText
        if ($actual -eq $Text) {
            return
        }
        Start-Sleep -Milliseconds 300
    }

    throw "Unable to set $Description to '$Text'."
}

function Read-StoreState {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Wait-StoreState {
    param(
        [string]$Path,
        [scriptblock]$Predicate,
        [string]$Description,
        [int]$TimeoutSeconds = 20
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = Read-StoreState -Path $Path
        if ($state -and (& $Predicate $state)) {
            return $state
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Description in $Path."
}

function Wait-FileText {
    param(
        [string]$Path,
        [string]$Pattern,
        [string]$Description,
        [int]$TimeoutSeconds = 20
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (Test-Path -LiteralPath $Path) {
            $text = Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue
            if ($text -match $Pattern) {
                return
            }
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Description in $Path."
}

function Assert-ScreenshotHasContent {
    param([string]$Path)
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -lt 20000) {
        throw "Screenshot file is unexpectedly small: $Path ($($file.Length) bytes)"
    }

    $bitmap = [System.Drawing.Bitmap]::FromFile($Path)
    try {
        $colors = New-Object 'System.Collections.Generic.HashSet[int]'
        $redAccentPixels = 0
        $brightTextPixels = 0
        $xStep = [Math]::Max(1, [int]($bitmap.Width / 96))
        $yStep = [Math]::Max(1, [int]($bitmap.Height / 64))
        for ($x = 0; $x -lt $bitmap.Width; $x += $xStep) {
            for ($y = 0; $y -lt $bitmap.Height; $y += $yStep) {
                $pixel = $bitmap.GetPixel($x, $y)
                [void]$colors.Add($pixel.ToArgb())
                $r = [int]$pixel.R
                $g = [int]$pixel.G
                $b = [int]$pixel.B
                if ($r -ge 150 -and $g -ge 35 -and $g -le 125 -and $b -ge 45 -and $b -le 155 -and ($r - $g) -ge 55) {
                    $redAccentPixels++
                }
                if ($r -ge 140 -and $g -ge 140 -and $b -ge 140) {
                    $brightTextPixels++
                }
            }
        }
        if ($colors.Count -lt 28) {
            throw "Screenshot appears blank or nearly blank: $Path"
        }
        if ($redAccentPixels -lt 8) {
            throw "Screenshot is missing the expected MiruPlay red accent: $Path"
        }
        if ($brightTextPixels -lt 8) {
            throw "Screenshot has too little readable light text: $Path"
        }
    } finally {
        $bitmap.Dispose()
    }
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

function Start-WebDavFixtureServer {
    param(
        [int]$Port,
        [string]$ReadyFile,
        [string]$RequestLogFile,
        [string]$Username,
        [string]$Password
    )

    Start-Job -Name "MiruPlayWebDavFixture-$Port" -ScriptBlock {
        param(
            [int]$Port,
            [string]$ReadyFile,
            [string]$RequestLogFile,
            [string]$Username,
            [string]$Password
        )

        $ErrorActionPreference = "Stop"
        $showName = "Fixture WebDAV"
        $dirPath = $showName
        $videoName = "$showName - S01E02.mkv"
        $episodeNfoName = "$showName - S01E02.nfo"
        $videoPath = "$dirPath/$videoName"
        $episodeNfoPath = "$dirPath/$episodeNfoName"
        $tvShowNfoPath = "$dirPath/tvshow.nfo"
        $lastModified = "Tue, 19 May 2026 00:00:00 GMT"
        $videoBytes = [System.Text.Encoding]::UTF8.GetBytes("fixture webdav video bytes")
        $tvShowNfoBytes = [System.Text.Encoding]::UTF8.GetBytes(@"
<tvshow>
  <title>Fixture WebDAV</title>
  <originaltitle>Fixture WebDAV Original</originaltitle>
</tvshow>
"@)
        $episodeNfoBytes = [System.Text.Encoding]::UTF8.GetBytes(@"
<episodedetails>
  <showtitle>Fixture WebDAV</showtitle>
  <title>Remote Fixture Episode</title>
  <season>1</season>
  <episode>2</episode>
  <plot>Fixture WebDAV plot for desktop GUI smoke.</plot>
</episodedetails>
"@)
        $expectedAuth = "Basic " + [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("${Username}:${Password}"))

        function Escape-Xml {
            param([string]$Value)
            return [System.Security.SecurityElement]::Escape($Value)
        }

        function Encode-HrefPath {
            param(
                [string]$Path,
                [bool]$IsDirectory
            )
            if ([string]::IsNullOrWhiteSpace($Path)) {
                return "/dav/"
            }
            $encoded = ($Path.Trim("/") -split "/" | ForEach-Object { [Uri]::EscapeDataString($_) }) -join "/"
            $href = "/dav/$encoded"
            if ($IsDirectory) {
                return "$href/"
            }
            return $href
        }

        function New-PropfindEntry {
            param(
                [string]$Path,
                [string]$Name,
                [bool]$IsDirectory,
                [long]$Length = 0,
                [string]$ContentType = ""
            )

            $href = Encode-HrefPath -Path $Path -IsDirectory $IsDirectory
            $displayName = Escape-Xml -Value $Name
            $resourceType = if ($IsDirectory) { "<d:resourcetype><d:collection/></d:resourcetype>" } else { "<d:resourcetype/>" }
            $fileProps = if ($IsDirectory) {
                ""
            } else {
                "<d:getcontentlength>$Length</d:getcontentlength><d:getcontenttype>$(Escape-Xml -Value $ContentType)</d:getcontenttype>"
            }
            return @"
<d:response>
  <d:href>$href</d:href>
  <d:propstat>
    <d:prop>
      <d:displayname>$displayName</d:displayname>
      <d:getlastmodified>$lastModified</d:getlastmodified>
      $resourceType
      $fileProps
    </d:prop>
    <d:status>HTTP/1.1 200 OK</d:status>
  </d:propstat>
</d:response>
"@
        }

        function New-Multistatus {
            param([string[]]$Entries)
            $joined = $Entries -join "`n"
            return "<?xml version=`"1.0`" encoding=`"utf-8`"?><d:multistatus xmlns:d=`"DAV:`">$joined</d:multistatus>"
        }

        function Send-Response {
            param(
                [System.Net.Sockets.TcpClient]$Client,
                [string]$Status,
                [byte[]]$Body = [byte[]]::new(0),
                [string]$ContentType = "text/plain; charset=utf-8",
                [hashtable]$Headers = @{}
            )
            $stream = $Client.GetStream()
            $headerText = "HTTP/1.1 $Status`r`nContent-Length: $($Body.Length)`r`nContent-Type: $ContentType`r`nConnection: close`r`nDate: $([DateTime]::UtcNow.ToString("R"))`r`n"
            foreach ($key in $Headers.Keys) {
                $headerText += "${key}: $($Headers[$key])`r`n"
            }
            $headerText += "`r`n"
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
            $headers = @{}
            while ($true) {
                $line = $reader.ReadLine()
                if ($null -eq $line -or $line.Length -eq 0) {
                    break
                }
                $colon = $line.IndexOf(":")
                if ($colon -gt 0) {
                    $headers[$line.Substring(0, $colon).ToLowerInvariant()] = $line.Substring($colon + 1).Trim()
                }
            }
            return [pscustomobject]@{
                Method = $parts[0].ToUpperInvariant()
                Target = $parts[1]
                Headers = $headers
            }
        }

        function Normalize-RequestPath {
            param([string]$Target)
            $targetPath = $Target.Split("?")[0]
            if ($targetPath -match "^[a-zA-Z][a-zA-Z0-9+.-]*://") {
                $targetPath = ([Uri]$targetPath).AbsolutePath
            }
            $decodedPath = [Uri]::UnescapeDataString($targetPath)
            if ($decodedPath -eq "/dav") {
                $decodedPath = "/dav/"
            }
            if (-not $decodedPath.StartsWith("/dav/")) {
                return $null
            }
            return $decodedPath.Substring(5).Trim("/")
        }

        function Write-RequestLog {
            param(
                [string]$Method,
                [string]$Path,
                [bool]$Authorized
            )
            Add-Content -LiteralPath $RequestLogFile -Value "$Method|$Path|auth=$Authorized"
        }

        function Send-Propfind {
            param(
                [System.Net.Sockets.TcpClient]$Client,
                [string]$Path,
                [string]$Depth
            )
            $includeChildren = $Depth -ne "0"
            $entries = New-Object 'System.Collections.Generic.List[string]'
            if ([string]::IsNullOrWhiteSpace($Path)) {
                $entries.Add((New-PropfindEntry -Path "" -Name "" -IsDirectory $true))
                if ($includeChildren) {
                    $entries.Add((New-PropfindEntry -Path $dirPath -Name $showName -IsDirectory $true))
                }
            } elseif ($Path -eq $dirPath) {
                $entries.Add((New-PropfindEntry -Path $dirPath -Name $showName -IsDirectory $true))
                if ($includeChildren) {
                    $entries.Add((New-PropfindEntry -Path $tvShowNfoPath -Name "tvshow.nfo" -IsDirectory $false -Length $tvShowNfoBytes.Length -ContentType "application/xml"))
                    $entries.Add((New-PropfindEntry -Path $videoPath -Name $videoName -IsDirectory $false -Length $videoBytes.Length -ContentType "video/x-matroska"))
                    $entries.Add((New-PropfindEntry -Path $episodeNfoPath -Name $episodeNfoName -IsDirectory $false -Length $episodeNfoBytes.Length -ContentType "application/xml"))
                }
            } elseif ($Path -eq $videoPath) {
                $entries.Add((New-PropfindEntry -Path $videoPath -Name $videoName -IsDirectory $false -Length $videoBytes.Length -ContentType "video/x-matroska"))
            } elseif ($Path -eq $tvShowNfoPath) {
                $entries.Add((New-PropfindEntry -Path $tvShowNfoPath -Name "tvshow.nfo" -IsDirectory $false -Length $tvShowNfoBytes.Length -ContentType "application/xml"))
            } elseif ($Path -eq $episodeNfoPath) {
                $entries.Add((New-PropfindEntry -Path $episodeNfoPath -Name $episodeNfoName -IsDirectory $false -Length $episodeNfoBytes.Length -ContentType "application/xml"))
            } else {
                Send-Response -Client $Client -Status "404 Not Found" -Body ([System.Text.Encoding]::UTF8.GetBytes("not found"))
                return
            }

            $body = [System.Text.Encoding]::UTF8.GetBytes((New-Multistatus -Entries $entries.ToArray()))
            Send-Response -Client $Client -Status "207 Multi-Status" -Body $body -ContentType "application/xml; charset=utf-8"
        }

        function Send-Get {
            param(
                [System.Net.Sockets.TcpClient]$Client,
                [string]$Path
            )
            if ($Path -eq $videoPath) {
                Send-Response -Client $Client -Status "200 OK" -Body $videoBytes -ContentType "video/x-matroska"
            } elseif ($Path -eq $tvShowNfoPath) {
                Send-Response -Client $Client -Status "200 OK" -Body $tvShowNfoBytes -ContentType "application/xml; charset=utf-8"
            } elseif ($Path -eq $episodeNfoPath) {
                Send-Response -Client $Client -Status "200 OK" -Body $episodeNfoBytes -ContentType "application/xml; charset=utf-8"
            } else {
                Send-Response -Client $Client -Status "404 Not Found" -Body ([System.Text.Encoding]::UTF8.GetBytes("not found"))
            }
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
                    $path = Normalize-RequestPath -Target $request.Target
                    $authorized = $request.Headers.ContainsKey("authorization") -and $request.Headers["authorization"] -eq $expectedAuth
                    Write-RequestLog -Method $request.Method -Path $path -Authorized $authorized
                    if ($null -eq $path) {
                        Send-Response -Client $client -Status "404 Not Found" -Body ([System.Text.Encoding]::UTF8.GetBytes("not found"))
                    } elseif (-not $authorized) {
                        Send-Response -Client $client -Status "401 Unauthorized" -Body ([System.Text.Encoding]::UTF8.GetBytes("auth required")) -Headers @{ "WWW-Authenticate" = "Basic realm=`"MiruPlay Fixture`"" }
                    } elseif ($request.Method -eq "PROPFIND") {
                        $depth = if ($request.Headers.ContainsKey("depth")) { $request.Headers["depth"] } else { "1" }
                        Send-Propfind -Client $client -Path $path -Depth $depth
                    } elseif ($request.Method -eq "GET") {
                        Send-Get -Client $client -Path $path
                    } else {
                        Send-Response -Client $client -Status "405 Method Not Allowed" -Body ([System.Text.Encoding]::UTF8.GetBytes("method not allowed"))
                    }
                } catch {
                    try {
                        Send-Response -Client $client -Status "500 Internal Server Error" -Body ([System.Text.Encoding]::UTF8.GetBytes($_.Exception.Message))
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
    } -ArgumentList $Port, $ReadyFile, $RequestLogFile, $Username, $Password
}

function Wait-WebDavServerReady {
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
            throw "WebDAV fixture server exited before becoming ready. $output"
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)

    $output = Receive-Job -Job $Job -Keep | Out-String
    throw "Timed out waiting for WebDAV fixture server. $output"
}

$resolvedAppScript = Resolve-FullPath $AppScript
$resolvedOutputRoot = Resolve-FullPath $OutputRoot
if (-not (Test-Path -LiteralPath $resolvedAppScript)) {
    throw "Desktop app launcher was not found at $resolvedAppScript. Run :desktop-app:installDist first."
}
if (Get-MiruPlayWindowProcess) {
    throw "A MiruPlay Desktop window is already open. Close it before running this isolated smoke test."
}

$runName = "run-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$runDir = Join-Path $resolvedOutputRoot $runName
$storePath = Join-Path $runDir "store\desktop-store.json"
$serverReadyPath = Join-Path $runDir "fixture-server.ready"
$serverLogPath = Join-Path $runDir "fixture-server.log"
$openScreenshotPath = Join-Path $runDir "webdav-source-opened.png"
$browseScreenshotPath = Join-Path $runDir "webdav-source-browsed.png"
$scanScreenshotPath = Join-Path $runDir "webdav-source-scanned.png"
$posterScreenshotPath = Join-Path $runDir "webdav-source-poster-wall.png"
$detailsScreenshotPath = Join-Path $runDir "webdav-source-details.png"
$playerScreenshotPath = Join-Path $runDir "webdav-source-player.png"
New-Item -ItemType Directory -Path (Split-Path -Parent $storePath) -Force | Out-Null

$webDavUsername = "fixture-user"
$webDavPassword = "fixture-secret"
$port = Get-FreeLoopbackPort
$webDavUrl = "http://127.0.0.1:$port/dav"
$expectedVideoPath = "/Fixture WebDAV/Fixture WebDAV - S01E02.mkv"
$webDavJob = $null
$previousClipboard = Get-Clipboard -Raw -ErrorAction SilentlyContinue
$previousStoreEnv = $env:MIRUPLAY_DESKTOP_STORE
$env:MIRUPLAY_DESKTOP_STORE = $storePath
$startedProcess = $null

try {
    $webDavJob = Start-WebDavFixtureServer -Port $port -ReadyFile $serverReadyPath -RequestLogFile $serverLogPath -Username $webDavUsername -Password $webDavPassword
    Wait-WebDavServerReady -Job $webDavJob -ReadyFile $serverReadyPath

    $startedProcess = Start-Process -FilePath $resolvedAppScript -PassThru
    $windowProcess = Wait-MiruPlayWindow

    Invoke-RelativeMouseWheel -Process $windowProcess -Notches -8
    Set-TextByRelativeClick -Process $windowProcess -X 280 -Y 298 -Text $webDavUrl -Description "WebDAV URL"
    Set-TextByRelativeClick -Process $windowProcess -X 170 -Y 375 -Text $webDavUsername -Description "WebDAV username"
    Set-TextByRelativeClick -Process $windowProcess -X 420 -Y 375 -Text $webDavPassword -Description "WebDAV password"
    Invoke-RelativeClick -Process $windowProcess -X 145 -Y 442

    $state = Wait-StoreState -Path $storePath -Description "saved WebDAV source" -Predicate {
        param($state)
        @($state.mediaSources | Where-Object { $_.type -eq "WEBDAV" }).Count -eq 1
    }
    Wait-FileText -Path $serverLogPath -Pattern "PROPFIND\|\|auth=True" -Description "authorized root PROPFIND"
    Start-Sleep -Milliseconds 900
    Save-WindowScreenshot -Process $windowProcess -Path $openScreenshotPath

    $source = @($state.mediaSources | Where-Object { $_.type -eq "WEBDAV" })[0]
    if ($source.connectionInfo.url.TrimEnd("/") -ne $webDavUrl.TrimEnd("/")) {
        throw "Stored WebDAV URL does not match fixture URL: $($source.connectionInfo.url)"
    }
    if ($source.connectionInfo.username -ne $webDavUsername) {
        throw "Stored WebDAV username does not match fixture username: $($source.connectionInfo.username)"
    }
    if ($source.connectionInfo.password -ne $webDavPassword) {
        throw "Stored WebDAV password does not match fixture password."
    }

    Invoke-RelativeClick -Process $windowProcess -X 760 -Y 310
    Wait-FileText -Path $serverLogPath -Pattern "PROPFIND\|Fixture WebDAV\|auth=True" -Description "authorized Fixture WebDAV directory PROPFIND"
    Start-Sleep -Milliseconds 900
    Save-WindowScreenshot -Process $windowProcess -Path $browseScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 295 -Y 681
    $state = Wait-StoreState -Path $storePath -Description "scanned WebDAV index entry" -Predicate {
        param($state)
        @($state.index | Where-Object { -not $_.isDirectory }).Count -ge 1
    } -TimeoutSeconds 90

    $indexedVideos = @($state.index | Where-Object { -not $_.isDirectory })
    if ($indexedVideos.Count -ne 1) {
        throw "Expected exactly one WebDAV fixture video, found $($indexedVideos.Count)."
    }
    $selectedVideo = $indexedVideos[0]
    if ($selectedVideo.path -ne $expectedVideoPath) {
        throw "Expected indexed video path '$expectedVideoPath', found '$($selectedVideo.path)'."
    }
    if ($selectedVideo.animeName -ne "Fixture WebDAV") {
        throw "Expected NFO anime name 'Fixture WebDAV', found '$($selectedVideo.animeName)'."
    }
    if ($selectedVideo.episodeNumber -ne 2) {
        throw "Expected episode number 2, found '$($selectedVideo.episodeNumber)'."
    }
    if ($selectedVideo.fileSize -le 0) {
        throw "Expected positive WebDAV fixture video size, found '$($selectedVideo.fileSize)'."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $scanScreenshotPath

    Invoke-RelativeMouseWheel -Process $windowProcess -Notches 10
    Save-WindowScreenshot -Process $windowProcess -Path $posterScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 130 -Y 360
    Start-Sleep -Milliseconds 700
    Save-WindowScreenshot -Process $windowProcess -Path $detailsScreenshotPath

    Invoke-RelativeClick -Process $windowProcess -X 674 -Y 466
    Start-Sleep -Milliseconds 500
    Invoke-RelativeClick -Process $windowProcess -X 520 -Y 615
    $selectedMediaPath = Get-FocusedText
    if ($selectedMediaPath -ne $expectedVideoPath) {
        throw "Player media path did not match selected WebDAV poster. Expected '$expectedVideoPath', found '$selectedMediaPath'."
    }
    Save-WindowScreenshot -Process $windowProcess -Path $playerScreenshotPath
} finally {
    if ($null -ne $previousClipboard) {
        Set-Clipboard -Value $previousClipboard
    }
    $env:MIRUPLAY_DESKTOP_STORE = $previousStoreEnv
    if ($webDavJob) {
        Stop-Job -Job $webDavJob -ErrorAction SilentlyContinue
        Remove-Job -Job $webDavJob -Force -ErrorAction SilentlyContinue
    }
    if (-not $KeepOpen) {
        $windowProcess = Get-MiruPlayWindowProcess
        if ($windowProcess) {
            $windowProcess.CloseMainWindow() | Out-Null
            Start-Sleep -Milliseconds 700
            if (-not $windowProcess.HasExited) {
                Stop-Process -Id $windowProcess.Id -Force
            }
        }
        if ($startedProcess -and -not $startedProcess.HasExited) {
            Stop-Process -Id $startedProcess.Id -Force
        }
    }
}

Write-Output "Run directory: $runDir"
Write-Output "Store: $storePath"
Write-Output "Fixture server log: $serverLogPath"
Write-Output "Open screenshot: $openScreenshotPath"
Write-Output "Browse screenshot: $browseScreenshotPath"
Write-Output "Scan screenshot: $scanScreenshotPath"
Write-Output "Poster wall screenshot: $posterScreenshotPath"
Write-Output "Details screenshot: $detailsScreenshotPath"
Write-Output "Player screenshot: $playerScreenshotPath"
