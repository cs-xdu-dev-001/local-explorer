@echo off
setlocal EnableDelayedExpansion

set MAVEN_VERSION=3.9.9
set BASE_DIR=%~dp0
set WRAPPER_DIR=%BASE_DIR%.mvn\wrapper
set MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
set MAVEN_ZIP=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip
set MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip

if not exist "!JAVA_HOME!\bin\javac.exe" (
  for %%I in (javac.exe) do set JAVAC_PATH=%%~$PATH:I
  if defined JAVAC_PATH (
    for %%J in ("!JAVAC_PATH!\..\..") do set JAVA_HOME=%%~fJ
    set "PATH=!JAVA_HOME!\bin;!PATH!"
  )
)

if exist "%MAVEN_HOME%\bin\mvn.cmd" goto run_wrapper

where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  mvn %*
  exit /b %ERRORLEVEL%
)

if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference=[System.Management.Automation.ActionPreference]::Stop;" ^
  "$ProgressPreference=[System.Management.Automation.ActionPreference]::SilentlyContinue;" ^
  "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
  "$zip=[IO.Path]::GetFullPath($env:MAVEN_ZIP);" ^
  "$temp=$zip+'.download';" ^
  "$dest=[IO.Path]::GetFullPath($env:WRAPPER_DIR);" ^
  "$url=$env:MAVEN_URL;" ^
  "Add-Type -AssemblyName System.IO.Compression.FileSystem;" ^
  "function Test-MavenArchive([string]$path) { if (-not (Test-Path -LiteralPath $path)) { return $false }; try { $archive=[IO.Compression.ZipFile]::OpenRead($path); $valid=$archive.Entries.Count -gt 0; $archive.Dispose(); return $valid } catch { return $false } };" ^
  "if (-not (Test-MavenArchive $zip)) { if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }; if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force }; try { Invoke-WebRequest -Uri $url -OutFile $temp -UseBasicParsing; if (-not (Test-MavenArchive $temp)) { throw ([System.Exception]::new('Downloaded Maven archive is invalid: '+$url)) }; Move-Item -LiteralPath $temp -Destination $zip -Force } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force } } };" ^
  "$archive=Get-Item -LiteralPath $zip -ErrorAction Stop;" ^
  "if ($archive.Length -lt 1000000) { throw ([System.Exception]::new($zip)) };" ^
  "Expand-Archive -LiteralPath $archive.FullName -DestinationPath $dest -Force"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

:run_wrapper
"%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
