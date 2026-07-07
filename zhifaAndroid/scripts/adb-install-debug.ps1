param(
    [string]$ApkPath = ".\app\build\outputs\apk\debug\app-debug.apk"
)

$ErrorActionPreference = "Stop"
$AdbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $AdbPath)) {
    throw "adb not found: $AdbPath"
}

if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath. Run .\gradlew.bat assembleDebug first."
}

Write-Host "Checking connected devices..."
& $AdbPath devices -l

Write-Host "Installing debug APK..."
& $AdbPath install -r $ApkPath

Write-Host "Launching ubikiTouch..."
& $AdbPath shell am start -n "com.paifa.ubikitouch/com.paifa.ubikitouch.app.MainActivity"

Write-Host "Install finished. Enable the ubikiTouch accessibility service manually on the phone."
