# Cai (hoac go) cac file dich tieng Viet dist\rs10-vi\VietHoa-*.apk len may chieu XGIMI RS10 (Android 9) qua ADB mang.
# Chay rs10-overlays.ps1 truoc. Chua thu tren may that: lan dau hay kiem tra tung buoc.
#   powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\rs10-install.ps1 -TvHost 192.168.31.x
#   powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\rs10-install.ps1 -TvHost 192.168.31.x -Remove
param([Parameter(Mandatory = $true)][string]$TvHost, [switch]$Remove)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
$dev = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }
& $adb connect $dev | Out-Host
$sdk = (& $adb -s $dev shell getprop ro.build.version.sdk).Trim()
if ($sdk -ne '28') { throw "may nay la SDK $sdk, ban dich nay chi danh cho RS10 Android 9 (SDK 28)" }

if ($Remove) {
    foreach ($p in (& $adb -s $dev shell pm list packages vn.cleantv.overlay.rs10.)) {
        $pkg = $p.Trim() -replace '^package:', ''
        if (-not $pkg) { continue }
        & $adb -s $dev shell cmd overlay disable --user 0 $pkg | Out-Null
        & $adb -s $dev uninstall $pkg | Out-Null
        Write-Host "da go  $pkg"
    }
    return
}

$apks = Get-ChildItem (Join-Path $root 'dist\rs10-vi') -Filter 'VietHoa-*.apk'
if (-not $apks) { throw 'chua co file dich: chay tools\xgimi-vi\rs10-overlays.ps1 truoc' }
$ok = 0
foreach ($apk in $apks) {
    $target = $apk.BaseName -replace '^VietHoa-', ''
    if (-not (& $adb -s $dev shell pm path $target)) { Write-Host "bo qua  $target (khong co tren may)"; continue }
    $pkg = 'vn.cleantv.overlay.rs10.' + ($target -split '\.')[-1]
    $r = & $adb -s $dev install -r $apk.FullName 2>&1 | Out-String
    if ($r -notmatch 'Success') { Write-Host "!! cai $pkg that bai: $($r.Trim())"; continue }
    & $adb -s $dev shell cmd overlay enable --user 0 $pkg | Out-Null
    $state = & $adb -s $dev shell cmd overlay list $target | Out-String
    if ($state -match "\[x\] $([regex]::Escape($pkg))") { $ok++; Write-Host "OK  $target" }
    else { Write-Host "!! $pkg da cai nhung chua bat duoc:`n$state" }
}
# Ung dung dang chay chi nhan ban dich sau khi khoi dong lai
& $adb -s $dev shell 'for p in com.android.newsettings com.xgimi.home com.xgimi.systemui; do am force-stop $p; done' | Out-Null
Write-Host "Da bat $ok/$($apks.Count) file dich. Go: them -Remove"
