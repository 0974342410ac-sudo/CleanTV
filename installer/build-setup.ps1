# Tao dist\CaiDat-ChinhSuaLauncher.exe (bo cai, dung IExpress co san trong Windows) va
# dist\ChinhSuaLauncher.zip (ban chay ngay, khong cai).
# Chay: powershell -ExecutionPolicy Bypass -File installer\build-setup.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$here = $PSScriptRoot
$root = Split-Path -Parent $here
$dist = Join-Path $root 'dist'
$work = Join-Path $env:TEMP 'ChinhSuaLauncher-build'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
$payload = Join-Path $work 'payload'
$setup = Join-Path $work 'setup'
New-Item -ItemType Directory -Force "$payload\tools\platform-tools", $setup, $dist | Out-Null

# PowerShell 5.1 chi doc dung tieng Viet trong .ps1/.txt co BOM
function Copy-Utf8Bom([string]$src, [string]$dst) {
    $t = [IO.File]::ReadAllText($src, [Text.Encoding]::UTF8)
    [IO.File]::WriteAllText($dst, $t, (New-Object Text.UTF8Encoding $true))
}

# Icon: o vuong bo goc toi, hinh TV trang. ICO chua cac anh PNG (Windows Vista tro len doc duoc).
function New-AppIcon([string]$path) {
    $pngs = foreach ($s in 16, 32, 48, 256) {
        $bmp = New-Object Drawing.Bitmap $s, $s
        $g = [Drawing.Graphics]::FromImage($bmp)
        $g.SmoothingMode = 'AntiAlias'
        $f = $s / 256.0
        $r = [float](56 * $f); $d = $r * 2; $e = [float]($s - 1)
        $bg = New-Object Drawing.Drawing2D.GraphicsPath
        $bg.AddArc(0, 0, $d, $d, 180, 90); $bg.AddArc($e - $d, 0, $d, $d, 270, 90)
        $bg.AddArc($e - $d, $e - $d, $d, $d, 0, 90); $bg.AddArc(0, $e - $d, $d, $d, 90, 90); $bg.CloseFigure()
        $brush = New-Object Drawing.Drawing2D.LinearGradientBrush((New-Object Drawing.PointF 0, 0), (New-Object Drawing.PointF 0, $s),
            [Drawing.Color]::FromArgb(255, 58, 58, 64), [Drawing.Color]::FromArgb(255, 12, 12, 14))
        $g.FillPath($brush, $bg)
        $pen = New-Object Drawing.Pen ([Drawing.Color]::White), ([float][Math]::Max(1.5, 14 * $f))
        $pen.LineJoin = 'Round'; $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
        $g.DrawRectangle($pen, [float](48 * $f), [float](64 * $f), [float](160 * $f), [float](100 * $f))
        $g.DrawLine($pen, [float](96 * $f), [float](196 * $f), [float](160 * $f), [float](196 * $f))
        $g.FillEllipse([Drawing.Brushes]::White, [float](118 * $f), [float](104 * $f), [float](20 * $f), [float](20 * $f))
        $g.Dispose()
        $ms = New-Object IO.MemoryStream
        $bmp.Save($ms, [Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
        , @($s, $ms.ToArray())
    }
    $out = New-Object IO.MemoryStream
    $w = New-Object IO.BinaryWriter $out
    $w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]$pngs.Count)
    $offset = 6 + 16 * $pngs.Count
    foreach ($p in $pngs) {
        $dim = if ($p[0] -ge 256) { 0 } else { $p[0] }
        $w.Write([byte]$dim); $w.Write([byte]$dim); $w.Write([byte]0); $w.Write([byte]0)
        $w.Write([uint16]1); $w.Write([uint16]32); $w.Write([uint32]$p[1].Length); $w.Write([uint32]$offset)
        $offset += $p[1].Length
    }
    foreach ($p in $pngs) { $w.Write([byte[]]$p[1]) }
    $w.Flush()
    [IO.File]::WriteAllBytes($path, $out.ToArray())
}

# 1. Noi dung ung dung
Copy-Utf8Bom "$root\LauncherEditor.ps1" "$payload\LauncherEditor.ps1"
Copy-Utf8Bom "$here\uninstall.ps1" "$payload\uninstall.ps1"
Copy-Utf8Bom "$here\HUONG-DAN.txt" "$payload\HUONG-DAN.txt"
Copy-Item "$root\ChinhSuaLauncher.cmd" $payload
# Launcher de ung dung cai len TV: ban build moi nhat (launcher\build-and-install.ps1 hoac gradlew assembleRelease)
$apk = Join-Path $root 'launcher\app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "Chua co APK launcher: $apk. Build launcher truoc." }
Copy-Item $apk "$payload\tvhome.apk"
New-AppIcon "$payload\app.ico"
foreach ($f in 'adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'libwinpthread-1.dll', 'NOTICE.txt', 'source.properties') {
    Copy-Item "$root\tools\platform-tools\$f" "$payload\tools\platform-tools\"
}

# 2. Ban zip chay ngay
$zip = Join-Path $dist 'ChinhSuaLauncher.zip'
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path "$payload\*" -DestinationPath $zip


# 3. Bo cai .exe: SetupStub.cs bien dich bang csc.exe cua .NET Framework 4 (co san tren Windows 10/11),
#    nhung install.ps1 + payload.zip lam resource; icon va manifest asInvoker (khong hoi UAC).
Compress-Archive -Path "$payload\*" -DestinationPath "$setup\payload.zip"
Copy-Utf8Bom "$here\install.ps1" "$setup\install.ps1"
$exe = Join-Path $dist 'CaiDat-ChinhSuaLauncher.exe'
if (Test-Path $exe) { Remove-Item $exe }
$csc = Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) { $csc = Join-Path $env:SystemRoot 'Microsoft.NET\Framework\v4.0.30319\csc.exe' }
$out = & $csc /nologo /target:winexe /optimize+ /codepage:65001 "/out:$exe" "/win32icon:$payload\app.ico" `
    "/win32manifest:$here\setup.manifest" "/resource:$setup\install.ps1,install.ps1" "/resource:$setup\payload.zip,payload.zip" `
    /reference:System.Windows.Forms.dll "$here\SetupStub.cs"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $exe)) { throw "csc khong tao duoc $exe`n$out" }
Remove-Item $work -Recurse -Force
Write-Host "Da tao:"
Get-Item $exe, $zip | ForEach-Object { Write-Host ("  {0}  ({1:N0} KB)" -f $_.FullName, ($_.Length / 1KB)) }