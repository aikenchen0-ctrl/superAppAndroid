$ErrorActionPreference = "Stop"
$AdbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $AdbPath)) {
    throw "adb not found: $AdbPath"
}

Write-Host "Starting ubikiTouch logcat. Press Ctrl+C to stop."
& $AdbPath logcat "*:S" "UbikiTouch:V" "AndroidRuntime:E"
