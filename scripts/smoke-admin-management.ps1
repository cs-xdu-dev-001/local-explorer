param(
  [string]$BaseUrl = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $root "scripts\smoke-admin-management.cjs"
$arguments = @($script)
if ($BaseUrl) {
  $arguments += "--base=$BaseUrl"
}

node @arguments
if ($LASTEXITCODE -ne 0) {
  throw "Admin management smoke failed with exit code $LASTEXITCODE."
}
