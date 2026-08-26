param(
  [string]$BaseUrl = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $root "scripts\smoke-auth-session.cjs"
$arguments = @($script)
if ($BaseUrl) {
  $arguments += "--base=$BaseUrl"
}

node @arguments
if ($LASTEXITCODE -ne 0) {
  throw "Authentication session smoke failed with exit code $LASTEXITCODE."
}
