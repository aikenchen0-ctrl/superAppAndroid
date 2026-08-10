param(
    [string]$Serial = "",
    [switch]$RestartServer,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$AdbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

function Write-Step {
    param([string]$Message)
    if (-not $Quiet) {
        Write-Host $Message
    }
}

function Get-AdbDeviceRows {
    param([string]$AdbExecutable)
    $output = & $AdbExecutable devices -l
    return $output | Select-Object -Skip 1 | Where-Object { $_.Trim().Length -gt 0 }
}

function Get-AdbDeviceState {
    param([string]$Row)
    $parts = $Row.Trim() -split "\s+"
    if ($parts.Length -lt 2) {
        return ""
    }
    return $parts[1]
}

function Get-AdbDeviceSerial {
    param([string]$Row)
    $parts = $Row.Trim() -split "\s+"
    if ($parts.Length -lt 1) {
        return ""
    }
    return $parts[0]
}

if (-not (Test-Path $AdbPath)) {
    Write-Error "adb not found: $AdbPath"
    exit 10
}

Write-Step "Using adb: $AdbPath"
& $AdbPath version | ForEach-Object { Write-Step $_ }

if ($RestartServer) {
    Write-Step "Restarting adb server..."
    & $AdbPath kill-server | Out-Null
    & $AdbPath start-server | Out-Null
}

$rows = @(Get-AdbDeviceRows -AdbExecutable $AdbPath)

if ($rows.Count -eq 0) {
    Write-Step "No adb device found."
    Write-Step "Check that the phone is powered on, unlocked, USB debugging is enabled, and the USB mode is File transfer/MTP."
    Write-Step "Connected USB-like devices:"
    if (-not $Quiet) {
        Get-PnpDevice -PresentOnly |
            Where-Object {
                $_.FriendlyName -match "Android|ADB|MTP|Portable|Composite|USB" -or
                $_.InstanceId -match "VID_18D1|VID_22B8|VID_04E8|VID_2717|VID_2A70|VID_2D95|VID_12D1"
            } |
            Select-Object Class, FriendlyName, Status, InstanceId |
            Format-Table -AutoSize
    }
    exit 20
}

$matchedRows = if ([string]::IsNullOrWhiteSpace($Serial)) {
    $rows
} else {
    @($rows | Where-Object { (Get-AdbDeviceSerial $_) -eq $Serial })
}

if ($matchedRows.Count -eq 0) {
    Write-Error "Requested device serial not found: $Serial"
    $rows | ForEach-Object { Write-Step $_ }
    exit 21
}

$readyRows = @($matchedRows | Where-Object { (Get-AdbDeviceState $_) -eq "device" })
$unauthorizedRows = @($matchedRows | Where-Object { (Get-AdbDeviceState $_) -eq "unauthorized" })
$offlineRows = @($matchedRows | Where-Object { (Get-AdbDeviceState $_) -eq "offline" })

if ($unauthorizedRows.Count -gt 0) {
    Write-Step "Device is unauthorized. Unlock the phone and accept the RSA debugging prompt."
    $unauthorizedRows | ForEach-Object { Write-Step $_ }
    exit 30
}

if ($offlineRows.Count -gt 0) {
    Write-Step "Device is offline. Replug USB or restart adb server."
    $offlineRows | ForEach-Object { Write-Step $_ }
    exit 31
}

if ($readyRows.Count -eq 0) {
    Write-Step "No ready adb device found. Current rows:"
    $rows | ForEach-Object { Write-Step $_ }
    exit 32
}

if ($readyRows.Count -gt 1 -and [string]::IsNullOrWhiteSpace($Serial)) {
    Write-Step "Multiple ready devices found. Pass -Serial to select one."
    $readyRows | ForEach-Object { Write-Step $_ }
    exit 40
}

$selectedSerial = Get-AdbDeviceSerial $readyRows[0]
if ($Quiet) {
    Write-Output $selectedSerial
} else {
    Write-Step "Ready adb device: $selectedSerial"
    $readyRows[0] | ForEach-Object { Write-Step $_ }
}
