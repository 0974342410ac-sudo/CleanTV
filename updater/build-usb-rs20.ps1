# Goi USB cho XGIMI RS 20 Pro Max va RS 20 Ultra Max (nen tang horse_popular): dist\USB-CapNhat-RS20.
# Firmware XGIMI_HORSEPOPULAR_*.bin bi ma hoa nen khong sua duoc; goi nay Viet hoa qua ADB root cua chinh may.
# Dung lai launcher trong assets (ky bang khoa cua cac TV), tao lai ban dich ung dung va menu cai dat nhanh, dung lai
# ung dung cap nhat, de chay duoc ca tren may tinh chua co khoa goc. Chay: updater\build-usb-rs20.ps1
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent $here
$assets = Join-Path $root 'launcher\updater\src\main\assets'
if (-not (Test-Path (Join-Path $assets 'tvhome.apk'))) { throw 'Chua co assets: chay updater\build-usb.ps1 truoc' }
Copy-Item (Join-Path $root 'tools\xgimi-vi\dohko-quick-panel-vi.sql') (Join-Path $assets 'quick-panel-vi.sql') -Force
$boot = [IO.File]::ReadAllText((Join-Path $root 'tools\xgimi-vi\dohko-boot.sh'), [Text.Encoding]::UTF8).Replace("`r", '')
[IO.File]::WriteAllText((Join-Path $assets 'boot.sh'), $boot, (New-Object Text.UTF8Encoding $false))
$appsDir = Join-Path $assets 'apps'
if (Test-Path $appsDir) { Remove-Item $appsDir -Recurse -Force }
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'tools\xgimi-vi\dohko-apps-xml.ps1') -OutDir $appsDir
if ($LASTEXITCODE -ne 0) { throw 'Tao file dich ung dung XGIMI that bai' }
# Bo nhan dang giong noi cua Google cho tim kiem bang giong noi; khong co trong Git (*.apk), lay tu may da cai
if (-not (Test-Path (Join-Path $root 'launcher\updater\src\xgimi\assets\speech.apk'))) { throw 'Thieu launcher\updater\src\xgimi\assets\speech.apk: adb pull APK cua com.google.android.tts vao do' }
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'E:\buildtools\jdk\jdk-17.0.20.1+1' }
Push-Location (Join-Path $root 'launcher')
.\gradlew.bat :updater:assembleXgimiRelease -q --console=plain
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw 'Build ung dung cap nhat that bai' }
$usb = Join-Path $root 'dist\USB-CapNhat-RS20'
if (Test-Path $usb) { Remove-Item $usb -Recurse -Force }
New-Item -ItemType Directory -Force $usb | Out-Null
Copy-Item (Join-Path $root 'launcher\updater\build\outputs\apk\xgimi\release\updater-xgimi-release.apk') (Join-Path $usb 'CapNhatXGIMI-DOHKO.apk')
# Launcher dung trong ung dung cap nhat, de cai rieng khi can
Copy-Item (Join-Path $assets 'tvhome.apk') (Join-Path $usb 'ManHinhChinh-Launcher.apk')
$adbGuide = [IO.File]::ReadAllText((Join-Path $here 'HUONG-DAN-BAT-ADB.txt'), [Text.Encoding]::UTF8)
$guide = [IO.File]::ReadAllText((Join-Path $here 'HUONG-DAN-USB-XGIMI.txt'), [Text.Encoding]::UTF8) + $adbGuide
[IO.File]::WriteAllText((Join-Path $usb 'HUONG-DAN.txt'), $guide, (New-Object Text.UTF8Encoding $true))
$zip = Join-Path $root 'dist\USB-CapNhat-RS20.zip'
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path $usb -DestinationPath $zip
Write-Host "Da tao $usb va $zip"
