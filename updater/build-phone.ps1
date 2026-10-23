# Ban cap nhat qua dien thoai (Android): dist\DienThoai\CapNhatTV-DienThoai.apk va version.json.
# Cai APK len dien thoai, nhap IP cua TV (hoac bam "Tim TV"), bam "Cap nhat": ung dung noi chuyen voi ADB mang
# cua TV (cong 5555) va lam dung nhu ban USB-CapNhat chung. Moi lan mo, ung dung doc version.json tren GitHub
# Release moi nhat (BuildConfig.UPDATE_URL) va de nghi tai ban moi neu co.
# Chay:      powershell -ExecutionPolicy Bypass -File updater\build-phone.ps1
# Phat hanh: them -Publish (can `gh auth login` truoc): tao GitHub Release "dienthoai-<ma>" kem APK va version.json.
#            Kho phai cong khai (hoac doi UPDATE_URL trong launcher\updater\build.gradle) thi dien thoai moi tai duoc.
param([switch]$Publish, [string]$Notes = '')
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = Split-Path -Parent $here
$repo = '0974342410ac-sudo/CleanTV'

# Assets giong ban USB chung (launcher, danh sach tat, ban dich): build-usb.ps1 dung va chep vao src\main, src\chung
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $here 'build-usb.ps1')
if ($LASTEXITCODE -ne 0) { throw 'build-usb.ps1 that bai' }

# Ma phien ban tang theo thoi gian (yyMMddHH), de moi ban phat hanh moi hon ban truoc
$now = Get-Date
$code = [int]$now.ToString('yyMMddHH')
$name = $now.ToString('yyyy.MM.dd-HHmm')
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'E:\buildtools\jdk\jdk-17.0.20.1+1' }
Push-Location (Join-Path $root 'launcher')
& .\gradlew.bat ':updater:assembleDienthoaiRelease' "-PphoneVersionCode=$code" "-PphoneVersionName=$name" -q --console=plain
$exit = $LASTEXITCODE
Pop-Location
if ($exit -ne 0) { throw 'Build ban dien thoai that bai' }

$out = Join-Path $root 'dist\DienThoai'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null
$apk = Join-Path $out 'CapNhatTV-DienThoai.apk'
Copy-Item (Join-Path $root 'launcher\updater\build\outputs\apk\dienthoai\release\updater-dienthoai-release.apk') $apk
$tag = "dienthoai-$code"
$json = [ordered]@{
    versionCode = $code
    versionName = $name
    apk         = "https://github.com/$repo/releases/download/$tag/CapNhatTV-DienThoai.apk"
    notes       = $Notes
} | ConvertTo-Json
[IO.File]::WriteAllText((Join-Path $out 'version.json'), $json, (New-Object Text.UTF8Encoding $false))
$guide = [IO.File]::ReadAllText((Join-Path $here 'HUONG-DAN-DIENTHOAI.txt'), [Text.Encoding]::UTF8)
[IO.File]::WriteAllText((Join-Path $out 'HUONG-DAN.txt'), $guide, (New-Object Text.UTF8Encoding $true))
Write-Host ("Da tao {0} ({1:N0} KB), phien ban {2} ({3})" -f $apk, ((Get-Item $apk).Length / 1KB), $name, $code)

if ($Publish) {
    $gh = 'C:\Program Files\GitHub CLI\gh.exe'
    $title = "Cập nhật TV qua điện thoại $name"
    & $gh release create $tag $apk (Join-Path $out 'version.json') --repo $repo --title $title --notes $(if ($Notes) { $Notes } else { $title }) --latest
    if ($LASTEXITCODE -ne 0) { throw 'Tao GitHub Release that bai' }
    Write-Host "Da phat hanh https://github.com/$repo/releases/tag/$tag"
}
