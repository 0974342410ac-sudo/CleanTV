# Viet hoa Cai dat XGIMI (com.android.newsettings) tren may chieu XGIMI DOHKO bang overlay "fabricated":
# Android 14 cho shell root tao overlay tai nguyen (cmd overlay fabricate). May chieu nay cho adb root san
# (ro.adb.secure=0), nen khong can ky bang khoa cua XGIMI. Khong sua phan vung he thong.
#
#   dich.tsv        chu tieng Trung<TAB>tieng Viet (them dong de dich them)
#   chuoi-goc.csv   ten chuoi va chu tieng Trung lay tu APK cua firmware v1.6.50
#
# Dung:  tools\xgimi-vi\apply.ps1 -TvHost 192.168.31.12          (bat / cap nhat ban dich)
#        tools\xgimi-vi\apply.ps1 -TvHost 192.168.31.12 -Remove  (tro ve tieng Trung)
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [switch]$Remove
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
$dev = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }
$target = 'com.android.newsettings'
$name = 'VietHoa'
$overlay = "com.android.shell:$name"

& $adb connect $dev | Out-Null
function Tv([string]$cmd) { (& $adb -s $dev shell $cmd | Out-String).Trim() }
if ((Tv 'id -u') -ne '0') { throw 'ADB tren may nay khong chay quyen root; khong tao duoc overlay.' }

if ($Remove) {
    Tv "cmd overlay disable --user 0 $overlay" | Out-Null
    # Overlay fabricated nam o /data/resource-cache; xoa file de go han
    Tv "rm -f /data/resource-cache/*$name*.frro /data/resource-cache/*$name*.idmap" | Out-Null
    Tv "am force-stop $target" | Out-Null
    Write-Host 'Da go ban Viet hoa. Cai dat XGIMI tro ve tieng Trung.'
    exit 0
}

$dict = @{}
$lines = foreach ($f in 'dich.tsv', 'dich-cau-dai.tsv') { $p = Join-Path $PSScriptRoot $f; if (Test-Path $p) { [IO.File]::ReadAllLines($p, [Text.Encoding]::UTF8) } }
foreach ($line in $lines) {
    if (-not $line -or $line.StartsWith('#')) { continue }
    $p = $line.Split("`t")
    if ($p.Count -ge 2 -and $p[1].Trim()) { $dict[($p[0] -replace '\s+', ' ').Trim()] = $p[1].Trim() }
}
$items = foreach ($row in (Import-Csv (Join-Path $PSScriptRoot 'chuoi-goc.csv') -Encoding UTF8)) {
    if ($dict.ContainsKey(($row.v -replace '\s+', ' ').Trim())) {
        # \n trong ban dich la xuong dong that
        $vi =[Security.SecurityElement]::Escape($dict[($row.v -replace '\s+', ' ').Trim()]).Replace('\n', '&#10;')
        "    <item target=`"string/$($row.n)`" value=`"$vi`" />"
    }
}
$xml = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<overlay>`n" + ($items -join "`n") + "`n</overlay>`n"
$tmp = Join-Path $env:TEMP 'xgimi-vi.xml'
[IO.File]::WriteAllText($tmp, $xml, (New-Object Text.UTF8Encoding $false))
& $adb -s $dev push $tmp /data/local/tmp/xgimi-vi.xml | Out-Null

if ((Tv "cmd overlay list $target") -match [regex]::Escape($overlay)) { Tv "cmd overlay disable --user 0 $overlay" | Out-Null }
$out = Tv "cmd overlay fabricate --target $target --name $name --file /data/local/tmp/xgimi-vi.xml"
Tv 'rm -f /data/local/tmp/xgimi-vi.xml' | Out-Null
if ($out) { Write-Host $out }
if ($out -match 'Error|Exception') { throw "Tao overlay that bai; ban Viet hoa cu (neu co) duoc giu nguyen." }
Tv "cmd overlay enable --user 0 $overlay" | Out-Null
$state = Tv "cmd overlay list $target"
if ($state -notmatch "\[x\] $([regex]::Escape($overlay))") { throw "Overlay khong bat duoc:`n$state" }
Tv "am force-stop $target" | Out-Null
Write-Host "Da Viet hoa $(@($items).Count) dong chu trong Cai dat XGIMI. Go: apply.ps1 -TvHost $TvHost -Remove"
