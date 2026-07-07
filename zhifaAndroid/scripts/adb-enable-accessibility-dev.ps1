$ErrorActionPreference = "Stop"
$AdbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$Service = "com.paifa.ubikitouch/com.paifa.ubikitouch.accessibility.UbikiAccessibilityService"

if (-not (Test-Path $AdbPath)) {
    throw "adb not found: $AdbPath"
}

Write-Host "Enabling accessibility service through ADB. Use this only for development testing."
$Current = (& $AdbPath shell settings get secure enabled_accessibility_services).Trim()
if ([string]::IsNullOrWhiteSpace($Current) -or $Current -eq "null") {
    $Next = $Service
} elseif ($Current.Split(":") -contains $Service) {
    $Next = $Current
} else {
    $Next = "$Current`:$Service"
}
& $AdbPath shell settings put secure enabled_accessibility_services $Next
& $AdbPath shell settings put secure accessibility_enabled 1
& $AdbPath shell settings get secure enabled_accessibility_services
Write-Host "Enable command sent. If the device blocks it, enable the service manually in Settings."
