param(
  [string]$BrowserPath = "",
  [string]$EdgePath = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $root "scripts\capture-demo-screenshots.cjs"
$arguments = @($script)
if ($BrowserPath) {
  $arguments += "--browser=$BrowserPath"
}
elseif ($EdgePath) {
  $arguments += "--edge=$EdgePath"
}

node @arguments
if ($LASTEXITCODE -ne 0) {
  throw "Screenshot capture failed with exit code $LASTEXITCODE."
}

Get-ChildItem (Join-Path $root "docs\screenshots") -Filter *.png |
  Sort-Object Name |
  Select-Object Name, Length, LastWriteTime
