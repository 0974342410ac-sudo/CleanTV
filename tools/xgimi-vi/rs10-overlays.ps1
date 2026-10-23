# Dung cac file dich (overlay) tieng Viet cho may chieu XGIMI RS10 va H6 (Android 9, m7642, firmware XGIMI_PIGCHIVALROUS_*):
# moi ung dung XGIMI mot APK dist\rs10-vi\VietHoa-<goi>.apk, tu rs10-apps-chuoi.tsv va h6-apps-chuoi.tsv (chuoi tieng Trung cua firmware)
# va cac bang dich chu Trung -> tieng Viet (dich.tsv, dich-cau-dai.tsv, dohko-apps-dich*.tsv, rs10-apps-dich.tsv).
# Android 9 khong co `cmd overlay fabricate`: day la overlay thuong, cai bang adb install va bat bang `cmd overlay
# enable` (rs10-install.ps1), khong can root. Ban dich nam o values/ va values-zh-rCN (may de tieng Trung).
# -Device ironman: cung cach cho cac may XGIMI dung firmware IRONMAN (XGIMI_IRONMAN_XJ03D, Android 8.0), tu
# ironman-apps-chuoi.tsv, ra dist\ironman-vi.
# Chay: powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\rs10-overlays.ps1 [-Device rs10|ironman]
param([ValidateSet('rs10', 'ironman')][string]$Device = 'rs10')
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent (Split-Path -Parent $here)
$bt = 'E:\buildtools\android-sdk\build-tools\34.0.0'
$androidJar = 'E:\buildtools\android-sdk\platforms\android-34\android.jar'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'E:\buildtools\jdk\jdk-17.0.20.1+1' }   # apksigner
$dev = @{
    rs10    = @{ Strings = 'rs10-apps-chuoi.tsv', 'h6-apps-chuoi.tsv'; Sdk = 28 }
    ironman = @{ Strings = 'ironman-apps-chuoi.tsv'; Sdk = 26 }
}[$Device]
$outDir = Join-Path $root "dist\$Device-vi"

# Khong dich: ban phim Sogou (dung Gboard), menu nha may, giong doc, thu thap du lieu, quang cao, dich vu ben thu ba
$skip = 'com.sohu.inputmethod.sogou.tv', 'com.xgimi.minitvfactory', 'mediatek.factorymenu.ui', 'com.xgimi.duertts',
    'com.xgimi.datareporter', 'com.xgimi.adservice', 'com.youku.bluray.tv', 'com.jd.smart.tv.sdk',
    'com.mediatek.speakerservice', 'com.xgimi.bugreportsender'

function Escape([string]$s) {
    # aapt string rules: XML entities, then quotes and apostrophes escaped; \n stays a newline escape
    $s = $s.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;')
    $s = $s.Replace('"', '\"').Replace("'", "\'")
    if ($s.StartsWith('@') -or $s.StartsWith('?')) { $s = '\' + $s }
    $s
}

$dict = @{}
foreach ($f in 'dich.tsv', 'dich-cau-dai.tsv', 'dohko-apps-dich.tsv', 'dohko-apps-dich-them.tsv', 'rs10-apps-dich.tsv', 'ironman-apps-dich.tsv') {
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $here $f), [Text.Encoding]::UTF8)) {
        if (-not $line -or $line.StartsWith('#')) { continue }
        $p = $line.Split("`t")
        if ($p.Count -ge 2 -and $p[1].Trim()) { $dict[($p[0] -replace '\s+', ' ').Trim()] = $p[1].Trim() }
    }
}

# goi -> danh sach (ten chuoi, tieng Viet)
$byPkg = [ordered]@{}
foreach ($line in ($dev.Strings | ForEach-Object { [IO.File]::ReadAllLines((Join-Path $here $_), [Text.Encoding]::UTF8) })) {
    if (-not $line -or $line.StartsWith('#')) { continue }
    $p = $line.Split("`t")
    if ($p.Count -lt 3 -or $skip -contains $p[0]) { continue }
    $vi = $dict[($p[2] -replace '\s+', ' ').Trim()]
    if (-not $vi) { continue }
    if (-not $byPkg.Contains($p[0])) { $byPkg[$p[0]] = New-Object System.Collections.Generic.List[object] }
    $byPkg[$p[0]].Add(@($p[1], $vi))
}

if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
New-Item -ItemType Directory -Force $outDir | Out-Null
$utf8 = New-Object Text.UTF8Encoding $false
$total = 0
foreach ($target in $byPkg.Keys) {
    $short = ($target -split '\.')[-1]
    $package = "vn.cleantv.overlay.$Device.$short"
    $work = Join-Path $env:TEMP "$Device-overlay-$short"
    if (Test-Path $work) { Remove-Item $work -Recurse -Force }
    $sb = New-Object System.Text.StringBuilder
    $seen = @{}
    foreach ($e in $byPkg[$target]) {
        if ($seen.ContainsKey($e[0])) { continue }
        $seen[$e[0]] = $true
        [void]$sb.AppendLine("    <string name=`"$($e[0])`" formatted=`"false`">$(Escape $e[1])</string>")
    }
    $xml = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<resources>`n$sb</resources>`n"
    foreach ($v in 'values', 'values-zh-rCN') {
        New-Item -ItemType Directory -Force "$work\res\$v" | Out-Null
        [IO.File]::WriteAllText("$work\res\$v\strings.xml", $xml, $utf8)
    }
    $manifest = @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$package"
    android:versionCode="1"
    android:versionName="1.0">
    <uses-sdk android:minSdkVersion="$($dev.Sdk)" android:targetSdkVersion="$($dev.Sdk)" />
    <overlay android:targetPackage="$target" android:priority="1" />
    <application android:hasCode="false" android:label="VietHoa $short" />
</manifest>
"@
    [IO.File]::WriteAllText("$work\AndroidManifest.xml", $manifest, $utf8)
    & "$bt\aapt.exe" package -f -M "$work\AndroidManifest.xml" -S "$work\res" -I $androidJar -F "$work\unsigned.apk"
    if ($LASTEXITCODE -ne 0) { throw "aapt package that bai ($target)" }
    $out = Join-Path $outDir "VietHoa-$target.apk"
    & "$bt\zipalign.exe" -f 4 "$work\unsigned.apk" "$work\aligned.apk"
    & "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android `
        --min-sdk-version $($dev.Sdk) --v4-signing-enabled false --out $out "$work\aligned.apk"
    if ($LASTEXITCODE -ne 0) { throw "ky APK that bai ($target)" }
    Remove-Item $work -Recurse -Force
    $total += $seen.Count
    Write-Host ("{0,-32} {1,5} chuoi" -f $target, $seen.Count)
}
Write-Host ("Da tao {0} file dich trong {1} ({2} chuoi)" -f $byPkg.Count, $outDir, $total)
