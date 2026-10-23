# Re-enables every package in examples\xgimi-dohko\debloat.txt and gives Home back to XGIMI's launcher.
# Only needs the bundled adb (no Python).
#
# Usage: tools\restore-xgimi-dohko.ps1 -TvHost 192.168.31.12 [-KeepLauncher]
#   -KeepLauncher  re-enable the packages but leave com.gathin.tvhome as Home
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [switch]$KeepLauncher
)
$ErrorActionPreference = 'Continue'
$adb = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
$list = Join-Path $PSScriptRoot '..\examples\xgimi-dohko\debloat.txt'
$dev = "${TvHost}:5555"

& $adb connect $dev | Out-Null
$pkgs = Get-Content $list | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
foreach ($p in $pkgs) {
    if ($KeepLauncher -and $p -eq 'com.xgimi.home') { continue }
    # one pm call per shell: chained pm commands can stop silently after the first
    & $adb -s $dev shell pm enable --user 0 $p
}
if (-not $KeepLauncher) {
    & $adb -s $dev shell cmd package set-home-activity --user 0 com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity
    & $adb -s $dev shell input keyevent KEYCODE_HOME
}
Write-Host "Done. Disabled now:"
& $adb -s $dev shell pm list packages -d
