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

# Folder names lie: an in-place update keeps the old name (the folder called
# "JetBrains Rider 261.22158.211" held 2026.2.1 and then 2026.3). Pick by the build number inside.
function Resolve-RiderHome {
    $candidates = Get-ChildItem "C:\Program Files\JetBrains" -Directory -Filter "JetBrains Rider*" -ErrorAction SilentlyContinue |
        ForEach-Object {
            $info = Join-Path $_.FullName "product-info.json"
            if (Test-Path $info) {
                $json = Get-Content $info -Raw | ConvertFrom-Json
                [pscustomobject]@{ Path = $_.FullName; Build = [int](($json.buildNumber -split '\.')[0]); Version = $json.version }
            }
        }
    $newest = $candidates | Sort-Object Build -Descending | Select-Object -First 1
    if (-not $newest) { throw "no Rider installation found under C:\Program Files\JetBrains" }
    $newest
}
$root = $PSScriptRoot

# TMP must not sit under AppData: AF_UNIX sockets fail there inside Claude Code's MSIX process tree,
# and Gradle's JVM needs one for Selector.open(). Harmless when run from a normal shell.
$tmp = Join-Path $root "Claude\tmp"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$env:TMP = $tmp
$env:TEMP = $tmp
if (-not $env:JAVA_HOME) {
    $rider = Resolve-RiderHome
    $env:JAVA_HOME = Join-Path $rider.Path "jbr"
    Write-Host "JAVA_HOME from Rider $($rider.Version): $env:JAVA_HOME"
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
