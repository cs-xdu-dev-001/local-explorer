param(
    [int]$Port = 5173,
    [switch]$NoOpen
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$FrontendDir = Join-Path $RepoRoot "explorer-web\frontend"
$ConsoleUrl = "http://127.0.0.1:$Port/console/login.html"
$ClientUrl = "http://127.0.0.1:$Port/client/login.html"

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
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed."
        }
    }

    Write-Host "Local Explorer frontend dev mode"
    Write-Host "Start the backend in IDEA first: LocalExplorerApplication on http://localhost:8080"
    Write-Host "Console: $ConsoleUrl"
    Write-Host "Client:  $ClientUrl"
    Write-Host "Stop with Ctrl+C."

    if (-not $NoOpen) {
        Start-Job -ScriptBlock {
            param($ConsoleUrl, $ClientUrl)
            Start-Sleep -Seconds 3
            Start-Process $ConsoleUrl
            Start-Process $ClientUrl
        } -ArgumentList $ConsoleUrl, $ClientUrl | Out-Null
    }

    $Npx = if (Get-Command npx.cmd -ErrorAction SilentlyContinue) { "npx.cmd" } else { "npx" }
    & $Npx vite --host 127.0.0.1 --port $Port --strictPort
}
finally {
    Pop-Location
}
