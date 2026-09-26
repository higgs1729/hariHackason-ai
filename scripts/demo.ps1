<#
.SYNOPSIS
  Start everything the demo needs on this PC and print the URL for the phone.

.DESCRIPTION
  Docker Desktop -> MySQL -> frontend build -> Spring Boot on :8080 -> demo seed
  -> cloudflared quick tunnel. Every step reuses what is already running, so the
  script can be re-run at any time; the tunnel URL only changes when the tunnel
  itself had to be restarted.

  Logs and pid files go to scripts\logs\ (git-ignored).

.EXAMPLE
  & C:\dev\hariHackason-ai\scripts\demo.ps1
.EXAMPLE
  & C:\dev\hariHackason-ai\scripts\demo.ps1 -NoTunnel
.EXAMPLE
  & C:\dev\hariHackason-ai\scripts\demo.ps1 -Stop
#>
param(
    [switch]$NoTunnel,  # local only: skip cloudflared
    [switch]$Rebuild,   # build the frontend even when dist looks current
    [switch]$Stop       # stop Spring and the tunnel (MySQL and Docker stay up)
)

$ErrorActionPreference = 'Stop'
# Native commands go through cmd.exe /c: in PowerShell 5.1 any stderr line from a
# native exe becomes a terminating error under 'Stop', even on success.
$Root = Split-Path -Parent $PSScriptRoot
$Logs = Join-Path $PSScriptRoot 'logs'
$Local = 'http://localhost:8080'
New-Item -ItemType Directory -Force $Logs | Out-Null

function Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Done($text) { Write-Host "    $text" -ForegroundColor DarkGray }

function Wait-Until([scriptblock]$Test, [int]$Seconds, [string]$What) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        try { if (& $Test) { return } } catch { }
        Start-Sleep -Seconds 2
    }
    throw "Timed out after $Seconds s waiting for $What"
}

function Invoke-Npm([string[]]$NpmArgs) {
    $log = Join-Path $Logs 'npm.log'
    $p = Start-Process npm.cmd -ArgumentList $NpmArgs -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    if ($p.ExitCode -ne 0) { throw "npm $NpmArgs failed, see $log" }
}

function Test-Health {
    try { (Invoke-RestMethod "$Local/api/health" -TimeoutSec 3).status -eq 'UP' } catch { $false }
}

function Get-PortOwner([int]$Port) {
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty OwningProcess
}

function Stop-Tree([int]$ProcessId) {
    & cmd.exe /c "taskkill /PID $ProcessId /T /F >nul 2>&1"
}

$tunnelPidFile = Join-Path $Logs 'cloudflared.pid'
$tunnelUrlFile = Join-Path $Logs 'tunnel-url.txt'

if ($Stop) {
    Step 'Stopping Spring and the tunnel'
    $owner = Get-PortOwner 8080
    if ($owner) { Stop-Tree $owner; Done "Spring (pid $owner) stopped" } else { Done 'Spring was not running' }
    if (Test-Path $tunnelPidFile) {
        Stop-Tree ([int](Get-Content $tunnelPidFile))
        Remove-Item $tunnelPidFile, $tunnelUrlFile -ErrorAction SilentlyContinue
        Done 'tunnel stopped'
    } else { Done 'tunnel was not running' }
    return
}

# --- 1. Docker Desktop ------------------------------------------------------
Step 'Docker'
function Test-Docker { & cmd.exe /c 'docker info --format {{.ServerVersion}} >nul 2>&1'; $LASTEXITCODE -eq 0 }
if (Test-Docker) {
    Done 'already running'
} else {
    # On this PC Docker Desktop dies at startup on socket files left by the last
    # run (reparse points nothing can delete). Moving the folders aside works.
    Get-Process | Where-Object { $_.Name -like '*docker*' } | ForEach-Object { Stop-Tree $_.Id }
    Start-Sleep -Seconds 3
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    foreach ($dir in @("$env:LOCALAPPDATA\Docker\run", "$env:LOCALAPPDATA\docker-secrets-engine")) {
        if (Test-Path $dir) {
            Rename-Item $dir "$(Split-Path -Leaf $dir).stale-$stamp"
            Done "moved aside $dir"
        }
    }
    Start-Process "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"
    Wait-Until { Test-Docker } 240 'Docker Desktop'
    Done 'started'
}

# --- 2. MySQL -----------------------------------------------------------------
Step 'MySQL'
Push-Location $Root
try { & cmd.exe /c 'docker compose up -d mysql >nul 2>&1' } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }
Wait-Until { (& cmd.exe /c 'docker inspect -f {{.State.Health.Status}} hanamizuki-mysql 2>nul') -eq 'healthy' } 120 'MySQL healthy'
Done 'healthy'

# --- 3. Frontend build --------------------------------------------------------
Step 'Frontend build'
$front = Join-Path $Root 'frontend'
$built = Join-Path $front 'dist\index.html'
$stale = $Rebuild -or -not (Test-Path $built)
if (-not $stale) {
    $builtAt = (Get-Item $built).LastWriteTime
    $stale = [bool](Get-ChildItem (Join-Path $front 'src'), (Join-Path $front 'public'), (Join-Path $front 'index.html') -Recurse -File |
        Where-Object { $_.LastWriteTime -gt $builtAt } | Select-Object -First 1)
}
if ($stale) {
    Push-Location $front
    try {
        if (-not (Test-Path 'node_modules')) { Invoke-Npm 'ci' }
        Invoke-Npm 'run', 'build'
    } finally { Pop-Location }
    Done 'built'
} else { Done 'dist is current' }

# --- 4. Spring Boot -----------------------------------------------------------
Step 'Spring Boot on :8080'
if (Test-Health) {
    Done 'already up'
} else {
    $owner = Get-PortOwner 8080
    if ($owner) { throw "Port 8080 is held by pid $owner but /api/health does not answer. Stop it or run with -Stop first." }
    $springLog = Join-Path $Logs 'spring.log'
    # AI_PROVIDER / ANTHROPIC_API_KEY / CLAUDE_CLI pass through from this shell.
    $backend = Join-Path $Root 'backend'
    # Full path: cmd.exe may be told not to look in the current directory
    # (NoDefaultCurrentDirectoryInExePath). /s keeps the inner quotes intact.
    $spring = Start-Process cmd.exe -WorkingDirectory $backend -WindowStyle Hidden -PassThru `
        -ArgumentList "/s /c `"`"$backend\mvnw.cmd`" -q spring-boot:run > `"$springLog`" 2>&1`""
    Wait-Until { $spring.HasExited -or (Test-Health) } 300 "Spring (log: $springLog)"
    if ($spring.HasExited) { throw "Spring exited during startup, see $springLog" }
    Done "up (log: $springLog)"
}
$health = Invoke-RestMethod "$Local/api/health"
Done "db=$($health.db) ai=$($health.aiProvider) aiReachable=$($health.aiReachable)"

# --- 5. Seed ------------------------------------------------------------------
Step 'Demo seed'
$seed = Invoke-RestMethod "$Local/api/dev/seed" -Method Post
Done $(if ($seed.alreadySeeded) { 'already seeded' } else { 'seeded' })

# --- 6. Tunnel ----------------------------------------------------------------
$phone = $null
if (-not $NoTunnel) {
    Step 'cloudflared quick tunnel'
    $alive = (Test-Path $tunnelPidFile) -and (Test-Path $tunnelUrlFile) -and
        (Get-Process -Id ([int](Get-Content $tunnelPidFile)) -ErrorAction SilentlyContinue)
    if ($alive) {
        $phone = (Get-Content $tunnelUrlFile).Trim()
        Done 'already running'
    } else {
        if (Test-Path $tunnelPidFile) { Stop-Tree ([int](Get-Content $tunnelPidFile)) }  # half-started earlier
        $errLog = Join-Path $Logs 'cloudflared.log'
        Remove-Item $errLog -ErrorAction SilentlyContinue
        $exe = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
        if (-not $exe) { $exe = 'C:\Program Files (x86)\cloudflared\cloudflared.exe' }
        $proc = Start-Process $exe -ArgumentList 'tunnel', '--no-autoupdate', '--url', $Local `
            -RedirectStandardError $errLog -RedirectStandardOutput (Join-Path $Logs 'cloudflared.out') `
            -WindowStyle Hidden -PassThru
        $proc.Id | Set-Content $tunnelPidFile
        # cloudflared prints the URL on stderr.
        $urlPattern = 'https://[a-z0-9-]+\.trycloudflare\.com'
        Wait-Until { (Get-Content $errLog -Raw) -match $urlPattern } 60 'the tunnel URL'
        $phone = [regex]::Match((Get-Content $errLog -Raw), $urlPattern).Value
        $phone | Set-Content $tunnelUrlFile
        Done 'started'
    }
    try {
        Wait-Until { (Invoke-RestMethod "$phone/api/health" -TimeoutSec 5).status -eq 'UP' } 90 'the tunnel to answer'
        Done 'answers from outside'
    } catch {
        Write-Warning "The tunnel URL does not answer yet from this PC (DNS can take a minute). Try it on the phone anyway."
    }
}

Write-Host ''
Write-Host "  PC     $Local" -ForegroundColor Green
if ($phone) { Write-Host "  Phone  $phone" -ForegroundColor Green }
Write-Host '  Login  nao / password   (friends: ayaka, miki, rin)' -ForegroundColor Green
Write-Host "  Stop   & `"$PSCommandPath`" -Stop" -ForegroundColor DarkGray
