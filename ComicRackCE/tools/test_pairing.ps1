<#
.SYNOPSIS
    Orchestrates an End-to-End WiFi Sync Pairing Test.
    
.DESCRIPTION
    1. Starts a Wireshark (tshark) capture in the background.
    2. Triggers the Android WiFi Sync Service via ADB Broadcast.
    3. Launches the AutoHotKey script to automate the Desktop "Pairing" UI.
    4. Stops capture after automation completes.

.PARAMETER DeviceIp
    The IP address of the Android device (for capture filter). Default: 192.168.232.2 (AVD emulator wlan0)
.PARAMETER InterfaceId
    The network interface ID for tshark. Default: 1
.PARAMETER Duration
    Max duration to wait for the test in seconds. Default: 30
#>
param(
    [string]$DeviceIp = "192.168.232.2",
    [string]$InterfaceId = "1",
    [int]$Duration = 30,
    [string]$SyncFolder = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FComics"
)

$ErrorActionPreference = "Stop"
$ScriptRoot = $PSScriptRoot
$AhkScript = Join-Path $ScriptRoot "auto_pair.ahk"
$PcapFile = Join-Path $ScriptRoot "pairing_test.pcapng"

# 0. Pre-checks
if (-not (Get-Command "tshark" -ErrorAction SilentlyContinue)) {
    Write-Error "tshark not found in PATH. Please install Wireshark."
}
if (-not (Test-Path $AhkScript)) {
    Write-Error "AHK script not found at $AhkScript"
}

Write-Host ">>> STARTING PAIRING TEST <<<" -ForegroundColor Cyan

# 1. Start Network Capture & Logcat
Write-Host "[1/4] Starting TShark & Logcat Capture..."
$InterfaceIndex = "1" # Default
$TsharkD = tshark -D
$InterfaceIndex = "1"
$Match = $TsharkD | Select-String "(\d+)\. .*Wi-Fi"
if ($Match) {
    $InterfaceIndex = $Match.Matches.Groups[1].Value
} else {
    $Match = $TsharkD | Select-String "(\d+)\. .*Local Area Connection\* \d+"
    if ($Match) {
        $InterfaceIndex = $Match.Matches.Groups[1].Value
    }
}
Write-Host "Capturing on Interface Index: $InterfaceIndex"
$Filter = "((udp port 7615 or tcp port 7614) and host $DeviceIp) or (udp dst portrange 7614-7616)"
$TsharkProc = Start-Process -FilePath "tshark" -ArgumentList "-i", $InterfaceIndex, "-f", "`"$Filter`"", "-w", "`"$PcapFile`"" -PassThru -NoNewWindow

$LogcatFile = Join-Path $ScriptRoot "pairing_test_logcat.txt"
# Clear buffer first
adb logcat -c
# Start logcat background job
$LogcatJob = Start-Job -ScriptBlock { 
    param($Path)
    # Using adb -d (device) to be specific, or just adb
    adb logcat -v time > $Path 
} -ArgumentList $LogcatFile

Start-Sleep -Seconds 2

# 2. Trigger Android Service
Write-Host "[2/4] Resetting and Triggering Android Service via ADB..."
adb shell am force-stop com.comicrack.viewer
Start-Sleep -Seconds 2
Write-Host "Launching Main Activity to bring app to foreground..."
adb shell am start -W -n com.comicrack.viewer/com.comicrack.viewer.ComposeLibraryActivity
Start-Sleep -Seconds 5
$AdbResult = adb shell am broadcast -a com.comicrack.viewer.DEBUG_START_SYNC -n com.comicrack.viewer/.DebugStartReceiver --es sync_folder "`"$SyncFolder`""
Write-Host "ADB Result: $AdbResult"

Start-Sleep -Seconds 2

# 3. Run Desktop Automation
Write-Host "[3/4] Running AutoHotKey Automation..."
# Assuming .ahk is associated, or try to find AutoHotKey.exe
try {
    $AhkProc = Start-Process -FilePath $AhkScript -PassThru
    # Wait for AHK to finish
    $AhkProc | Wait-Process -Timeout $Duration
    
    if ($AhkProc.ExitCode -eq 0) {
        Write-Host "AHK Script Completed Successfully." -ForegroundColor Green
    } else {
        Write-Warning "AHK Script Failed (Exit Code $($AhkProc.ExitCode))."
    }
} catch {
    Write-Warning "AHK Script timed out or failed to start."
}

# 4. Stop Capture
Write-Host "[4/4] Stopping Capture..."
Stop-Process -Id $TsharkProc.Id -Force -ErrorAction SilentlyContinue
Stop-Job $LogcatJob
Remove-Job $LogcatJob -Force

if (Test-Path $PcapFile) {
    Write-Host ">>> TEST COMPLETE <<<" -ForegroundColor Cyan
    Write-Host "Capture saved to: $PcapFile"
    Write-Host "Logcat saved to: $LogcatFile"
    Write-Host "You can analyze it with: tshark -r $PcapFile"
} else {
    Write-Error "Capture file not found!"
}
