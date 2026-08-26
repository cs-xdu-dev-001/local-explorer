param(
    [int]$Port = 5173,
    [switch]$NoOpen
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$FrontendDir = Join-Path $RepoRoot "explorer-web\frontend"
$ClientUrl = "http://127.0.0.1:$Port/client/index.html?demo=1"
$ConsoleUrl = "http://127.0.0.1:$Port/console/index.html?demo=1"

if (-not (Test-Path $FrontendDir)) {
    throw "Frontend directory not found: $FrontendDir"
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "npm is required. Install Node.js first, then run this script again."
}

Push-Location $FrontendDir
try {
    if (-not (Test-Path "node_modules")) {
        & npm install
    }

    Write-Host "Local Explorer demo mode"
    Write-Host "Client:  $ClientUrl"
    Write-Host "Console: $ConsoleUrl"
    Write-Host "Stop with Ctrl+C."

    if (-not $NoOpen) {
        Start-Job -ScriptBlock {
            param($ClientUrl, $ConsoleUrl)
            Start-Sleep -Seconds 3
            Start-Process $ClientUrl
            Start-Process $ConsoleUrl
        } -ArgumentList $ClientUrl, $ConsoleUrl | Out-Null
    }

    $Npx = if (Get-Command npx.cmd -ErrorAction SilentlyContinue) { "npx.cmd" } else { "npx" }
    & $Npx vite --host 127.0.0.1 --port $Port --strictPort
}
finally {
    Pop-Location
}
