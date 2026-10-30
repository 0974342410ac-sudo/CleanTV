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

Write-Host '== Adding overlays to vendor'
$apks = Get-ChildItem $overlays -Filter *.apk
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
Remove-Item check -Recurse -Force
Copy-Item (Join-Path $here 'HUONG-DAN-FIRMWARE-VI.txt') $OutDir -Force
Write-Host "== Done: $out"
