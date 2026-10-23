# Cap nhat may chieu XGIMI DOHKO mot cham: tim may chieu tren Wi-Fi, roi
#   1. tat cac goi quang cao / rac (debloat.txt, pm disable-user, khoi phuc duoc)
#   2. cai launcher moi nhat (tvhome.apk), dat lam man hinh chinh, tat launcher XGIMI
#   3. Viet hoa Cai dat XGIMI (tools\xgimi-vi\apply.ps1)
# Chay: nhap dup CapNhat.cmd, hoac: powershell -ExecutionPolicy Bypass -File CapNhat.ps1 [-TvHost 192.168.31.12]
param([string]$TvHost = '')
# Continue: o PowerShell 5.1, stderr cua adb voi Stop se thanh loi dung chuong trinh
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = 'Cập nhật máy chiếu'
$root = $PSScriptRoot
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
$pkg = 'com.gathin.tvhome'
$stock = 'com.xgimi.home'

function Step([string]$t) { Write-Host ''; Write-Host "== $t" -ForegroundColor Cyan }
function Ok([string]$t) { Write-Host "   OK  $t" -ForegroundColor Green }
function Warn([string]$t) { Write-Host "   !!  $t" -ForegroundColor Yellow }
function Done([int]$code) { Write-Host ''; if ($env:CAPNHAT_NOPAUSE) { exit $code }; Read-Host 'Bấm Enter để đóng' | Out-Null; exit $code }

# ---------- Tim may chieu ----------
Step 'Tìm máy chiếu'
Start-Process -FilePath $adb -ArgumentList 'start-server' -WindowStyle Hidden -Wait
if (-not $TvHost) {
    # Quet cong 5555 tren cac mang IPv4 cua may tinh, roi chi giu may XGIMI
    $nets = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and $_.PrefixLength -ge 16 } |
        ForEach-Object { ($_.IPAddress -split '\.')[0..2] -join '.' } | Sort-Object -Unique
    $found = @()
    foreach ($n in $nets) {
        $probes = 1..254 | ForEach-Object { $c = New-Object Net.Sockets.TcpClient; @{ ip = "$n.$_"; c = $c; r = $c.BeginConnect("$n.$_", 5555, $null, $null) } }
        Start-Sleep -Milliseconds 1500
        foreach ($p in $probes) { if ($p.r.IsCompleted -and $p.c.Connected) { $found += $p.ip }; $p.c.Close() }
    }
    $xgimi = @()
    foreach ($ip in $found) {
        & $adb connect "${ip}:5555" | Out-Null
        $model = (& $adb -s "${ip}:5555" shell getprop ro.product.model 2>$null | Out-String).Trim()
        Write-Host "   $ip  $model"
        if ($model -match 'XGIMI') { $xgimi += $ip }
    }
    if ($xgimi.Count -eq 1) { $TvHost = $xgimi[0] }
    elseif ($xgimi.Count -gt 1) { $TvHost = Read-Host "Có nhiều máy chiếu XGIMI ($($xgimi -join ', ')). Nhập IP máy cần cập nhật" }
    else { $TvHost = Read-Host 'Không tự tìm thấy máy chiếu. Nhập IP máy chiếu (xem trong Cài đặt > Mạng)' }
}
$dev = if ($TvHost -match ':') { $TvHost.Trim() } else { "$($TvHost.Trim()):5555" }
& $adb connect $dev | Out-Null
if ((& $adb -s $dev get-state 2>$null | Out-String).Trim() -ne 'device') {
    Warn "Không kết nối được $dev. Bật Gỡ lỗi mạng (ADB) trên máy chiếu; nếu máy hỏi ""Cho phép gỡ lỗi?"" thì chọn Cho phép rồi chạy lại."
    Done 1
}
function Tv([string]$cmd) { (& $adb -s $dev shell $cmd 2>&1 | Out-String).Trim() }
$model = Tv 'getprop ro.product.model'
$fw = Tv 'getprop ro.build.version.incremental'
Ok "Đã kết nối $dev - $model (firmware $fw)"
if ($model -notmatch 'XGIMI') {
    $ans = Read-Host "Gói này làm cho XGIMI DOHKO, còn máy này là '$model'. Vẫn tiếp tục? (c/k)"
    if ($ans -ne 'c') { Done 1 }
}

# Nhat ky: danh sach goi truoc khi cap nhat, de doi chieu / khoi phuc
$logDir = Join-Path $root 'nhat-ky'
New-Item -ItemType Directory -Force $logDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
Tv 'pm list packages -d' | Out-File (Join-Path $logDir "goi-da-tat_truoc_$stamp.txt") -Encoding utf8

# ---------- 1. Debloat ----------
Step '1/3  Tắt quảng cáo và ứng dụng thừa'
$off = Tv 'pm list packages -d'
$n = 0
foreach ($line in Get-Content (Join-Path $root 'debloat.txt') -Encoding UTF8) {
    $p = ($line -replace '#.*$', '').Trim()
    if (-not $p) { continue }
    if ($off -match "package:$([regex]::Escape($p))(\s|$)") { continue }
    if (-not (Tv "pm path $p")) { continue }   # khong co tren may nay
    # moi lenh pm mot lan goi: noi chuoi pm co the dung sau lenh dau
    $r = Tv "pm disable-user --user 0 $p"
    if ($r -match 'disabled') { $n++; Write-Host "   tắt  $p" } else { Warn "$p : $r" }
}
Ok $(if ($n) { "Đã tắt thêm $n gói" } else { 'Các gói đã tắt từ trước' })

# ---------- 2. Launcher ----------
Step '2/3  Cài launcher mới nhất'
$apk = Join-Path $root 'tvhome.apk'
$r = & $adb -s $dev push $apk /data/local/tmp/tvhome.apk 2>&1 | Out-String
$out = Tv 'pm install -r /data/local/tmp/tvhome.apk'
Tv 'rm -f /data/local/tmp/tvhome.apk' | Out-Null
if ($out -notmatch 'Success') { Warn "Cài launcher thất bại: $out"; Done 1 }
Tv "cmd package set-home-activity --user 0 $pkg/.MainActivity" | Out-Null
Tv "pm grant $pkg android.permission.ACCESS_FINE_LOCATION" | Out-Null
Tv "pm grant $pkg android.permission.RECORD_AUDIO" | Out-Null   # nut Tim kiem giong noi
Tv "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS" | Out-Null   # launcher tu bat lai ADB (AdbKeeper)
# Phim micro tren remote mo tim kiem giong noi tu moi ung dung: bat InputKeyService (giu cac dich vu khac)
Tv "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow" | Out-Null
$acc = (Tv 'settings get secure enabled_accessibility_services').Trim()
if ($acc -notmatch 'InputKeyService') {
    $acc = if ($acc -and $acc -ne 'null') { "${acc}:$pkg/.InputKeyService" } else { "$pkg/.InputKeyService" }
    Tv "settings put secure enabled_accessibility_services $acc" | Out-Null
    Tv 'settings put secure accessibility_enabled 1' | Out-Null
}
Tv "cmd package compile -m speed -f $pkg" | Out-Null
# Giao dien XGIMI giu nguyen (cac chuc nang XGIMI goi den no; tat di gay loi): bat lai neu ban truoc da tat,
# nut Home van mo launcher moi nho set-home-activity
if ((Tv "pm list packages -d $stock") -match $stock) { Tv "pm enable --user 0 $stock" | Out-Null; Ok "Bật lại giao diện XGIMI ($stock)" }
Tv "cmd package set-home-activity --user 0 $pkg/.MainActivity" | Out-Null
$homeNow = ((Tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME') -split "`n")[-1].Trim()
if ($homeNow -like "$pkg/*") { Ok "Launcher đã cài và là màn hình chính ($((Tv "dumpsys package $pkg | grep -m1 versionName") -replace '.*=',''))" }
else { Warn "Launcher đã cài nhưng nút Home đang mở '$homeNow'" }

# ---------- 3. Viet hoa Cai dat ----------
Step '3/3  Việt hoá Cài đặt XGIMI'
if ((Tv 'id -u') -eq '0') {
    & (Join-Path $root 'tools\xgimi-vi\apply.ps1') -TvHost $dev
    Ok 'Đã Việt hoá'
} else {
    Warn 'ADB trên máy này không có quyền root: bỏ qua Việt hoá Cài đặt.'
}

Tv 'input keyevent KEYCODE_HOME' | Out-Null
Step 'Xong'
Write-Host "   Máy chiếu $model đã được cập nhật. Nhật ký: $logDir"
Write-Host '   Khôi phục: chạy KhoiPhuc.cmd'
Done 0
