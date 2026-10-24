# Cai "Chinh sua Launcher TV" cho nguoi dung hien tai (khong can quyen Admin).
# Duoc CaiDat-ChinhSuaLauncher.exe (IExpress) giai nen ra thu muc tam va chay qua install.cmd.
param([switch]$Quiet)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms

$appName = 'Chỉnh sửa Launcher TV'
$dest = Join-Path $env:LOCALAPPDATA 'Programs\ChinhSuaLauncher'
$payload = Join-Path $PSScriptRoot 'payload.zip'
$regKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ChinhSuaLauncher'
$ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'

try {
    # Ban cu: giu lai IP da nho, thay het phan con lai
    $lastIp = Join-Path $dest 'tools\last-tv-ip.txt'
    $savedIp = if (Test-Path $lastIp) { Get-Content $lastIp -Raw } else { $null }
    # The adb server started by the installed app keeps adb.exe locked: stop it before replacing the files
    $oldAdb = Join-Path $dest 'tools\platform-tools\adb.exe'
    if (Test-Path $oldAdb) {
        try { & $oldAdb kill-server 2>$null | Out-Null } catch { }
        Get-Process adb -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $oldAdb } |
            ForEach-Object { try { $_.Kill(); $_.WaitForExit(3000) | Out-Null } catch { } }
    }
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    New-Item -ItemType Directory -Force $dest | Out-Null
    Expand-Archive -Path $payload -DestinationPath $dest -Force
    if ($savedIp) { Set-Content -Path $lastIp -Value $savedIp.Trim() -Encoding ascii }

    $shell = New-Object -ComObject WScript.Shell
    $links = @(
        (Join-Path ([Environment]::GetFolderPath('Desktop')) "$appName.lnk"),
        (Join-Path ([Environment]::GetFolderPath('Programs')) "$appName.lnk")
    )
    foreach ($l in $links) {
        # WScript.Shell khong luu duoc ten co dau tieng Viet: luu ten ASCII roi doi ten
        $tmp = Join-Path (Split-Path $l) 'ChinhSuaLauncherTV.lnk'
        $s = $shell.CreateShortcut($tmp)
        $s.TargetPath = $ps
        $s.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$dest\LauncherEditor.ps1`""
        $s.WorkingDirectory = $dest
        $s.IconLocation = "$dest\app.ico,0"
        $s.WindowStyle = 7
        $s.Description = 'Chỉnh giao diện launcher trên TV qua mạng'
        $s.Save()
        Move-Item -LiteralPath $tmp -Destination $l -Force
    }

    # Muc trong Settings > Apps de go cai dat
    New-Item -Path $regKey -Force | Out-Null
    $size = [int]((Get-ChildItem $dest -Recurse -File | Measure-Object Length -Sum).Sum / 1KB)
    $props = @{
        DisplayName = $appName; DisplayVersion = '1.0'; Publisher = 'CleanTV'
        DisplayIcon = "$dest\app.ico"; InstallLocation = $dest
        UninstallString = "`"$ps`" -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$dest\uninstall.ps1`""
        InstallDate = (Get-Date -Format 'yyyyMMdd')
    }
    foreach ($k in $props.Keys) { Set-ItemProperty -Path $regKey -Name $k -Value $props[$k] }
    Set-ItemProperty -Path $regKey -Name EstimatedSize -Value $size -Type DWord
    Set-ItemProperty -Path $regKey -Name NoModify -Value 1 -Type DWord
    Set-ItemProperty -Path $regKey -Name NoRepair -Value 1 -Type DWord

    if (-not $Quiet) {
        $ans = [System.Windows.Forms.MessageBox]::Show("Đã cài xong $appName.`nLối tắt đã có trên Desktop và Start Menu.`n`nMở ứng dụng ngay?", $appName, 'YesNo', 'Information')
        if ($ans -eq 'Yes') {
            Start-Process -FilePath $ps -ArgumentList "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$dest\LauncherEditor.ps1`"" -WorkingDirectory $dest -WindowStyle Hidden
        }
    }
} catch {
    if ($Quiet) { throw }
    [System.Windows.Forms.MessageBox]::Show("Cài đặt thất bại:`n$_", $appName, 'OK', 'Error') | Out-Null
    exit 1
}
