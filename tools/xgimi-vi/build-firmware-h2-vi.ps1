# Builds a USB firmware for the XGIMI H2 (msd838, Android 6, synsepalum_YN) in Vietnamese with the CleanTV launcher,
# from XGIMI's own V1.3.15 Global bin: the three Settings/quick bar/power menu overlays in /system/vendor/overlay, the
# launcher as a privileged system app (so it can enable its Home key service by itself), and Vietnamese / Vietnam time
# as the defaults. Only the system partition changes; mboot and everything else stay byte for byte.
# The input bin is only read. The output keeps its file name, since mboot checks the name of the bin on the USB.
# NOT FLASH-TESTED YET. Flashing can leave a unit unbootable and it erases user data: read HUONG-DAN-FIRMWARE-H2.txt.
# Run: powershell -ExecutionPolicy Bypass -File tools\xgimi-vi\build-firmware-h2-vi.ps1
param(
    [string]$Bin = 'D:\ROOM xgimi h2\room qt\GIMI_XHAD01_XHC05_MAXIM_YN_WMP.bin',
    [string]$Work = 'E:\cleantv-firmware-work\h2',
    [string]$OutDir
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent (Split-Path -Parent $here)
if (-not $OutDir) { $OutDir = Join-Path $root 'dist\firmware-vi\xgimi h2' }
$java = 'E:\buildtools\jdk\jdk-17.0.20.1+1\bin\java.exe'
$env:JAVA_HOME = 'E:\buildtools\jdk\jdk-17.0.20.1+1'
$cyg = 'E:\buildtools\cygwin-e2fs\root'
$env:PATH = "$cyg\bin;$cyg\usr\bin;$cyg\usr\sbin;$env:PATH"
$label = 'u:object_r:system_file:s0'
$launcher = Join-Path $root 'launcher\updater\src\main\assets\tvhome.apk'
$ascii = New-Object Text.ASCIIEncoding

function Run-Lzo { & $java -Xmx1500m (Join-Path $here 'FirmwareLzo.java') @args; if ($LASTEXITCODE -ne 0) { throw "FirmwareLzo $($args[0]) failed" } }
function Run-Vi { & $java (Join-Path $here 'FirmwareVi.java') @args; if ($LASTEXITCODE -ne 0) { throw "FirmwareVi $($args[0]) failed" } }

# Through cmd: PowerShell 5.1 turns a tool's stderr into a stopping error here
function Debugfs([string]$image, [string]$commands) {
    [IO.File]::WriteAllText("$Work\debugfs.txt", $commands.Replace("`r`n", "`n"), $ascii)
    cmd /c "debugfs -w -f debugfs.txt $image 2>&1" | Where-Object { "$_" -match 'rror|not found|No space|Couldn|already exists' } | ForEach-Object { throw "debugfs: $_" }
}

function Add-File([System.Text.StringBuilder]$c, [string]$source, [string]$name, [string]$gid = '0') {
    [void]$c.AppendLine("write $source $name")
    [void]$c.AppendLine("sif $name mode 0100644")
    [void]$c.AppendLine("sif $name uid 0")
    [void]$c.AppendLine("sif $name gid $gid")
    [void]$c.AppendLine("ea_set $name security.selinux $label")
}

function Add-Dir([System.Text.StringBuilder]$c, [string]$path, [string]$gid = '0') {
    [void]$c.AppendLine("mkdir $path")
    [void]$c.AppendLine("sif $path mode 040755")
    [void]$c.AppendLine("sif $path uid 0")
    [void]$c.AppendLine("sif $path gid $gid")
    [void]$c.AppendLine("ea_set $path security.selinux $label")
}

if (-not (Test-Path -LiteralPath $Bin)) { throw "No file $Bin" }
if (-not (Test-Path $launcher)) { throw 'No launcher: run updater\build-usb.ps1 first' }
New-Item -ItemType Directory -Force $Work, $OutDir | Out-Null
Set-Location $Work

Write-Host '== Overlays'
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'build-synsepalum-overlay.ps1') | ForEach-Object { Write-Host "  $_" }
if ($LASTEXITCODE -ne 0) { throw 'overlay build failed' }

Write-Host '== Original firmware'
Run-Vi footer $Bin
Run-Lzo unpack $Bin system system.img
$prop = cmd /c 'debugfs -R "cat /build.prop" system.img 2>nul'
if (-not ($prop -match '^ro\.build\.display\.id=.* V1\.3\.15 ')) { throw 'This bin is not V1.3.15: the translations are made for that version' }
if (-not ($prop -contains 'ro.product.device=synsepalum_YN')) { throw 'This bin is not for synsepalum_YN' }

Write-Host '== Adding the Vietnamese overlays and the launcher'
Copy-Item system.img system-new.img -Force
Remove-Item add -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory add | Out-Null
$map = [ordered]@{ 'XgimiSettingsVi-overlay.apk' = 'XgimiSettingsVi.apk'; 'XgimiQuickVi-overlay.apk' = 'XgimiQuickVi.apk'; 'XgimiPowerVi-overlay.apk' = 'XgimiPowerVi.apk' }
$c = New-Object System.Text.StringBuilder
# Paths in a write are relative to the work folder, where debugfs runs
Add-Dir $c '/vendor/overlay' '2000'
[void]$c.AppendLine('cd /vendor/overlay')
foreach ($k in $map.Keys) {
    Copy-Item (Join-Path $root "dist\$k") "add\$($map[$k])"
    Add-File $c "add/$($map[$k])" $map[$k] '2000'
}
Add-Dir $c '/priv-app/TvHome'
[void]$c.AppendLine('cd /priv-app/TvHome')
Copy-Item $launcher 'add\TvHome.apk'
Add-File $c 'add/TvHome.apk' 'TvHome.apk'
Debugfs 'system-new.img' $c.ToString()

Write-Host '== Vietnamese and Vietnam time as the defaults'
$props = @{ 'persist.sys.locale' = 'vi-VN'; 'persist.sys.language' = 'vi'; 'persist.sys.country' = 'VN'
    'persist.sys.timezone' = 'Asia/Ho_Chi_Minh'; 'ro.product.locale' = 'vi-VN' }
cmd /c 'debugfs -R "dump /build.prop prop.old" system-new.img 2>nul' | Out-Null
$left = @{} + $props
$lines = foreach ($l in [IO.File]::ReadAllLines("$Work\prop.old", $ascii)) {
    $key = ($l -split '=', 2)[0]
    if ($left.ContainsKey($key)) { "$key=$($left[$key])"; $left.Remove($key) }
    elseif ($props.ContainsKey($key)) { continue }   # a repeat of the same key
    else { $l }
}
$lines = @($lines) + @($left.GetEnumerator() | Sort-Object Key | ForEach-Object { "$($_.Key)=$($_.Value)" })
[IO.File]::WriteAllText("$Work\prop.new", (($lines -join "`n") + "`n"), $ascii)
$c = New-Object System.Text.StringBuilder
[void]$c.AppendLine('cd /')
[void]$c.AppendLine('rm build.prop')
Add-File $c 'prop.new' 'build.prop'
Debugfs 'system-new.img' $c.ToString()
foreach ($k in ($props.Keys | Sort-Object)) { Write-Host "  $k=$($props[$k])" }

Write-Host '== Checking the changed file system (read only)'
# XGIMI's own image already fails e2fsck (made by make_ext4fs: files on backup group blocks, image shorter than the file
# system), so the changed one must report those same problems and no others
function Fsck-Problems([string]$image) {
    cmd /c "e2fsck -fn $image 2>&1" | Where-Object { "$_" -notmatch 'physical size|^system: \d+/|^\s*$' }
}
$before = @(Fsck-Problems 'system.img')
$after = @(Fsck-Problems 'system-new.img')
if (Compare-Object $before $after) { Compare-Object $before $after | Select-Object -First 15 | ForEach-Object { Write-Host "  $($_.SideIndicator) $($_.InputObject)" }; throw 'e2fsck reports new problems' }
Write-Host "  same e2fsck report as the original ($($before.Count) lines)"

Write-Host '== Packing'
$out = Join-Path $OutDir (Split-Path -Leaf $Bin)
Run-Lzo pack $Bin system system-new.img $out

Write-Host '== Checking the new bin'
Run-Vi footer $out
Run-Lzo compare $Bin $out
Run-Lzo verify $out system system-new.img
Remove-Item add -Recurse -Force
Remove-Item prop.old, prop.new, system-new.img, system.img -ErrorAction SilentlyContinue
Copy-Item (Join-Path $here 'HUONG-DAN-FIRMWARE-H2.txt') $OutDir -Force
Write-Host "== Done: $out"
