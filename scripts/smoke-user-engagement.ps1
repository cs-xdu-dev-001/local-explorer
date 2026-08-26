param(
  [string]$BaseUrl = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $root "scripts\smoke-user-engagement.cjs"
$arguments = @($script)
if ($BaseUrl) {
  $arguments += "--base=$BaseUrl"
}

node @arguments
if ($LASTEXITCODE -ne 0) {
  throw "User engagement smoke failed with exit code $LASTEXITCODE."
}
