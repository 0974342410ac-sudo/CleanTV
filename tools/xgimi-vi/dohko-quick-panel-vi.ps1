# Viet hoa ten o trong bang cai dat nhanh cua may chieu XGIMI DOHKO (can adb root, co san tren may nay).
# Ten nam trong co so du lieu cua com.android.newsettings, khong phai chuoi tai nguyen: keo ve, chay
# dohko-quick-panel-vi.sql bang sqlite3.exe, roi tra lai khi newsettings dang dung. File SQL dat trigger trong co so
# du lieu: newsettings tai cau hinh tu may chu XGIMI va ghi de ten tieng Trung moi lan mo, trigger dich lai ngay.
# Chay: powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\dohko-quick-panel-vi.ps1 -TvHost 192.168.31.12
param([Parameter(Mandatory = $true)][string]$TvHost)
# Continue: adb writes progress to stderr, which Windows PowerShell 5.1 turns into errors under Stop
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
$sq = Join-Path $root 'tools\platform-tools\sqlite3.exe'
$dev = "${TvHost}:5555"
$db = '/data/data/com.android.newsettings/databases/misckey_config.db'
$work = Join-Path $env:TEMP ('dohko-quick-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $work | Out-Null

& $adb connect $dev | Out-Null
if ((& $adb -s $dev shell getprop ro.product.device).Trim() -ne 'horse_popular') { throw 'Day khong phai may XGIMI DOHKO' }
if ((& $adb -s $dev shell id -u).Trim() -ne '0') { throw 'Can adb root' }
# Ban sao luu tren may (chi tao lan dau, giu ban goc tieng Trung)
& $adb -s $dev shell "[ -f /sdcard/misckey_config.db.bak ] || cp $db /sdcard/misckey_config.db.bak"
# newsettings dung truoc khi chep: file khong con bi giu, WAL cung da ghi vao file chinh
& $adb -s $dev shell 'am force-stop com.android.newsettings; sleep 1'
& $adb -s $dev shell "mkdir -p /data/local/tmp/mk; cp $db $db-wal $db-shm /data/local/tmp/mk/ 2>/dev/null; chmod 644 /data/local/tmp/mk/*"
foreach ($f in 'misckey_config.db', 'misckey_config.db-wal', 'misckey_config.db-shm') {
    & $adb -s $dev pull "/data/local/tmp/mk/$f" (Join-Path $work $f) | Out-Null
}
$local = Join-Path $work 'misckey_config.db'
if (-not (Test-Path $local)) { throw 'Khong keo duoc co so du lieu ve may tinh' }
$sql = (Join-Path $PSScriptRoot 'dohko-quick-panel-vi.sql').Replace('\', '/')
& $sq $local 'PRAGMA wal_checkpoint(TRUNCATE);' ".read '$sql'" 'PRAGMA wal_checkpoint(TRUNCATE);' | Out-Null
if ((& $sq $local 'PRAGMA integrity_check;') -ne 'ok') { throw 'Co so du lieu sau khi sua bi loi: khong chep len may' }
& $adb -s $dev push $local /data/local/tmp/mk/new.db | Out-Null
& $adb -s $dev shell "am force-stop com.android.newsettings; cp /data/local/tmp/mk/new.db $db; rm -f $db-wal $db-shm; chown system:system $db; chmod 660 $db; restorecon $db; rm -rf /data/local/tmp/mk; ls -lZ $db"
Remove-Item $work -Recurse -Force
Write-Host 'Xong. Bam phim cai dat nhanh tren remote de xem. Tra lai ban goc: cp /sdcard/misckey_config.db.bak len $db (adb root).'
