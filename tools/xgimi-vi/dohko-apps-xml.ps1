# Tao file dich (overlay fabricated) cho cac ung dung XGIMI DOHKO hay thay: moi goi mot file ov-<goi>.xml trong
# -OutDir, tu dohko-apps-chuoi.tsv (chuoi goc) va dohko-apps-dich.tsv (ban dich theo cau). boot.sh tao overlay
# VietHoa_<ten cuoi goi> tu moi file ov-*.xml. Dung boi dohko-boot-vi.ps1 va updater\build-usb.ps1.
# Chay: powershell -File tools\xgimi-vi\dohko-apps-xml.ps1 -OutDir <thu muc>
param([Parameter(Mandatory = $true)][string]$OutDir)
$ErrorActionPreference = 'Stop'
$dict = @{}
# dohko-apps-dich.tsv (nhom hay thay) va dohko-apps-dich-them.tsv (cac ung dung con lai)
foreach ($f in Get-ChildItem $PSScriptRoot -Filter 'dohko-apps-dich*.tsv' | Sort-Object Name) {
    foreach ($l in [IO.File]::ReadAllLines($f.FullName, [Text.Encoding]::UTF8)) {
        if (-not $l -or $l.StartsWith('#')) { continue }
        $c = $l.Split("`t")
        # Khoang trang dau/cuoi cua ban dich giu nguyen (" 元" -> " đồng")
        if ($c.Count -ge 2 -and $c[1].Trim()) { if (-not $dict.ContainsKey($c[0])) { $dict[$c[0]] = $c[1].TrimEnd("`r") } }
    }
}
# Cac bang dich chung, tim theo chu goc da gop khoang trang: cung mot cau o ung dung khac da duoc dich
$shared = @{}
foreach ($f in 'dich.tsv', 'dich-cau-dai.tsv', 'rs10-apps-dich.tsv', 'ironman-apps-dich.tsv', 'h6-full-dich.tsv', 'h6-full-dich2.tsv', 'dohko-full-dich.tsv') {
    foreach ($l in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot $f), [Text.Encoding]::UTF8)) {
        if (-not $l -or $l.StartsWith('#')) { continue }
        $c = $l.Split("`t")
        if ($c.Count -ge 2 -and $c[1].Trim()) { $shared[($c[0] -replace '\s+', ' ').Trim()] = $c[1].TrimEnd("`r") }
    }
}
# %s, %1$d...: khac voi ban goc thi ung dung co the hong khi chay, nen giu chu goc
function FormatTokens([string]$s) {
    (([regex]::Matches($s, '%(\d+\$)?[-#+0(]*\d*(\.\d+)?[a-zA-Z%]') | ForEach-Object { $_.Value }) | Sort-Object) -join ' '
}
$byPkg = [ordered]@{}
$seen = @{}
$lines = foreach ($t in 'dohko-apps-chuoi.tsv', 'dohko-full-chuoi.tsv') { [IO.File]::ReadAllLines((Join-Path $PSScriptRoot $t), [Text.Encoding]::UTF8) }
foreach ($l in $lines) {
    if (-not $l -or $l.StartsWith('#')) { continue }
    $c = $l.Split("`t")
    # Mang (@ten) va chuoi JSON noi bo (feature) khong doi duoc bang overlay fabricated
    if ($c.Count -lt 3 -or $c[1].StartsWith('@') -or $c[1] -eq 'feature') { continue }
    if ($seen["$($c[0])|$($c[1])"]) { continue }
    $text = if ($dict.ContainsKey($c[2])) { $dict[$c[2]] } else { $shared[($c[2] -replace '\s+', ' ').Trim()] }
    if (-not $text -or (FormatTokens $c[2]) -ne (FormatTokens $text)) { continue }
    $seen["$($c[0])|$($c[1])"] = $true
    # aapt in \" va \n dang thoat: tra ve dau ngoac kep that, \n thanh xuong dong
    $vi = [Security.SecurityElement]::Escape($text.Replace('\"', '"')).Replace('\n', '&#10;')
    if (-not $byPkg.Contains($c[0])) { $byPkg[$c[0]] = New-Object System.Collections.Generic.List[string] }
    $byPkg[$c[0]].Add("    <item target=`"string/$($c[1])`" value=`"$vi`" />")
}
New-Item -ItemType Directory -Force $OutDir | Out-Null
$utf8 = New-Object Text.UTF8Encoding $false
foreach ($p in $byPkg.Keys) {
    $xml = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<overlay>`n" + ($byPkg[$p] -join "`n") + "`n</overlay>`n"
    [IO.File]::WriteAllText((Join-Path $OutDir "ov-$p.xml"), $xml, $utf8)
}
Write-Host ("Da tao {0} file dich ung dung ({1} chuoi) trong {2}" -f $byPkg.Count, ($byPkg.Values | ForEach-Object { $_.Count } | Measure-Object -Sum).Sum, $OutDir)
