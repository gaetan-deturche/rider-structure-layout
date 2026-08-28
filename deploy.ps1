# Builds the whole plugin (Kotlin frontend + rdgen protocol + .NET backend) and installs it unpacked
# into a Rider config dir. Rider must be RESTARTED afterwards: the backend half is loaded once at
# host startup.
#
# Default target is the isolated dev instance under Claude\dev-ide, not the real Rider.
param(
    [string]$RiderConfig = "$PSScriptRoot\Claude\dev-ide\config",
    [string]$Configuration = "Debug"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

# TMP must not sit under AppData: AF_UNIX sockets fail there inside Claude Code's MSIX process tree,
# and Gradle's JVM needs one for Selector.open(). Harmless when run from a normal shell.
$tmp = Join-Path $root "Claude\tmp"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$env:TMP = $tmp
$env:TEMP = $tmp
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\JetBrains\JetBrains Rider 261.22158.211\jbr"
}

Push-Location $root
try {
    & "$root\gradlew.bat" buildPlugin "-PBuildConfiguration=$Configuration" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "gradle buildPlugin failed" }
} finally {
    Pop-Location
}

$zip = Get-ChildItem (Join-Path $root "build\distributions") -Filter "*.zip" |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $zip) { throw "no plugin zip produced" }

$plugins = Join-Path $RiderConfig "plugins"
$target = Join-Path $plugins "struct-layout"
New-Item -ItemType Directory -Force -Path $plugins | Out-Null
if (Test-Path $target) { Remove-Item $target -Recurse -Force }

Expand-Archive -Path $zip.FullName -DestinationPath $plugins -Force

Write-Host "Installed $($zip.Name) to $target" -ForegroundColor Green
Write-Host "Restart Rider, then right-click a type -> Show Struct Layout (or Alt+L)." -ForegroundColor Green
