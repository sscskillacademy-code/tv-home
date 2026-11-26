# Puts the TV back the way it was before Glass Home changed it:
#   - switches back on every app listed in tv-backup\disabled-by-glasshome.txt
#   - restores the settings saved in tv-backup\settings-before.txt (screensaver, animations, ...)
#   - makes the original home screen the default again
#
#   powershell -ExecutionPolicy Bypass -File restore-tv.ps1                      (TV at the default address)
#   powershell -ExecutionPolicy Bypass -File restore-tv.ps1 -Tv 10.204.207.212:5555 -RemoveGlassHome
param(
    [string]$Tv = "10.204.207.212:5555",
    [switch]$RemoveGlassHome
)
$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$b = "$PSScriptRoot\tv-backup"

function Quote([string]$v) { "'" + ($v -replace "'", "'\''") + "'" }

& $adb connect $Tv | Out-Null

# 1. Switch back on everything Glass Home switched off
if (Test-Path "$b\disabled-by-glasshome.txt") {
    Get-Content "$b\disabled-by-glasshome.txt" | ForEach-Object { $_.Trim() } | Where-Object { $_ } | ForEach-Object {
        Write-Host "Switching back on: $_"
        & $adb -s $Tv shell "pm enable $_" | Out-Null
    }
}

# 2. Settings as they were
Get-Content "$b\settings-before.txt" | Where-Object { $_.Trim() } | ForEach-Object {
    $ns, $key, $val = $_.Trim() -split " ", 3
    if ($val -eq "null") { & $adb -s $Tv shell "settings delete $ns $key" | Out-Null }
    else { & $adb -s $Tv shell "settings put $ns $key $(Quote $val)" | Out-Null }
}

# 3. Original home screen as the default
$home = (Get-Content "$b\home-before.txt" | Select-Object -First 1).Trim()
& $adb -s $Tv shell "cmd package set-home-activity $home" | Out-Null

if ($RemoveGlassHome) { & $adb -s $Tv uninstall com.charan.glasshome | Out-Null }

& $adb -s $Tv shell "input keyevent KEYCODE_HOME"
Write-Host "Done - the TV is back to its original home screen." -ForegroundColor Green
