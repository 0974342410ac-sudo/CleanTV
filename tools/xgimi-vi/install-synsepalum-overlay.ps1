# Cai (hoac go voi -Remove) file dich tieng Viet cho Cai dat XGIMI tren may XGIMI Android 6 (synsepalum_YN).
# Can ban userdebug (adb root): chep dist\XgimiSettingsVi-overlay.apk vao /system/vendor/overlay roi khoi dong lai.
# Chay: powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\install-synsepalum-overlay.ps1 -TvHost 192.168.31.239 [-Remove]
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [switch]$Remove
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
$dev = "${TvHost}:5555"
# Ban dich Cai dat va thanh cai dat nhanh: file trong dist -> ten trong /system/vendor/overlay
$files = [ordered]@{ 'XgimiSettingsVi-overlay.apk' = 'XgimiSettingsVi.apk'; 'XgimiQuickVi-overlay.apk' = 'XgimiQuickVi.apk'
    'XgimiPowerVi-overlay.apk' = 'XgimiPowerVi.apk' }

& $adb connect $dev | Out-Null
if ((& $adb -s $dev shell getprop ro.product.device).Trim() -ne 'synsepalum_YN') { throw 'Day khong phai may XGIMI synsepalum_YN' }
& $adb -s $dev root | Out-Null
Start-Sleep 4
& $adb connect $dev | Out-Null
if ((& $adb -s $dev shell id -u).Trim() -ne '0') { throw 'adb root khong duoc (can firmware userdebug)' }
& $adb -s $dev shell 'mount -o rw,remount /system'
foreach ($src in $files.Keys) {
    $target = "/system/vendor/overlay/$($files[$src])"
    $base = [IO.Path]::GetFileNameWithoutExtension($files[$src])
    if ($Remove) {
        & $adb -s $dev shell "rm -f $target; rm -f /data/resource-cache/*$base*"
        continue
    }
    $apk = Join-Path $root "dist\$src"
    if (-not (Test-Path $apk)) { throw "Chua co ${apk}: chay build-synsepalum-overlay.ps1 truoc" }
    & $adb -s $dev shell 'mkdir -p /system/vendor/overlay; chmod 755 /system/vendor/overlay'
    & $adb -s $dev push $apk $target | Out-Null
    & $adb -s $dev shell "chmod 644 $target; chown root:root $target; ls -l $target"
}
& $adb -s $dev shell 'sync; mount -o ro,remount /system'
Write-Host 'Khoi dong lai may de Android nap (hoac bo) file dich...'
& $adb -s $dev reboot
