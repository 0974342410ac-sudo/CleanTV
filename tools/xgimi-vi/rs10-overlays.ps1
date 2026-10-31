# Dung cac file dich (overlay) tieng Viet cho may chieu XGIMI RS10 va H6 (Android 9, m7642, firmware XGIMI_PIGCHIVALROUS_*):
# moi ung dung XGIMI mot APK dist\rs10-vi\VietHoa-<goi>.apk, tu rs10-apps-chuoi.tsv va h6-apps-chuoi.tsv (chuoi tieng Trung cua firmware)
# va cac bang dich chu Trung -> tieng Viet (dich.tsv, dich-cau-dai.tsv, dohko-apps-dich*.tsv, rs10-apps-dich.tsv).
# Android 9 khong co `cmd overlay fabricate`: day la overlay thuong, cai bang adb install va bat bang `cmd overlay
# enable` (rs10-install.ps1), khong can root. Ban dich nam o values/ va values-zh-rCN (may de tieng Trung).
# -Device ironman: cung cach cho cac may XGIMI dung firmware IRONMAN (XGIMI_IRONMAN_XJ03D, Android 8.0), tu
# ironman-apps-chuoi.tsv, ra dist\ironman-vi.
# -Static: overlay tinh (android:isStatic) de dua vao firmware /vendor/overlay, ra dist\<may>-vi-static (build-firmware-vi.ps1).
# Chuoi co %s/%d khac voi ban goc bi bo qua, de nguyen tieng Trung: sai dinh dang lam ung dung hong khi chay.
# Chay: powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\rs10-overlays.ps1 [-Device rs10|ironman] [-Static]
param([ValidateSet('rs10', 'ironman')][string]$Device = 'rs10', [switch]$Static)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent (Split-Path -Parent $here)
$bt = 'E:\buildtools\android-sdk\build-tools\34.0.0'
$androidJar = 'E:\buildtools\android-sdk\platforms\android-34\android.jar'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'E:\buildtools\jdk\jdk-17.0.20.1+1' }   # apksigner
$dev = @{
    rs10    = @{ Strings = 'rs10-apps-chuoi.tsv', 'h6-apps-chuoi.tsv', 'plus-apps-chuoi.tsv', 'z7-apps-chuoi.tsv', 'h6-full-chuoi.tsv'; Arrays = 'h6-full-mang.tsv'; Sdk = 28 }
    ironman = @{ Strings = 'ironman-apps-chuoi.tsv'; Sdk = 26 }
}[$Device]
# In a firmware (-Static, the H6/M1 bin) only that firmware's own text counts: the first table with a name decides its
# translation, and the same name in another firmware can mean something else or take another format (%ds and %ss)
if ($Static) { $dev.Strings = 'h6-apps-chuoi.tsv', 'h6-full-chuoi.tsv' }
$outDir =Join-Path $root "dist\$Device-vi$(if ($Static) { '-static' })"

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

# Cac dau dinh dang trong mot chuoi (%s, %1$d, %.2f, %%), sap xep de so sanh ban goc voi ban dich
function FormatTokens([string]$s) {
    # No space or comma among the flags: "10% pin" in Vietnamese is a plain percent sign, not a format
    (([regex]::Matches($s, '%(\d+\$)?[-#+0(]*\d*(\.\d+)?[a-zA-Z%]') | ForEach-Object { $_.Value }) | Sort-Object) -join ' '
}

$dict = @{}
foreach ($f in 'dich.tsv', 'dich-cau-dai.tsv', 'dohko-apps-dich.tsv', 'dohko-apps-dich-them.tsv', 'rs10-apps-dich.tsv', 'ironman-apps-dich.tsv', 'h6-full-dich.tsv') {
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
    if ((FormatTokens $p[2]) -ne (FormatTokens $vi)) {
        Write-Host "Bo qua (sai dinh dang) $($p[0]) $($p[1]): [$(FormatTokens $p[2])] / [$(FormatTokens $vi)]"
        continue
    }
    if (-not $byPkg.Contains($p[0])) { $byPkg[$p[0]] = New-Object System.Collections.Generic.List[object] }
    $byPkg[$p[0]].Add(@($p[1], $vi))
}

# Mang va so nhieu: giu du so muc va thu tu; muc chua co ban dich, hoac sai %s/%d, de nguyen chu goc
function ItemText([string]$cn) {
    $raw = $cn.Replace('\"', '"')
    if ($raw -notmatch '\p{IsCJKUnifiedIdeographs}') { return $raw }
    $vi = $dict[($raw -replace '\s+', ' ').Trim()]
    if ($vi -and (FormatTokens $raw) -eq (FormatTokens $vi)) { return Positional $vi }
    Positional $raw
}

# aapt refuses two plain %d in one plural; %1$d %2$d in the same order formats the same
function Positional([string]$s) {
    $plain = '%(?![\d]+\$|%)[-#+0(]*\d*(\.\d+)?[a-zA-Z]'
    if ([regex]::Matches($s, $plain).Count -lt 2) { return $s }
    $script:argNo = 0
    [regex]::Replace($s, $plain, { param($m) $script:argNo++; '%' + $script:argNo + '$' + $m.Value.Substring(1) })
}
$arrayXml = @{}
$arrayCount = 0
foreach ($file in @($dev.Arrays | Where-Object { $_ })) {
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $here $file), [Text.Encoding]::UTF8)) {
        if (-not $line -or $line.StartsWith('#')) { continue }
        $p = $line.Split("`t")
        if ($p.Count -lt 4 -or $skip -contains $p[0]) { continue }
        $sb = New-Object System.Text.StringBuilder
        if ($p[1] -eq 'plurals') {
            [void]$sb.AppendLine("    <plurals name=`"$($p[2])`">")
            foreach ($m in [regex]::Matches($p[3], '(\w+)="((?:[^"\\]|\\.)*)"')) {
                [void]$sb.AppendLine("        <item quantity=`"$($m.Groups[1].Value)`" formatted=`"false`">$(Escape (ItemText $m.Groups[2].Value))</item>")
            }
            [void]$sb.AppendLine('    </plurals>')
        } else {
            [void]$sb.AppendLine("    <string-array name=`"$($p[2])`">")
            foreach ($m in [regex]::Matches($p[3], '"((?:[^"\\]|\\.)*)"')) {
                [void]$sb.AppendLine("        <item formatted=`"false`">$(Escape (ItemText $m.Groups[1].Value))</item>")
            }
            [void]$sb.AppendLine('    </string-array>')
        }
        if (-not $arrayXml.ContainsKey($p[0])) { $arrayXml[$p[0]] = New-Object System.Text.StringBuilder }
        [void]$arrayXml[$p[0]].Append($sb.ToString())
        $arrayCount++
        if (-not $byPkg.Contains($p[0])) { $byPkg[$p[0]] = New-Object System.Collections.Generic.List[object] }
    }
}

# Factory menu, firmware only: translated by name, since its text is not in the tables above
if ($Static) {
    $factory = 'com.xgimi.minitvfactory'
    if (-not $byPkg.Contains($factory)) { $byPkg[$factory] = New-Object System.Collections.Generic.List[object] }
    $sb = New-Object System.Text.StringBuilder
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $here 'h6-factory-vi.tsv'), [Text.Encoding]::UTF8)) {
        if (-not $line -or $line.StartsWith('#')) { continue }
        $p = $line.Split("`t")
        if ($p[0] -eq 's') { $byPkg[$factory].Add(@($p[1], $p[2])) }
        elseif ($p[0] -eq 'a') {
            [void]$sb.AppendLine("    <string-array name=`"$($p[1])`">")
            foreach ($item in ($p[2] -split ' \| ')) { [void]$sb.AppendLine("        <item>$(Escape $item)</item>") }
            [void]$sb.AppendLine('    </string-array>')
            $arrayCount++
        }
    }
    $arrayXml[$factory] = $sb
}

if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
New-Item -ItemType Directory -Force $outDir | Out-Null
$utf8 = New-Object Text.UTF8Encoding $false
$total = 0
$shortNames = @{}
foreach ($target in $byPkg.Keys) {
    $short = ($target -split '\.')[-1]
    # Hai goi trung ten cuoi (providers.settings, ...): dung ten day du de ten overlay khong trung
    if ($shortNames.ContainsKey($short)) { $short = $target.Replace('.', '_') }
    $shortNames[$short] = $true
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
    if ($arrayXml.ContainsKey($target)) { [void]$sb.Append($arrayXml[$target].ToString()) }
    $xml = "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<resources>`n$sb</resources>`n"
    # values-vi too: an app's own, partial Vietnamese would otherwise win over the default here when Vietnamese is chosen
    foreach ($v in 'values', 'values-zh-rCN', 'values-vi', 'values-vi-rVN') {
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
    <overlay android:targetPackage="$target" android:priority="1"$(if ($Static) { ' android:isStatic="true"' }) />
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
Write-Host ("Da tao {0} file dich trong {1} ({2} chuoi, {3} mang)" -f $byPkg.Count, $outDir, $total, $arrayCount)
