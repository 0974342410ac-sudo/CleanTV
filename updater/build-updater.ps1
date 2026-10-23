# Dong goi dist\CapNhatMayChieu.zip: CapNhat.cmd (cap nhat 1 cham), KhoiPhuc.cmd, launcher moi nhat,
# danh sach debloat cua XGIMI DOHKO, ban Viet hoa Cai dat va adb.
# Chay: powershell -ExecutionPolicy Bypass -File updater\build-updater.ps1
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent $here
$stage = Join-Path $env:TEMP 'CapNhatMayChieu'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force "$stage\tools\platform-tools", "$stage\tools\xgimi-vi" | Out-Null

function Copy-Bom([string]$src, [string]$dst) {
    $t = [IO.File]::ReadAllText($src, [Text.Encoding]::UTF8)
    [IO.File]::WriteAllText($dst, $t, (New-Object Text.UTF8Encoding $true))
}
Copy-Bom "$here\CapNhat.ps1" "$stage\CapNhat.ps1"
Copy-Bom "$here\KhoiPhuc.ps1" "$stage\KhoiPhuc.ps1"
Copy-Bom "$here\HUONG-DAN.txt" "$stage\HUONG-DAN.txt"
foreach ($c in 'CapNhat', 'KhoiPhuc') {
    "@echo off`r`npowershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0$c.ps1`" %*`r`n" |
        Set-Content "$stage\$c.cmd" -Encoding ascii -NoNewline
}
Copy-Item "$root\examples\xgimi-dohko\debloat.txt" "$stage\debloat.txt"
$apk = "$root\launcher\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "Chua co APK launcher: $apk" }
Copy-Item $apk "$stage\tvhome.apk"
Copy-Bom "$root\tools\xgimi-vi\apply.ps1" "$stage\tools\xgimi-vi\apply.ps1"
Copy-Bom "$root\tools\xgimi-vi\dich.tsv" "$stage\tools\xgimi-vi\dich.tsv"
Copy-Bom "$root\tools\xgimi-vi\dich-cau-dai.tsv" "$stage\tools\xgimi-vi\dich-cau-dai.tsv"
Copy-Item "$root\tools\xgimi-vi\chuoi-goc.csv" "$stage\tools\xgimi-vi\"
foreach ($f in 'adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'libwinpthread-1.dll', 'NOTICE.txt', 'source.properties') {
    Copy-Item "$root\tools\platform-tools\$f" "$stage\tools\platform-tools\"
}
$zip = Join-Path $root 'dist\CapNhatMayChieu.zip'
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path "$stage\*" -DestinationPath $zip
Remove-Item $stage -Recurse -Force
Write-Host ("Da tao {0} ({1:N0} KB)" -f $zip, ((Get-Item $zip).Length / 1KB))
