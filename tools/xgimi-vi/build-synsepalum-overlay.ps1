# Dung cac file dich (overlay tinh) tieng Viet cho may XGIMI Android 6 (synsepalum_YN):
#   dist\XgimiSettingsVi-overlay.apk  Cai dat XGIMI (com.android.newsettings), tu synsepalum-settings-vi.tsv
#   dist\XgimiQuickVi-overlay.apk     thanh cai dat nhanh (com.xgimi.misckey), tu synsepalum-quick-vi.tsv
#   dist\XgimiPowerVi-overlay.apk     menu tat may, cua so he thong (com.xgimi.windowsystem), tu synsepalum-power-vi.tsv
# Ban dich nam o values/ (mac dinh), thay chu tieng Trung o moi ngon ngu (chuoi da co ban vi cua ung dung van
# dung ban vi do). Android 6 nap overlay tinh tu /vendor/overlay khi khoi dong; cai bang install-synsepalum-overlay.ps1.
# Chay: powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\build-synsepalum-overlay.ps1
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent (Split-Path -Parent $here)
$bt = 'E:\buildtools\android-sdk\build-tools\34.0.0'
$androidJar = 'E:\buildtools\android-sdk\platforms\android-34\android.jar'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'E:\buildtools\jdk\jdk-17.0.20.1+1' }   # apksigner

$overlays = @(
    @{ Tsv = 'synsepalum-settings-vi.tsv'; Target = 'com.android.newsettings'
       Package = 'vn.cleantv.overlay.xgimisettings'; Out = 'XgimiSettingsVi-overlay.apk' },
    @{ Tsv = 'synsepalum-quick-vi.tsv'; Target = 'com.xgimi.misckey'
       Package = 'vn.cleantv.overlay.xgimiquick'; Out = 'XgimiQuickVi-overlay.apk' },
    @{ Tsv = 'synsepalum-power-vi.tsv'; Target = 'com.xgimi.windowsystem'
       Package = 'vn.cleantv.overlay.xgimipower'; Out = 'XgimiPowerVi-overlay.apk' }
)

function Escape([string]$s) {
    # aapt string rules: XML entities, then quotes and apostrophes escaped; \n stays a newline escape
    $s = $s.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;')
    $s = $s.Replace('"', '\"').Replace("'", "\'")
    if ($s.StartsWith('@') -or $s.StartsWith('?')) { $s = '\' + $s }
    $s
}

function Build-Overlay($o) {
    $work = Join-Path $env:TEMP "synsepalum-overlay-$($o.Package)"
    if (Test-Path $work) { Remove-Item $work -Recurse -Force }
    New-Item -ItemType Directory -Force "$work\res\values" | Out-Null
    $strings = New-Object System.Text.StringBuilder
    $arrays = New-Object System.Text.StringBuilder
    $n = 0
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $here $o.Tsv), [Text.Encoding]::UTF8)) {
        if (-not $line -or $line.StartsWith('#')) { continue }
        $p = $line.Split("`t")
        if ($p.Count -lt 3) { continue }
        if ($p[0] -eq 's') {
            [void]$strings.AppendLine("    <string name=`"$($p[1])`" formatted=`"false`">$(Escape $p[2])</string>")
        } elseif ($p[0] -eq 'a') {
            [void]$arrays.AppendLine("    <string-array name=`"$($p[1])`">")
            foreach ($item in ($p[2] -split ' \| ')) { [void]$arrays.AppendLine("        <item>$(Escape $item)</item>") }
            [void]$arrays.AppendLine('    </string-array>')
        }
        $n++
    }
    $utf8 = New-Object Text.UTF8Encoding $false
    [IO.File]::WriteAllText("$work\res\values\strings.xml", "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<resources>`n$strings</resources>`n", $utf8)
    [IO.File]::WriteAllText("$work\res\values\arrays.xml", "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<resources>`n$arrays</resources>`n", $utf8)
    $manifest = @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$($o.Package)"
    android:versionCode="1"
    android:versionName="1.0">
    <overlay android:targetPackage="$($o.Target)" android:priority="1" />
    <application android:hasCode="false" />
</manifest>
"@
    [IO.File]::WriteAllText("$work\AndroidManifest.xml", $manifest, $utf8)
    & "$bt\aapt.exe" package -f -M "$work\AndroidManifest.xml" -S "$work\res" -I $androidJar -F "$work\unsigned.apk"
    if ($LASTEXITCODE -ne 0) { throw "aapt package that bai ($($o.Out))" }
    $out = Join-Path $root "dist\$($o.Out)"
    New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
    & "$bt\zipalign.exe" -f 4 "$work\unsigned.apk" "$work\aligned.apk"
    & "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android `
        --min-sdk-version 23 --out $out "$work\aligned.apk"
    if ($LASTEXITCODE -ne 0) { throw "ky APK that bai ($($o.Out))" }
    Remove-Item $work -Recurse -Force
    Write-Host ("Da tao {0} ({1} muc dich, {2:N0} KB)" -f $out, $n, ((Get-Item $out).Length / 1KB))
}

foreach ($o in $overlays) { Build-Overlay $o }
