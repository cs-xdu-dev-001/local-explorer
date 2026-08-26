param(
  [string]$BrowserPath = "",
  [string]$EdgePath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $root "scripts\smoke-demo-pages.cjs"
$arguments = @($script)
if ($BrowserPath) {
  $arguments += "--browser=$BrowserPath"
}
elseif ($EdgePath) {
  $arguments += "--edge=$EdgePath"
}

node @arguments
if ($LASTEXITCODE -ne 0) {
  throw "UI smoke failed with exit code $LASTEXITCODE."
}

Get-ChildItem (Join-Path $root ".superpowers\ui-smoke-logs") |
  Sort-Object Name |
  Select-Object Name, Length, LastWriteTime
