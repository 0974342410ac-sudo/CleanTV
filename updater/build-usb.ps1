# Tao thu muc cap nhat USB: dist\USB-CapNhat\CapNhatMayChieu.apk (+ huong dan).
# Chep ca thu muc vao goc USB, cam vao may chieu, mo APK bang X-plore de cai, roi bam "Cap nhat".
# Chay: powershell -ExecutionPolicy Bypass -File updater\build-usb.ps1
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent $here
$assets = Join-Path $root 'launcher\updater\src\main\assets'
New-Item -ItemType Directory -Force $assets | Out-Null
# Chi ban chung: onn va XGIMI Android 6 (ban XGIMI DOHKO khong chua)
$assetsChung = Join-Path $root 'launcher\updater\src\chung\assets'
New-Item -ItemType Directory -Force $assetsChung | Out-Null
Remove-Item (Join-Path $assets 'debloat-onn.txt'), (Join-Path $assets 'xgimi-settings-vi-a6.apk') -ErrorAction SilentlyContinue

# 1. Assets: launcher moi nhat, danh sach debloat, ban dich Cai dat XGIMI (dang XML cua cmd overlay fabricate)
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'E:\buildtools\jdk\jdk-17.0.20.1+1' }
Push-Location (Join-Path $root 'launcher')
.\gradlew.bat :app:assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Build launcher that bai' }
Copy-Item 'app\build\outputs\apk\release\app-release.apk' (Join-Path $assets 'tvhome.apk') -Force
Copy-Item (Join-Path $root 'examples\xgimi-dohko\debloat.txt') (Join-Path $assets 'debloat.txt') -Force
Copy-Item (Join-Path $root 'examples\onn-4k-streaming-box-2023\debloat.txt') (Join-Path $assetsChung 'debloat-onn.txt') -Force
# Ban dich Cai dat cho XGIMI Android 6 (overlay tinh, tools\xgimi-vi\build-synsepalum-overlay.ps1)
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'tools\xgimi-vi\build-synsepalum-overlay.ps1')
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Dung ban dich Cai dat XGIMI Android 6 that bai' }
Copy-Item (Join-Path $root 'dist\XgimiSettingsVi-overlay.apk') (Join-Path $assetsChung 'xgimi-settings-vi-a6.apk') -Force
$dict = @{}
$lines = foreach ($f in 'dich.tsv', 'dich-cau-dai.tsv') { [IO.File]::ReadAllLines((Join-Path $root "tools\xgimi-vi\$f"), [Text.Encoding]::UTF8) }
foreach ($line in $lines) {
    if (-not $line -or $line.StartsWith('#')) { continue }
    $p = $line.Split("`t")
    if ($p.Count -ge 2 -and $p[1].Trim()) { $dict[($p[0] -replace '\s+', ' ').Trim()] = $p[1].Trim() }
}
$items = foreach ($row in (Import-Csv (Join-Path $root 'tools\xgimi-vi\chuoi-goc.csv') -Encoding UTF8)) {
    if ($dict.ContainsKey(($row.v -replace '\s+', ' ').Trim())) { "    <item target=`"string/$($row.n)`" value=`"$([Security.SecurityElement]::Escape($dict[($row.v -replace '\s+', ' ').Trim()]).Replace('\n', '&#10;'))`" />" }
}
$xml = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<overlay>`n" + ($items -join "`n") + "`n</overlay>`n"
[IO.File]::WriteAllText((Join-Path $assets 'vi.xml'), $xml, (New-Object Text.UTF8Encoding $false))
# DOHKO: menu tat may (dohko-shutdown-vi.tsv) va boot.sh tao lai ban dich sau moi lan khoi dong (dohko-boot.sh)
$power = foreach ($line in [IO.File]::ReadAllLines((Join-Path $root 'tools\xgimi-vi\dohko-shutdown-vi.tsv'), [Text.Encoding]::UTF8)) {
    if (-not $line -or $line.StartsWith('#')) { continue }
    $p = $line.Split("`t")
    if ($p.Count -ge 2 -and $p[1].Trim()) { "    <item target=`"string/$($p[0].Trim())`" value=`"$([Security.SecurityElement]::Escape($p[1].Trim()))`" />" }
}
$xml = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<overlay>`n" + ($power -join "`n") + "`n</overlay>`n"
[IO.File]::WriteAllText((Join-Path $assets 'shutdown-vi.xml'), $xml, (New-Object Text.UTF8Encoding $false))
# Cac ung dung XGIMI khac (dohko-apps-xml.ps1): assets/apps/ov-<goi>.xml
$appsDir = Join-Path $assets 'apps'
if (Test-Path $appsDir) { Remove-Item $appsDir -Recurse -Force }
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'tools\xgimi-vi\dohko-apps-xml.ps1') -OutDir $appsDir
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Tao file dich ung dung XGIMI that bai' }
$boot = [IO.File]::ReadAllText((Join-Path $root 'tools\xgimi-vi\dohko-boot.sh'), [Text.Encoding]::UTF8).Replace("`r", '')
[IO.File]::WriteAllText((Join-Path $assets 'boot.sh'), $boot, (New-Object Text.UTF8Encoding $false))

# 2. Ung dung cap nhat
# Hai ban: chung (moi may, USB-CapNhat) va xgimi (chi may chieu XGIMI, USB-CapNhat-XGIMI)
.\gradlew.bat :updater:assembleChungRelease :updater:assembleXgimiRelease -q --console=plain
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw 'Build ung dung cap nhat that bai' }

# 3. Thu muc USB
$usb = Join-Path $root 'dist\USB-CapNhat'
if (Test-Path $usb) { Remove-Item $usb -Recurse -Force }
New-Item -ItemType Directory -Force $usb | Out-Null
Copy-Item (Join-Path $root 'launcher\updater\build\outputs\apk\chung\release\updater-chung-release.apk') (Join-Path $usb 'CapNhatMayChieu.apk')
# May XGIMI Android 6: ung dung cap nhat khong dung duoc ADB noi bo o do; cai thang launcher bang trinh quan ly file
Copy-Item (Join-Path $assets 'tvhome.apk') (Join-Path $usb 'ManHinhChinh-Launcher.apk')
$guide = [IO.File]::ReadAllText((Join-Path $here 'HUONG-DAN-USB.txt'), [Text.Encoding]::UTF8)
[IO.File]::WriteAllText((Join-Path $usb 'HUONG-DAN.txt'), $guide, (New-Object Text.UTF8Encoding $true))
$zip = Join-Path $root 'dist\USB-CapNhat.zip'
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path $usb -DestinationPath $zip
Write-Host ("Da tao {0} ({1:N0} KB) va {2}" -f $usb, ((Get-Item (Join-Path $usb 'CapNhatMayChieu.apk')).Length / 1KB), $zip)

# 4. Thu muc USB rieng cho may chieu XGIMI DOHKO (Android 14; khong co phan Android 6)
foreach ($old in 'dist\USB-CapNhat-XGIMI', 'dist\USB-CapNhat-XGIMI.zip') { $o = Join-Path $root $old; if (Test-Path $o) { Remove-Item $o -Recurse -Force } }
$usbX = Join-Path $root 'dist\USB-CapNhat-XGIMI-DOHKO'
if (Test-Path $usbX) { Remove-Item $usbX -Recurse -Force }
New-Item -ItemType Directory -Force $usbX | Out-Null
Copy-Item (Join-Path $root 'launcher\updater\build\outputs\apk\xgimi\release\updater-xgimi-release.apk') (Join-Path $usbX 'CapNhatXGIMI-DOHKO.apk')
$guideX = [IO.File]::ReadAllText((Join-Path $here 'HUONG-DAN-USB-XGIMI.txt'), [Text.Encoding]::UTF8)
[IO.File]::WriteAllText((Join-Path $usbX 'HUONG-DAN.txt'), $guideX, (New-Object Text.UTF8Encoding $true))
$zipX = Join-Path $root 'dist\USB-CapNhat-XGIMI-DOHKO.zip'
if (Test-Path $zipX) { Remove-Item $zipX }
Compress-Archive -Path $usbX -DestinationPath $zipX
Write-Host ("Da tao {0} ({1:N0} KB) va {2}" -f $usbX, ((Get-Item (Join-Path $usbX 'CapNhatXGIMI-DOHKO.apk')).Length / 1KB), $zipX)
