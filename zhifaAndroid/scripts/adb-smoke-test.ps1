param(
    [int]$LeftStartX = 1,
    [int]$RightStartX = 1079,
    [int]$CenterY = 1200
)

$ErrorActionPreference = "Stop"
$AdbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $AdbPath)) {
    throw "adb not found: $AdbPath"
}

Write-Host "Connected devices:"
& $AdbPath devices -l

Write-Host "Launching ubikiTouch settings..."
& $AdbPath shell am start -n "com.paifa.ubikitouch/com.paifa.ubikitouch.app.MainActivity"
Start-Sleep -Seconds 1

Write-Host "Simulating left inward pull. Default action is Back."
& $AdbPath shell input swipe $LeftStartX $CenterY 220 $CenterY 180
Start-Sleep -Milliseconds 500

Write-Host "Simulating right inward pull. Default action is Back."
& $AdbPath shell input swipe $RightStartX $CenterY 860 $CenterY 180

Write-Host "Smoke test commands sent. Confirm behavior with logcat and the device screen."
