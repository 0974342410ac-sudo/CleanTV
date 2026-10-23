# Giu ban dich tieng Viet qua moi lan khoi dong tren may chieu XGIMI DOHKO (Android 14).
# May nay xoa cac overlay "fabricated" (VietHoa cho Cai dat, VietHoaTatMay cho menu tat may) moi lan khoi dong.
# Script nay dat file dich va boot.sh vao /data/local/tmp/cleantv (chu root), bat boot_script trong config.json
# cua launcher; sau moi lan khoi dong launcher chay boot.sh qua ADB cua chinh may (BootScript.java) de tao lai.
# Can: adb root (co san tren may nay), launcher co keep_adb_on.
# Dung:  tools\xgimi-vi\dohko-boot-vi.ps1 -TvHost 192.168.31.12          (cai / cap nhat, chay ngay mot lan)
#        tools\xgimi-vi\dohko-boot-vi.ps1 -TvHost 192.168.31.12 -Remove  (bo tu dong; ban dich hien tai giu den lan khoi dong sau)
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [switch]$Remove
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
$dev = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }
$dir = '/data/local/tmp/cleantv'
$config = '/sdcard/Android/data/com.gathin.tvhome/files/config.json'
$utf8 = New-Object Text.UTF8Encoding $false

& $adb connect $dev | Out-Null
function Tv([string]$cmd) { (& $adb -s $dev shell $cmd | Out-String).Trim() }
if ((Tv 'getprop ro.product.device') -ne 'horse_popular') { throw 'Day khong phai may XGIMI DOHKO' }
if ((Tv 'id -u') -ne '0') { throw 'Can adb root' }

# boot_script trong config.json cua launcher (giu cac khoa khac)
$cfg = Tv "cat $config 2>/dev/null"
if (-not $cfg.StartsWith('{')) { $cfg = '{}' }
$cfg = $cfg -replace '"boot_script"\s*:\s*(true|false)\s*,?\s*', ''
$cfg = $cfg -replace '^\{\s*', ("{`n  `"boot_script`": " + $(if ($Remove) { 'false' } else { 'true' }) + ",`n  ")
$cfg = $cfg -replace ',\s*\}$', "`n}"
$tmp = Join-Path $env:TEMP 'cleantv-config.json'
[IO.File]::WriteAllText($tmp, $cfg, $utf8)
& $adb -s $dev push $tmp $config | Out-Null
Tv "chmod 644 $config" | Out-Null

if ($Remove) {
    Tv "rm -rf $dir" | Out-Null
    Write-Host 'Da bo tu dong Viet hoa sau khoi dong.'
    exit 0
}

function Overlay-Xml([string[]]$items) {
    "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<overlay>`n" + ($items -join "`n") + "`n</overlay>`n"
}
# Cai dat XGIMI: nhu apply.ps1 (dich.tsv + dich-cau-dai.tsv theo chuoi-goc.csv)
$dict = @{}
foreach ($f in 'dich.tsv', 'dich-cau-dai.tsv') {
    $p = Join-Path $PSScriptRoot $f
    if (-not (Test-Path $p)) { continue }
    foreach ($line in [IO.File]::ReadAllLines($p, [Text.Encoding]::UTF8)) {
        if (-not $line -or $line.StartsWith('#')) { continue }
        $c = $line.Split("`t")
        if ($c.Count -ge 2 -and $c[1].Trim()) { $dict[($c[0] -replace '\s+', ' ').Trim()] = $c[1].Trim() }
    }
}
$settings = foreach ($row in (Import-Csv (Join-Path $PSScriptRoot 'chuoi-goc.csv') -Encoding UTF8)) {
    $k = ($row.v -replace '\s+', ' ').Trim()
    if ($dict.ContainsKey($k)) {
        "    <item target=`"string/$($row.n)`" value=`"$([Security.SecurityElement]::Escape($dict[$k]).Replace('\n', '&#10;'))`" />"
    }
}
# Menu tat may: nhu dohko-shutdown-vi.ps1
$power = foreach ($line in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'dohko-shutdown-vi.tsv'), [Text.Encoding]::UTF8)) {
    if (-not $line -or $line.StartsWith('#')) { continue }
    $c = $line.Split("`t")
    if ($c.Count -ge 2 -and $c[1].Trim()) {
        "    <item target=`"string/$($c[0].Trim())`" value=`"$([Security.SecurityElement]::Escape($c[1].Trim()))`" />"
    }
}
# Chung voi ban cap nhat USB (build-usb.ps1 dua file nay vao ung dung cap nhat)
$boot = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'dohko-boot.sh'), [Text.Encoding]::UTF8)
$local = Join-Path $env:TEMP 'cleantv-boot'
New-Item -ItemType Directory -Force $local | Out-Null
[IO.File]::WriteAllText("$local\settings-vi.xml", (Overlay-Xml $settings), $utf8)
[IO.File]::WriteAllText("$local\shutdown-vi.xml", (Overlay-Xml $power), $utf8)
[IO.File]::WriteAllText("$local\boot.sh", $boot.Replace("`r", ''), $utf8)
# Cac ung dung XGIMI khac (HDMI, SystemUI, cai ung dung, lay net, Cua than ky...): ov-<goi>.xml
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'dohko-apps-xml.ps1') -OutDir $local
Tv "rm -rf $dir; mkdir -p $dir" | Out-Null
foreach ($f in Get-ChildItem $local -File) { & $adb -s $dev push $f.FullName "$dir/$($f.Name)" | Out-Null }
# Chu root: shell khong sua duoc; file dich doc duoc cho system_server (cmd overlay fabricate mo file)
Tv "chown -R root:root $dir; chmod 755 $dir; chmod 644 $dir/*.xml; chmod 700 $dir/boot.sh" | Out-Null
Remove-Item $local -Recurse -Force
Write-Host ("Da dat {0} chuoi Cai dat, {1} chuoi menu tat may va boot.sh vao {2}" -f @($settings).Count, @($power).Count, $dir)
Write-Host ('Chay thu ngay: ' + (Tv "sh $dir/boot.sh"))
