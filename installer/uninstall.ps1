# Go "Chinh sua Launcher TV": xoa loi tat, muc trong Settings > Apps va thu muc cai dat.
param([switch]$Quiet)
Add-Type -AssemblyName System.Windows.Forms
$appName = 'Chỉnh sửa Launcher TV'
$dest = Join-Path $env:LOCALAPPDATA 'Programs\ChinhSuaLauncher'

if (-not $Quiet) {
    $ans = [System.Windows.Forms.MessageBox]::Show("Gỡ $appName khỏi máy này?`n(Cấu hình trên TV không bị ảnh hưởng.)", $appName, 'YesNo', 'Question')
    if ($ans -ne 'Yes') { exit 0 }
}
Set-Location $env:TEMP
foreach ($l in @(
        (Join-Path ([Environment]::GetFolderPath('Desktop')) "$appName.lnk"),
        (Join-Path ([Environment]::GetFolderPath('Programs')) "$appName.lnk"))) {
    Remove-Item $l -Force -ErrorAction SilentlyContinue
}
Remove-Item 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ChinhSuaLauncher' -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $dest -Recurse -Force -ErrorAction SilentlyContinue
if (-not $Quiet) {
    $msg = if (Test-Path $dest) { "Đã gỡ phần lớn. Hãy đóng ứng dụng rồi xoá thư mục:`n$dest" } else { "Đã gỡ $appName." }
    [System.Windows.Forms.MessageBox]::Show($msg, $appName) | Out-Null
}
