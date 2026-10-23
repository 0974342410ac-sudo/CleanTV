# Hoan tac CapNhat.ps1: bat lai cac goi trong debloat.txt, tra launcher XGIMI, go ban Viet hoa Cai dat.
# Launcher moi duoc giu lai (khong go) tru khi them -GoLauncher.
param([string]$TvHost = '', [switch]$GoLauncher)
# Continue: o PowerShell 5.1, stderr cua adb voi Stop se thanh loi dung chuong trinh
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$root = $PSScriptRoot
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
if (-not $TvHost) { $TvHost = Read-Host 'Nhập IP máy chiếu' }
$dev = if ($TvHost -match ':') { $TvHost.Trim() } else { "$($TvHost.Trim()):5555" }
Start-Process -FilePath $adb -ArgumentList 'start-server' -WindowStyle Hidden -Wait
& $adb connect $dev | Out-Null
function Tv([string]$cmd) { (& $adb -s $dev shell $cmd 2>&1 | Out-String).Trim() }
if ((& $adb -s $dev get-state 2>$null | Out-String).Trim() -ne 'device') { Write-Host "Không kết nối được $dev"; Read-Host 'Enter' | Out-Null; exit 1 }

foreach ($line in Get-Content (Join-Path $root 'debloat.txt') -Encoding UTF8) {
    $p = ($line -replace '#.*$', '').Trim()
    if ($p) { Write-Host "bật  $p"; Tv "pm enable --user 0 $p" | Out-Null }
}
Tv 'cmd package set-home-activity --user 0 com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity' | Out-Null
& (Join-Path $root 'tools\xgimi-vi\apply.ps1') -TvHost $dev -Remove
if ($GoLauncher) { Write-Host (Tv 'pm uninstall com.gathin.tvhome') }
Tv 'input keyevent KEYCODE_HOME' | Out-Null
Write-Host 'Đã khôi phục.'
Read-Host 'Bấm Enter để đóng' | Out-Null
