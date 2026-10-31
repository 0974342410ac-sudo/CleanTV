# Builds a USB firmware for XGIMI m7642 (Android 9) in Vietnamese: the overlays inside as static overlays in
# /vendor/overlay (retail units refuse them over ADB, no platform key, see examples\xgimi-rs10\debloat.txt), and
# Vietnamese / Vietnam time as the defaults in build.prop, so text with no Vietnamese falls back to English, not Chinese.
# The input bin is only read. The output keeps its file name, since mboot checks the name of the bin on the USB.
# NOT FLASH-TESTED YET. Flashing can leave a unit unbootable and it erases user data: read HUONG-DAN-FIRMWARE-VI.txt.
# Run: powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\build-firmware-vi.ps1 -Bin "E:\xgimi h6\XGIMI_PIGCHIVALROUS_XM03D.bin"
param(
    [Parameter(Mandatory = $true)][string]$Bin,
    [string]$Work = 'E:\cleantv-firmware-work',
    [string]$OutDir
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent (Split-Path -Parent $here)
if (-not $OutDir) { $OutDir = Join-Path $root 'dist\firmware-vi' }
$java = 'E:\buildtools\jdk\jdk-17.0.20.1+1\bin\java.exe'
$tool = Join-Path $here 'FirmwareVi.java'
$cyg = 'E:\buildtools\cygwin-e2fs\root'
$env:PATH = "$cyg\bin;$cyg\usr\bin;$cyg\usr\sbin;$env:PATH"
$overlays = Join-Path $root 'dist\rs10-vi-static'
$label = 'u:object_r:vendor_overlay_file:s0'
$ascii = New-Object Text.ASCIIEncoding

function Run-Tool { & $java $tool @args; if ($LASTEXITCODE -ne 0) { throw "FirmwareVi $($args[0]) failed" } }

# Through cmd: PowerShell 5.1 turns a tool's stderr into a stopping error here
function Debugfs([string]$image, [string]$commands) {
    [IO.File]::WriteAllText("$Work\debugfs.txt", $commands.Replace("`r`n", "`n"), $ascii)
    cmd /c "debugfs -w -f debugfs.txt $image 2>&1" | Where-Object { "$_" -match 'rror|not found|No space|Couldn|already exists' } | ForEach-Object { throw "debugfs: $_" }
}

function Check-Fs([string]$image) {
    $fsck = cmd /c "e2fsck -fn $image 2>&1"
    $code = $LASTEXITCODE
    $fsck | Select-Object -Last 1 | ForEach-Object { Write-Host "$_" }
    if ($code -ne 0) { throw "e2fsck found problems in $image ($code)" }
}

# Replaces lines of a build.prop inside an image, adding the ones it lacks; keeps the file's mode and SELinux label
function Set-Props([string]$image, [string]$dir, [hashtable]$props) {
    $path = "$($dir.TrimEnd('/'))/build.prop"
    cmd /c "debugfs -R `"dump $path prop.old`" $image 2>nul" | Out-Null
    if (-not (Test-Path prop.old)) { throw "no $path" }
    $stat = cmd /c "debugfs -R `"stat $path`" $image 2>nul"
    $mode = ([regex]::Match(($stat -join ' '), 'Mode:\s+(\d+)')).Groups[1].Value
    $selinux = ([regex]::Match(($stat -join ' '), 'security\.selinux \(\d+\) = "([^"\\]+)')).Groups[1].Value
    if (-not $mode -or -not $selinux) { throw "no mode or label for $path" }
    $left = @{} + $props
    $lines = foreach ($l in [IO.File]::ReadAllLines("$Work\prop.old", $ascii)) {
        $key = ($l -split '=', 2)[0]
        if ($left.ContainsKey($key)) { "$key=$($left[$key])"; $left.Remove($key) }
        elseif ($props.ContainsKey($key)) { continue }   # a repeat of the same key
        else { $l }
    }
    $lines = @($lines) + @($left.GetEnumerator() | Sort-Object Key | ForEach-Object { "$($_.Key)=$($_.Value)" })
    [IO.File]::WriteAllText("$Work\prop.new", (($lines -join "`n") + "`n"), $ascii)
    Debugfs $image "cd $dir`nrm build.prop`nwrite prop.new build.prop`nsif build.prop mode 010$mode`nsif build.prop uid 0`nsif build.prop gid 0`nea_set build.prop security.selinux $selinux`n"
    Remove-Item prop.old, prop.new
    foreach ($k in ($props.Keys | Sort-Object)) { Write-Host "  $path $k=$($props[$k])" }
}

if (-not (Test-Path -LiteralPath $Bin)) { throw "No file $Bin" }
Write-Host '== Overlays'
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'rs10-overlays.ps1') -Device rs10 -Static | Select-Object -Last 1
if ($LASTEXITCODE -ne 0) { throw 'overlay build failed' }

New-Item -ItemType Directory -Force $Work, $OutDir | Out-Null
Set-Location $Work
Write-Host '== Original firmware'
Run-Tool footer $Bin
Run-Tool unpack $Bin $Work system vendor
Copy-Item system.img system-new.img -Force
Copy-Item vendor.img vendor-new.img -Force

Write-Host '== Language row in the Settings menu'
# Settings builds its pages from res/raw/settings_<product>.json by ro.xgimi.product.name. Most products (Product_xiaobai
# here) leave out the language row, so an overlay adds it, on top of VietHoa-com.android.newsettings (higher priority).
$bt = 'E:\buildtools\android-sdk\build-tools\34.0.0'
$androidJar = 'E:\buildtools\android-sdk\platforms\android-34\android.jar'
$jdk = 'E:\buildtools\jdk\jdk-17.0.20.1+1\bin'
$env:JAVA_HOME = 'E:\buildtools\jdk\jdk-17.0.20.1+1'
$langSrc = Join-Path $here 'm7642-language'
$utf8 = New-Object Text.UTF8Encoding $false
Remove-Item lang -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory lang, lang\ov, lang\ov\res, lang\ov\res\raw, lang\ov\res\values, lang\app | Out-Null
cmd /c 'debugfs -R "dump /system/priv-app/release_838_newsettings/release_838_newsettings.apk lang\ns.apk" system.img 2>nul'
$map = @{}
$cur = ''
foreach ($x in (& "$bt\aapt2.exe" dump resources lang\ns.apk 2>$null)) {
    if ($x -match '^\s*resource 0x[0-9a-f]+ raw/(settings_product_\S+)') { $cur = $Matches[1] }
    elseif ($x -match '^\s*resource ') { $cur = '' }
    elseif ($cur -and $x -match '\(file\)\s+(\S+)') { $map[$cur] = $Matches[1] }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead("$Work\lang\ns.apk")
$added = 0
foreach ($name in ($map.Keys | Sort-Object)) {
    $r = New-Object IO.StreamReader($zip.GetEntry($map[$name]).Open(), [Text.Encoding]::UTF8)
    $t = $r.ReadToEnd()
    $r.Close()
    if ($t -match '"language"\s*[,\]]') { continue }
    $sys = [regex]::Match($t, '"system"\s*:\s*\[([^\]]*)\]')
    if (-not $sys.Success) { Write-Host "  no system page in $name"; continue }
    # Before the legal rows when there are any, as the products with the row have it
    $list = $sys.Groups[1].Value
    if ($list -match '"law_info_title"') { $new = $list -replace '"law_info_title"', '"language", "setting_line", "law_info_title"' }
    elseif ($list.Trim()) { $new = $list.TrimEnd() + ', "setting_line", "language"' }
    else { $new = '"language"' }
    $t = $t.Substring(0, $sys.Groups[1].Index) + $new + $t.Substring($sys.Groups[1].Index + $list.Length)
    # The row opens the page named "language"
    $t = [regex]::Replace($t, '^\s*\[', { param($m) '[ { "language": ["language_back", "language_chinese", "language_english"] },' }, 'None', [TimeSpan]::FromSeconds(5))
    $check = $t | ConvertFrom-Json
    if (-not (@($check | Where-Object { $_.language }).Count -eq 1)) { throw "language page not added in $name" }
    if (-not (@(($check | Where-Object { $_.system }).system) -contains 'language')) { throw "language row not added in $name" }
    [IO.File]::WriteAllText("$Work\lang\ov\res\raw\$name.json", $t, $utf8)
    $added++
}
$zip.Dispose()
Remove-Item lang\ns.apk
if (-not (Test-Path 'lang\ov\res\raw\settings_product_xiaobai.json')) { throw 'no language row for this firmware''s product' }
Write-Host "  language row added for $added products"
foreach ($v in 'values', 'values-vi', 'values-vi-rVN') {
    New-Item -ItemType Directory -Force "$Work\lang\ov\res\$v" | Out-Null
    [IO.File]::WriteAllText("$Work\lang\ov\res\$v\strings.xml", "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<resources>`n    <string name=`"language_text`">Ng&#244;n ng&#7919;</string>`n    <string name=`"language_chinese_text`">Ti&#7871;ng Vi&#7879;t</string>`n</resources>`n", $utf8)
}
[IO.File]::WriteAllText("$Work\lang\ov\AndroidManifest.xml", "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<manifest xmlns:android=`"http://schemas.android.com/apk/res/android`" package=`"vn.cleantv.overlay.ngonngu`" android:versionCode=`"1`" android:versionName=`"1.0`">`n    <overlay android:targetPackage=`"com.android.newsettings`" android:priority=`"2`" android:isStatic=`"true`" />`n    <application android:hasCode=`"false`" />`n</manifest>`n", $utf8)
& "$bt\aapt.exe" package -f -M lang\ov\AndroidManifest.xml -S lang\ov\res -I $androidJar -F lang\ov.apk
if ($LASTEXITCODE -ne 0) { throw 'aapt package failed (language overlay)' }
& "$bt\zipalign.exe" -f 4 lang\ov.apk lang\ov2.apk
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --min-sdk-version 26 --out lang\VietHoaNgonNgu-com.android.newsettings.apk lang\ov2.apk
if ($LASTEXITCODE -ne 0) { throw 'signing the language overlay failed' }

Write-Host '== Chinese to Vietnamese switch'
& "$jdk\javac.exe" -encoding UTF-8 -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath $androidJar -d lang\app\classes (Join-Path $langSrc 'Doi.java')
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$classes = @(Get-ChildItem lang\app\classes -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& "$bt\d8.bat" --min-api 26 --lib $androidJar --output lang\app @classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& "$bt\aapt.exe" package -f -M (Join-Path $langSrc 'AndroidManifest.xml') -I $androidJar -F lang\app\unsigned.apk
if ($LASTEXITCODE -ne 0) { throw 'aapt package failed (language switch)' }
Push-Location lang\app
& "$bt\aapt.exe" add unsigned.apk classes.dex | Out-Null
Pop-Location
& "$bt\zipalign.exe" -f 4 lang\app\unsigned.apk lang\app\aligned.apk
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --min-sdk-version 26 --out lang\XgimiLanguage.apk lang\app\aligned.apk
if ($LASTEXITCODE -ne 0) { throw 'signing the language switch failed' }

Write-Host '== Adding overlays to vendor'
$apks = @(Get-ChildItem $overlays -Filter *.apk) + @(Get-Item 'lang\VietHoaNgonNgu-com.android.newsettings.apk')
Remove-Item overlays-work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory overlays-work | Out-Null
$cmd = New-Object System.Text.StringBuilder
[void]$cmd.AppendLine('mkdir /overlay')
[void]$cmd.AppendLine('sif /overlay mode 040755')
[void]$cmd.AppendLine('sif /overlay uid 0')
[void]$cmd.AppendLine('sif /overlay gid 0')
[void]$cmd.AppendLine("ea_set /overlay security.selinux $label")
# debugfs write takes a plain name in the current directory, not a path
[void]$cmd.AppendLine('cd /overlay')
foreach ($a in $apks) {
    # debugfs reads paths relative to the work folder; no spaces there
    Copy-Item $a.FullName "overlays-work\$($a.Name)"
    $to = $a.Name
    [void]$cmd.AppendLine("write overlays-work/$($a.Name) $to")
    [void]$cmd.AppendLine("sif $to mode 0100644")
    [void]$cmd.AppendLine("sif $to uid 0")
    [void]$cmd.AppendLine("sif $to gid 0")
    [void]$cmd.AppendLine("ea_set $to security.selinux $label")
}
Debugfs 'vendor-new.img' $cmd.ToString()
Write-Host "  $($apks.Count) overlays"

Write-Host '== Vietnamese and Vietnam time as the defaults'
Set-Props 'vendor-new.img' '/' @{
    'persist.sys.locale' = 'vi-VN'; 'persist.sys.language' = 'vi'; 'persist.sys.country' = 'VN'
    'persist.sys.timezone' = 'Asia/Ho_Chi_Minh'; 'ro.product.locale' = 'vi-VN'
}
Set-Props 'system-new.img' '/system' @{ 'ro.product.locale' = 'vi-VN' }

Write-Host '== Adding the language switch to system'
$sysLabel = ([regex]::Match(((cmd /c 'debugfs -R "stat /system/priv-app/release_838_newsettings/release_838_newsettings.apk" system-new.img 2>nul') -join ' '), 'security\.selinux \(\d+\) = "([^"\\]+)')).Groups[1].Value
$etcLabel = ([regex]::Match(((cmd /c 'debugfs -R "stat /system/etc/permissions/platform.xml" system-new.img 2>nul') -join ' '), 'security\.selinux \(\d+\) = "([^"\\]+)')).Groups[1].Value
if (-not $sysLabel -or -not $etcLabel) { throw 'no SELinux label for the language switch' }
Copy-Item (Join-Path $langSrc 'privapp-permissions-vn.cleantv.language.xml') 'lang\privapp.xml'
$c = New-Object System.Text.StringBuilder
foreach ($line in @('mkdir /system/priv-app/XgimiLanguage', 'sif /system/priv-app/XgimiLanguage mode 040755', 'sif /system/priv-app/XgimiLanguage uid 0', 'sif /system/priv-app/XgimiLanguage gid 0',
        "ea_set /system/priv-app/XgimiLanguage security.selinux $sysLabel", 'cd /system/priv-app/XgimiLanguage', 'write lang/XgimiLanguage.apk XgimiLanguage.apk',
        'sif XgimiLanguage.apk mode 0100644', 'sif XgimiLanguage.apk uid 0', 'sif XgimiLanguage.apk gid 0', "ea_set XgimiLanguage.apk security.selinux $sysLabel",
        'cd /system/etc/permissions', 'write lang/privapp.xml privapp-permissions-vn.cleantv.language.xml', 'sif privapp-permissions-vn.cleantv.language.xml mode 0100644',
        'sif privapp-permissions-vn.cleantv.language.xml uid 0', 'sif privapp-permissions-vn.cleantv.language.xml gid 0', "ea_set privapp-permissions-vn.cleantv.language.xml security.selinux $etcLabel")) { [void]$c.AppendLine($line) }
Debugfs 'system-new.img' $c.ToString()

Write-Host '== Checking the changed file systems (read only)'
Check-Fs 'vendor-new.img'
Check-Fs 'system-new.img'

Write-Host '== Packing'
Run-Tool delta system.img system-new.img system-delta.simg
Run-Tool delta vendor.img vendor-new.img vendor-delta.simg
$out = Join-Path $OutDir (Split-Path -Leaf $Bin)
Run-Tool pack $Bin system-delta.simg system step1.bin
Run-Tool pack step1.bin vendor-delta.simg vendor $out
Remove-Item step1.bin

Write-Host '== Checking the new bin'
Run-Tool footer $out
Run-Tool compare $Bin $out
New-Item -ItemType Directory -Force check | Out-Null
Run-Tool unpack $out "$Work\check" system vendor
foreach ($n in 'system', 'vendor') {
    if ((Get-FileHash "check\$n.img").Hash -ne (Get-FileHash "$n-new.img").Hash) { throw "$n from the new bin differs from the built image" }
    Write-Host "$n rebuilt from the new bin matches the built image"
}
Remove-Item check, lang -Recurse -Force
Copy-Item (Join-Path $here 'HUONG-DAN-FIRMWARE-VI.txt') $OutDir -Force
Write-Host "== Done: $out"
