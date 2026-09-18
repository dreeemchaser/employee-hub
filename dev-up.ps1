<#
.SYNOPSIS
  Quiet-ish `docker-compose up --build` for local dev.

.DESCRIPTION
  Wraps `docker-compose up --build` and filters out BuildKit's noisy layer
  reporting (layer hashes, "exporting manifest sha256:...", cache-hit spam),
  leaving a readable stream of build steps and container startup logs.

  It does NOT change docker-compose.yml or the Dockerfiles — it only filters
  what reaches your terminal. The full, unfiltered build still runs.

.PARAMETER Detach
  Start containers in the background (compose `-d`) and return once they're up,
  instead of streaming logs. Handy when you just want the stack running.

.PARAMETER NoBuild
  Skip the image rebuild (plain `up`). Use when nothing changed.

.EXAMPLE
  .\dev-up.ps1            # rebuild + stream filtered output
  .\dev-up.ps1 -Detach    # rebuild, run in background, print a status table
  .\dev-up.ps1 -NoBuild   # start without rebuilding
#>
[CmdletBinding()]
param(
    [switch]$Detach,
    [switch]$NoBuild
)

Set-Location $PSScriptRoot

# docker-compose writes its progress to stderr. Without this, PowerShell wraps
# every such line as a NativeCommandError (noisy red text). Treat stderr as
# ordinary output instead.
$ErrorActionPreference = 'Continue'
$PSNativeCommandUseErrorActionPreference = $false

# Plain progress is easier to filter than the animated TTY renderer.
$env:BUILDKIT_PROGRESS = 'plain'

# Lines we don't want to see — pure BuildKit/registry bookkeeping.
$noise = @(
    'sha256:',
    'exporting',
    'importing',
    'CACHED',
    'resolving provenance',
    'naming to docker.io',
    'unpacking to docker.io',
    'transferring context',
    'load build context',
    'load metadata',
    'load \.dockerignore',
    'DONE \d',
    '^\s*#\d+\s*$',
    '^\s*=> '
) -join '|'

function Write-Step($msg) { Write-Host "  $msg" -ForegroundColor Cyan }

$composeArgs = @('up')
if (-not $NoBuild) { $composeArgs += '--build' }
if ($Detach)       { $composeArgs += '-d' }

Write-Host ''
Write-Host 'EmployeeHub - bringing up the stack' -ForegroundColor Green
Write-Step ($(if ($NoBuild) { 'up (no rebuild)' } else { 'up --build' }) + $(if ($Detach) { ' -d' } else { '' }))
Write-Host ''

if ($Detach) {
    # Background: run quietly, then print a clean status table.
    docker-compose @composeArgs 2>&1 |
        ForEach-Object { "$_" } |
        Where-Object { $_ -notmatch $noise } |
        ForEach-Object { Write-Host $_ }

    Write-Host ''
    Write-Host 'Services:' -ForegroundColor Green
    docker-compose ps --format 'table {{.Service}}\t{{.State}}\t{{.Status}}\t{{.Ports}}'
    Write-Host ''
    Write-Host 'URLs:' -ForegroundColor Green
    Write-Host '  Employee portal : http://localhost:3000'
    Write-Host '  HR dashboard    : http://localhost:3001'
    Write-Host '  API / Swagger   : http://localhost:8080/swagger-ui/index.html'
}
else {
    # Foreground: stream filtered logs. Ctrl-C stops the stack as usual.
    docker-compose @composeArgs 2>&1 |
        ForEach-Object { "$_" } |
        Where-Object { $_ -notmatch $noise } |
        ForEach-Object { Write-Host $_ }
}
