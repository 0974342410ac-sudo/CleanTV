# Dong goi dist\PC-CapNhat (+ .zip): ban cap nhat chay tren may tinh Windows. CapNhat.cmd tim TV qua Wi-Fi, cai
# ung dung cap nhat cua goi USB len TV va cho no tu chay, nen moi may lam giong het goi USB. KhoiPhuc.cmd hoan tac.
# build-usb.ps1 goi script nay sau khi dung xong cac goi USB (can dist\USB-CapNhat va dist\USB-CapNhat-XGIMI-DOHKO).
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent $here
$out = Join-Path $root 'dist\PC-CapNhat'
foreach ($old in $out, (Join-Path $root 'dist\CapNhatMayChieu.zip')) { if (Test-Path $old) { Remove-Item $old -Recurse -Force } }
New-Item -ItemType Directory -Force "$out\tools\platform-tools" | Out-Null

function Copy-Bom([string]$src, [string]$dst) {
    $t = [IO.File]::ReadAllText($src, [Text.Encoding]::UTF8)
    [IO.File]::WriteAllText($dst, $t, (New-Object Text.UTF8Encoding $true))
}
Copy-Bom "$here\CapNhat.ps1" "$out\CapNhat.ps1"
Copy-Bom "$here\KhoiPhuc.ps1" "$out\KhoiPhuc.ps1"
Copy-Bom "$here\HUONG-DAN.txt" "$out\HUONG-DAN.txt"
foreach ($c in 'CapNhat', 'KhoiPhuc') {
    "@echo off`r`npowershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0$c.ps1`" %*`r`n" |
        Set-Content "$out\$c.cmd" -Encoding ascii -NoNewline
}
foreach ($apk in 'USB-CapNhat\CapNhatMayChieu.apk', 'USB-CapNhat-XGIMI-DOHKO\CapNhatXGIMI-DOHKO.apk') {
    $src = Join-Path $root "dist\$apk"
    if (-not (Test-Path $src)) { throw "Chua co $src (chay build-usb.ps1, them -XgimiDohko cho ban DOHKO)" }
    Copy-Item $src $out
}
foreach ($f in 'adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'libwinpthread-1.dll', 'NOTICE.txt', 'source.properties') {
    Copy-Item "$root\tools\platform-tools\$f" "$out\tools\platform-tools\"
}
$zip = "$out.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path $out -DestinationPath $zip
Write-Host ("Da tao {0} va {1} ({2:N0} KB)" -f $out, $zip, ((Get-Item $zip).Length / 1KB))
