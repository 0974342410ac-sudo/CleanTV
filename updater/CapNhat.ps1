# Cap nhat TV / may chieu tu may tinh qua Wi-Fi, giong het goi USB: tim TV, cai ung dung cap nhat cua goi USB
# (CapNhatMayChieu.apk, hoac CapNhatXGIMI-DOHKO.apk cho XGIMI DOHKO) len TV, cho no tu bam "Cap nhat"
# (hoac "Khoi phuc" voi -Restore), roi hien nhat ky cua no tren may tinh.
# Chay: nhap dup CapNhat.cmd / KhoiPhuc.cmd, hoac: powershell -ExecutionPolicy Bypass -File CapNhat.ps1 [-TvHost 192.168.31.12] [-Restore]
param([string]$TvHost = '', [switch]$Restore)
# Continue: o PowerShell 5.1, stderr cua adb voi Stop se thanh loi dung chuong trinh
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = if ($Restore) { 'Khôi phục TV' } else { 'Cập nhật TV' }
$root = $PSScriptRoot
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
$job = if ($Restore) { 'restore' } else { 'update' }

function Step([string]$t) { Write-Host ''; Write-Host "== $t" -ForegroundColor Cyan }
function Ok([string]$t) { Write-Host "   OK  $t" -ForegroundColor Green }
function Warn([string]$t) { Write-Host "   !!  $t" -ForegroundColor Yellow }
function Done([int]$code) { Write-Host ''; if ($env:CAPNHAT_NOPAUSE) { exit $code }; Read-Host 'Bấm Enter để đóng' | Out-Null; exit $code }
function Tv([string]$cmd) { (& $adb -s $dev shell $cmd 2>$null | Out-String).Trim() }

# ---------- Tim TV ----------
Step 'Tìm TV / máy chiếu'
Start-Process -FilePath $adb -ArgumentList 'start-server' -WindowStyle Hidden -Wait
if (-not $TvHost) {
    # Quet cong 5555 (ADB mang) tren cac mang IPv4 cua may tinh
    $nets = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and $_.PrefixLength -ge 16 } |
        ForEach-Object { ($_.IPAddress -split '\.')[0..2] -join '.' } | Sort-Object -Unique
    $found = @()
    foreach ($n in $nets) {
        $probes = 1..254 | ForEach-Object { $c = New-Object Net.Sockets.TcpClient; @{ ip = "$n.$_"; c = $c; r = $c.BeginConnect("$n.$_", 5555, $null, $null) } }
        Start-Sleep -Milliseconds 1500
        foreach ($p in $probes) { if ($p.r.IsCompleted -and $p.c.Connected) { $found += $p.ip }; $p.c.Close() }
    }
    $tvs = @()
    foreach ($ip in $found) {
        & $adb connect "${ip}:5555" | Out-Null
        $model = (& $adb -s "${ip}:5555" shell getprop ro.product.model 2>$null | Out-String).Trim()
        if (-not $model) { $model = '(chưa cho phép gỡ lỗi, hoặc đang bận)' }
        $tvs += $ip
        Write-Host ("   {0}. {1}  {2}" -f $tvs.Count, $ip, $model)
    }
    if ($tvs.Count -eq 1) { $TvHost = $tvs[0] }
    else {
        $ask = if ($tvs.Count -gt 1) { 'Nhập số thứ tự hoặc IP của TV cần làm' } else { 'Không tự tìm thấy TV. Nhập IP của TV (xem trong Cài đặt > Mạng)' }
        $TvHost = (Read-Host $ask).Trim()
        if ($TvHost -match '^\d+$' -and [int]$TvHost -ge 1 -and [int]$TvHost -le $tvs.Count) { $TvHost = $tvs[[int]$TvHost - 1] }
    }
}
if (-not $TvHost) { Done 1 }
$dev = if ($TvHost -match ':') { $TvHost.Trim() } else { "$($TvHost.Trim()):5555" }

# Ket noi; TV co the hien hop "Cho phep go loi?" cho khoa ADB cua may tinh nay
& $adb connect $dev | Out-Null
$state = ''
for ($i = 0; $i -lt 30; $i++) {
    $state = (& $adb -s $dev get-state 2>$null | Out-String).Trim()
    if ($state -eq 'device') { break }
    if ($i -eq 0) { Warn 'Trên TV, nếu hiện hộp "Cho phép gỡ lỗi?": chọn "Luôn cho phép" rồi bấm Cho phép.' }
    Start-Sleep 2
    & $adb disconnect $dev 2>$null | Out-Null
    & $adb connect $dev | Out-Null
}
if ($state -ne 'device') {
    Warn "Không kết nối được $dev. Bật Gỡ lỗi mạng (ADB) trên TV, cho phép máy tính này, rồi chạy lại."
    Done 1
}
$device = Tv 'getprop ro.product.device'
Ok ("{0}  {1} (Android {2})" -f $dev, (Tv 'getprop ro.product.model'), (Tv 'getprop ro.build.version.release'))

# Dang co nguoi xem? (TV dang thuc va khong o man hinh chinh)
$awake = (Tv 'dumpsys power | grep mWakefulness=') -match 'Awake'
$focus = Tv 'dumpsys window | grep mCurrentFocus'
if ($awake -and $focus -match ' u0 ' -and $focus -notmatch 'com\.gathin\.tvhome|launcher|home|Launcher|Home') {
    Warn "TV đang mở: $(($focus -replace '.* u0 ', '' -replace '}.*', ''))"
    $answer = Read-Host '   Có thể có người đang xem. Vẫn làm tiếp? (c/k)'
    if (-not $answer -or $answer.Trim() -notmatch '^[cCyY]') { Done 1 }
}
# TV dang ngu: Android chan mang cua ung dung (ca ket noi 127.0.0.1), nen danh thuc truoc
if (-not $awake) {
    Tv 'input keyevent KEYCODE_WAKEUP' | Out-Null
    Start-Sleep 3
    Ok 'đã bật màn hình TV'
}

# ---------- Cai ung dung cap nhat len TV ----------
Step 'Cài ứng dụng cập nhật lên TV'
if ($device -eq 'horse_popular') { $apk = 'CapNhatXGIMI-DOHKO.apk'; $pkg = 'vn.cleantv.updater.xgimi' }
else { $apk = 'CapNhatMayChieu.apk'; $pkg = 'vn.cleantv.updater' }
$out = (& $adb -s $dev install -r (Join-Path $root $apk) 2>&1 | Out-String).Trim()
if ($out -notmatch 'Success') { Warn "Cài $apk thất bại: $out"; Done 1 }
Ok $apk

# ---------- Chay tren TV ----------
Step $(if ($Restore) { 'Khôi phục (ứng dụng trên TV đang chạy)' } else { 'Cập nhật (ứng dụng trên TV đang chạy)' })
$since = Tv "date '+%m-%d %H:%M:%S.000'"
Tv "am start -S -n $pkg/vn.cleantv.updater.UpdaterActivity --es job $job" | Out-Null
# Tha ket noi: ung dung tren TV dung ADB cua chinh TV (Android 6 chi nhan mot ket noi mang)
& $adb disconnect $dev | Out-Null
$legacy = $device -eq 'synsepalum_YN'
$shown = 0
$end = (Get-Date).AddMinutes(15)
$finished = $false
while ((Get-Date) -lt $end -and -not $finished) {
    Start-Sleep -Seconds $(if ($legacy) { 15 } else { 2 })
    & $adb connect $dev 2>$null | Out-Null
    if ((& $adb -s $dev get-state 2>$null | Out-String).Trim() -ne 'device') { continue }
    $lines = @(& $adb -s $dev shell "logcat -d -v raw -s CapNhat:I -T '$since'" 2>$null |
        Where-Object { $_ -and $_ -notmatch '^-+ beginning of' -and $_ -notmatch '^(reopen |exec )' })
    for ($i = $shown; $i -lt $lines.Count; $i++) {
        $l = $lines[$i]
        if ($l -match 'KET THUC') { $finished = $true; break }
        $color = if ($l -match '!!') { 'Yellow' } elseif ($l -match '^\s*OK') { 'Green' } elseif ($l -match '^==') { 'Cyan' } else { 'Gray' }
        Write-Host $l -ForegroundColor $color
    }
    $shown = $lines.Count
    if ($legacy) { & $adb disconnect $dev | Out-Null }
}
if (-not $finished) { Warn 'Chưa thấy ứng dụng trên TV báo xong: xem trên màn hình TV.' }
& $adb disconnect $dev | Out-Null
Done 0
