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
$byPkg = [ordered]@{}
foreach ($l in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'dohko-apps-chuoi.tsv'), [Text.Encoding]::UTF8)) {
    if (-not $l -or $l.StartsWith('#')) { continue }
    $c = $l.Split("`t")
    # Mang (@ten) va chuoi JSON noi bo (feature) khong doi duoc bang overlay fabricated
    if ($c.Count -lt 3 -or $c[1].StartsWith('@') -or $c[1] -eq 'feature' -or -not $dict.ContainsKey($c[2])) { continue }
    # aapt in \" va \n dang thoat: tra ve dau ngoac kep that, \n thanh xuong dong
    $vi = [Security.SecurityElement]::Escape($dict[$c[2]].Replace('\"', '"')).Replace('\n', '&#10;')
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
