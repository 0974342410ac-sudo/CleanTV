# Viet hoa menu tat may (com.xgimi.shutdown) tren may chieu XGIMI DOHKO bang overlay "fabricated", nhu apply.ps1
# lam cho Cai dat. Ban dich nam trong dohko-shutdown-vi.tsv (ten chuoi<TAB>tieng Viet). Can adb root (co san).
#
# Dung:  tools\xgimi-vi\dohko-shutdown-vi.ps1 -TvHost 192.168.31.12          (bat / cap nhat ban dich)
#        tools\xgimi-vi\dohko-shutdown-vi.ps1 -TvHost 192.168.31.12 -Remove  (tro ve tieng Anh)
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [switch]$Remove
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
$dev = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }
$target = 'com.xgimi.shutdown'
$name = 'VietHoaTatMay'
$overlay = "com.android.shell:$name"

& $adb connect $dev | Out-Null
function Tv([string]$cmd) { (& $adb -s $dev shell $cmd | Out-String).Trim() }
if ((Tv 'id -u') -ne '0') { throw 'ADB tren may nay khong chay quyen root; khong tao duoc overlay.' }

if ($Remove) {
    Tv "cmd overlay disable --user 0 $overlay" | Out-Null
    Tv "rm -f /data/resource-cache/*$name*.frro /data/resource-cache/*$name*.idmap" | Out-Null
    Tv "am force-stop $target" | Out-Null
    Write-Host 'Da go ban Viet hoa. Menu tat may tro ve tieng Anh.'
    exit 0
}

$items = foreach ($line in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'dohko-shutdown-vi.tsv'), [Text.Encoding]::UTF8)) {
    if (-not $line -or $line.StartsWith('#')) { continue }
    $p = $line.Split("`t")
    if ($p.Count -ge 2 -and $p[1].Trim()) {
        "    <item target=`"string/$($p[0].Trim())`" value=`"$([Security.SecurityElement]::Escape($p[1].Trim()))`" />"
    }
}
$xml = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<overlay>`n" + ($items -join "`n") + "`n</overlay>`n"
$tmp = Join-Path $env:TEMP 'xgimi-shutdown-vi.xml'
[IO.File]::WriteAllText($tmp, $xml, (New-Object Text.UTF8Encoding $false))
& $adb -s $dev push $tmp /data/local/tmp/xgimi-shutdown-vi.xml | Out-Null

if ((Tv "cmd overlay list $target") -match [regex]::Escape($overlay)) { Tv "cmd overlay disable --user 0 $overlay" | Out-Null }
$out = Tv "cmd overlay fabricate --target $target --name $name --file /data/local/tmp/xgimi-shutdown-vi.xml"
Tv 'rm -f /data/local/tmp/xgimi-shutdown-vi.xml' | Out-Null
if ($out) { Write-Host $out }
if ($out -match 'Error|Exception') { throw "Tao overlay that bai; ban Viet hoa cu (neu co) duoc giu nguyen." }
Tv "cmd overlay enable --user 0 $overlay" | Out-Null
$state = Tv "cmd overlay list $target"
if ($state -notmatch "\[x\] $([regex]::Escape($overlay))") { throw "Overlay khong bat duoc:`n$state" }
Tv "am force-stop $target" | Out-Null
Write-Host "Da Viet hoa $(@($items).Count) dong chu trong menu tat may. Go: dohko-shutdown-vi.ps1 -TvHost $TvHost -Remove"
