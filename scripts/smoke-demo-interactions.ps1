param(
  [string]$BrowserPath = "",
  [string]$EdgePath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $root "scripts\smoke-demo-interactions.cjs"
$arguments = @($script)
if ($BrowserPath) {
  $arguments += "--browser=$BrowserPath"
}
elseif ($EdgePath) {
  $arguments += "--edge=$EdgePath"
}

node @arguments
if ($LASTEXITCODE -ne 0) {
  throw "Interaction smoke failed with exit code $LASTEXITCODE."
}

Get-ChildItem (Join-Path $root ".superpowers\interaction-smoke-logs") |
  Sort-Object Name |
  Select-Object Name, Length, LastWriteTime
