# Ung dung Windows chinh giao dien launcher (com.gathin.tvhome) tren TV qua ADB, khong can build lai.
# Ghi config.json (+ wallpaper.jpg) vao thu muc du lieu cua launcher tren TV roi khoi dong lai Home,
# giong tools\theme.ps1 nhung chi can adb (khong can Python). Cac khoa trong config.json: xem Config.java.
#
# Chay: nhap dup ChinhSuaLauncher.cmd, hoac: powershell -ExecutionPolicy Bypass -File .\LauncherEditor.ps1
param([string]$TvIp = '')

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -Namespace Win32 -Name Dpi -MemberDefinition '[DllImport("user32.dll")] public static extern bool SetProcessDPIAware();'
[Win32.Dpi]::SetProcessDPIAware() | Out-Null
[System.Windows.Forms.Application]::EnableVisualStyles()

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:adb = Join-Path $root 'tools\platform-tools\adb.exe'
if (-not (Test-Path $script:adb)) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    $script:adb = if ($cmd) { $cmd.Source } else { $null }
}
$pkg = 'com.gathin.tvhome'
$tvDir = "/sdcard/Android/data/$pkg/files"
$work = Join-Path $env:TEMP 'LauncherEditor'
New-Item -ItemType Directory -Force $work | Out-Null
$lastIpFile = Join-Path $root 'tools\last-tv-ip.txt'
$script:dev = $null
$script:wallpaperFile = $null      # anh moi chon tren may tinh
$script:removeWallpaper = $false
$script:extraKeys = [ordered]@{}   # khoa trong config.json tren TV ma ung dung nay khong sua: giu nguyen

# Mac dinh = launcher\app\src\main\res\values\config.xml
$defaults = @{
    dock_apps = @('com.viettel.tv360.tv', 'com.google.android.youtube.tv', 'net.fptplay.ottbox', 'io.gh.reisxd.tizentube.cobalt')
    hidden_apps = @('com.android.tv'); greeting_name = ''; columns = 4; rows_below_dock = 0
    sky_intensity = 0; background_color = '#000000'; dock_title = $true; show_stats = $false
    reduced_motion = $false; animations = $true; wallpaper_dim = 0; weather_city = 'Phù Đổng'; wallpaper_by_time = $false; keep_adb_on = $false
}
$knownNames = @{
    'com.viettel.tv360.tv' = 'TV360'; 'com.google.android.youtube.tv' = 'YouTube'; 'net.fptplay.ottbox' = 'FPT Play'
    'io.gh.reisxd.tizentube.cobalt' = 'TizenTube'; 'com.dinhlap.dlstore' = 'DL Store'; 'com.netflix.ninja' = 'Netflix'
    'org.xbmc.kodi' = 'Kodi'; 'com.lonelycatgames.Xplore' = 'X-plore'; 'com.android.vending' = 'Play Store'
    'com.google.android.katniss' = 'Google Assistant'; 'com.android.tv' = 'Live TV'
}
function Label-Of([string]$p) { if ($knownNames.ContainsKey($p)) { "$($knownNames[$p])  ($p)" } else { $p } }
function Pkg-Of([string]$label) { if ($label -match '\(([^()]+)\)\s*$') { $Matches[1] } else { $label.Trim() } }

# ---------- ADB ----------
function Invoke-Adb([string]$Arguments) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo $script:adb, $Arguments
    $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $p = [System.Diagnostics.Process]::Start($psi)
    $err = $p.StandardError.ReadToEndAsync()
    $out = $p.StandardOutput.ReadToEnd()
    $p.WaitForExit()
    [pscustomobject]@{ Out = $out.Trim(); Err = $err.Result.Trim(); Code = $p.ExitCode }
}
# Lenh shell: khong dung dau nhay kep (Windows tach tham so theo chung); dung nhay don phia TV.
function Tv([string]$ShellCmd) { (Invoke-Adb "-s $script:dev shell $ShellCmd").Out }
function Start-AdbServer {
    # Khoi dong server rieng, khong chuyen huong output: neu de 'adb connect' tu khoi dong, server giu pipe va treo.
    $p = Start-Process -FilePath $script:adb -ArgumentList 'start-server' -WindowStyle Hidden -PassThru
    $p.WaitForExit()
}
function Get-Screencap {
    $psi = New-Object System.Diagnostics.ProcessStartInfo $script:adb, "-s $script:dev exec-out screencap -p"
    $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true; $psi.RedirectStandardOutput = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    $ms = New-Object System.IO.MemoryStream
    $p.StandardOutput.BaseStream.CopyTo($ms)
    $p.WaitForExit()
    $ms.Position = 0
    $ms
}

# ---------- Giao dien ----------
$font = New-Object System.Drawing.Font('Segoe UI', 10)
$form = New-Object System.Windows.Forms.Form
$form.Text = 'Chỉnh sửa Launcher TV'
$form.Font = $font
$form.ClientSize = New-Object System.Drawing.Size(980, 798)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedSingle'
$form.MaximizeBox = $false
$form.AutoScaleMode = 'Dpi'
$icoFile = Join-Path $root 'app.ico'
if (Test-Path $icoFile) { try { $form.Icon = New-Object System.Drawing.Icon $icoFile } catch { } }

function New-Ctl($type, $x, $y, $w, $h, $parent, $text = $null) {
    $c = New-Object "System.Windows.Forms.$type"
    $c.Location = New-Object System.Drawing.Point($x, $y)
    $c.Size = New-Object System.Drawing.Size($w, $h)
    if ($null -ne $text) { $c.Text = $text }
    $parent.Controls.Add($c)
    $c
}

# Ket noi
$gConn = New-Ctl GroupBox 12 8 956 70 $form 'Kết nối TV'
New-Ctl Label 14 30 60 24 $gConn 'TV:' | Out-Null
# Go IP, hoac chon TV ma "Tu tim" thay tren mang ("192.168.1.50  —  model")
$txtIp = New-Ctl ComboBox 74 27 290 28 $gConn
$txtIp.DropDownStyle = 'DropDown'
$btnFind = New-Ctl Button 372 25 100 30 $gConn 'Tự tìm'
$btnConnect = New-Ctl Button 480 25 110 30 $gConn 'Kết nối'
$lblStatus = New-Ctl Label 600 30 346 24 $gConn 'Chưa kết nối'
$lblStatus.ForeColor = [System.Drawing.Color]::DimGray

# Dock
$gDock = New-Ctl GroupBox 12 86 470 200 $form 'Dock (4 ứng dụng, trái sang phải)'
$cmbDock = @()
for ($i = 0; $i -lt 4; $i++) {
    New-Ctl Label 14 (32 + $i * 40) 60 24 $gDock ("Ô " + ($i + 1) + ':') | Out-Null
    $cb = New-Ctl ComboBox 74 (29 + $i * 40) 380 28 $gDock
    $cb.DropDownStyle = 'DropDown'
    $cmbDock += $cb
}

# Hien thi
$gLook = New-Ctl GroupBox 12 294 470 402 $form 'Hiển thị'
New-Ctl Label 14 32 150 24 $gLook 'Tên lời chào:' | Out-Null
$txtGreeting = New-Ctl TextBox 170 29 284 26 $gLook
New-Ctl Label 14 70 150 24 $gLook 'Số cột:' | Out-Null
$numColumns = New-Ctl NumericUpDown 170 67 70 26 $gLook
$numColumns.Minimum = 4; $numColumns.Maximum = 7
New-Ctl Label 250 70 140 24 $gLook 'Hàng dưới dock:' | Out-Null
$numRows = New-Ctl NumericUpDown 394 67 60 26 $gLook
$numRows.Minimum = 0; $numRows.Maximum = 3
New-Ctl Label 14 110 150 24 $gLook 'Bầu trời theo giờ:' | Out-Null
$trkSky = New-Ctl TrackBar 164 102 230 40 $gLook
$trkSky.Minimum = 0; $trkSky.Maximum = 100; $trkSky.TickFrequency = 10
$lblSky = New-Ctl Label 400 110 60 24 $gLook '0'
$trkSky.Add_ValueChanged({ $lblSky.Text = "$($trkSky.Value)" })
New-Ctl Label 14 154 150 24 $gLook 'Màu nền:' | Out-Null
$pnlColor = New-Ctl Panel 170 150 40 28 $gLook
$pnlColor.BorderStyle = 'FixedSingle'
$btnColor = New-Ctl Button 220 149 110 30 $gLook 'Chọn màu…'
$chkTitle = New-Ctl CheckBox 14 190 440 26 $gLook 'Tên ứng dụng lớn phía trên dock'
$chkAnim = New-Ctl CheckBox 14 218 440 26 $gLook 'Hiệu ứng chuyển động'
$chkReduced = New-Ctl CheckBox 14 246 440 26 $gLook 'Giảm chuyển động (cho TV yếu)'
$chkStats = New-Ctl CheckBox 14 274 440 26 $gLook 'Hiện CPU / RAM / bộ nhớ dưới đồng hồ'
$chkWeather = New-Ctl CheckBox 14 302 440 26 $gLook 'Lời chào kèm thời tiết Phù Đổng (cần Internet)'
$chkAdb = New-Ctl CheckBox 14 330 440 26 $gLook 'Tự bật lại ADB khi TV khởi động (để ứng dụng luôn kết nối được)'
New-Ctl Label 14 364 150 24 $gLook 'Múi giờ TV:' | Out-Null
$cmbTz = New-Ctl ComboBox 170 361 284 28 $gLook
$cmbTz.DropDownStyle = 'DropDown'
# Mui gio IANA hay dung; go ten khac (vd. Europe/Rome) cung duoc
$tzList = @(
    '(UTC-08) America/Los_Angeles', '(UTC-06) America/Chicago', '(UTC-05) America/New_York', '(UTC-03) America/Sao_Paulo',
    '(UTC+00) Europe/London', '(UTC+01) Europe/Paris', '(UTC+01) Europe/Berlin', '(UTC+03) Europe/Moscow',
    '(UTC+04) Asia/Dubai', '(UTC+05:30) Asia/Kolkata', '(UTC+07) Asia/Ho_Chi_Minh', '(UTC+07) Asia/Bangkok',
    '(UTC+07) Asia/Jakarta', '(UTC+08) Asia/Singapore', '(UTC+08) Asia/Shanghai', '(UTC+08) Asia/Taipei',
    '(UTC+08) Asia/Manila', '(UTC+09) Asia/Seoul', '(UTC+09) Asia/Tokyo', '(UTC+10) Australia/Sydney', '(UTC+12) Pacific/Auckland')
foreach ($z in $tzList) { $cmbTz.Items.Add($z) | Out-Null }
function Tz-Of([string]$text) { ($text -replace '^\([^)]*\)\s*', '').Trim() }
$btnColor.Add_Click({
    $dlg = New-Object System.Windows.Forms.ColorDialog
    $dlg.Color = $pnlColor.BackColor; $dlg.FullOpen = $true
    if ($dlg.ShowDialog() -eq 'OK') { $pnlColor.BackColor = $dlg.Color }
})

# Hinh nen
$gWall = New-Ctl GroupBox 498 86 470 336 $form 'Hình nền'
$rbSingle = New-Ctl RadioButton 14 26 190 26 $gWall 'Một hình nền'
$rbTime = New-Ctl RadioButton 210 26 250 26 $gWall 'Đổi theo giờ (trên TV)'
$rbSingle.Checked = $true
function New-Thumb($x, $y, $w, $h, $parent) {
    $p = New-Ctl PictureBox $x $y $w $h $parent
    $p.BorderStyle = 'FixedSingle'; $p.SizeMode = 'Zoom'; $p.BackColor = [System.Drawing.Color]::Black
    $p
}
# Mot hinh nen: wallpaper.jpg
$pnlSingle = New-Ctl Panel 8 56 454 236 $gWall
$picWall = New-Thumb 6 0 270 152 $pnlSingle
$btnPick = New-Ctl Button 288 0 156 32 $pnlSingle 'Chọn ảnh…'
$btnNoWall = New-Ctl Button 288 40 156 32 $pnlSingle 'Bỏ hình nền'
$lblWall = New-Ctl Label 6 160 440 24 $pnlSingle 'Không có hình nền'
$lblWall.ForeColor = [System.Drawing.Color]::DimGray
# Theo gio: wallpapers/morning|afternoon|evening|night.jpg, launcher doi anh theo gio (wallpaper_by_time)
$pnlTime = New-Ctl Panel 8 56 454 236 $gWall
$pnlTime.Visible = $false
$slotKeys = @('morning', 'afternoon', 'evening', 'night')
$slotTitles = @{ morning = 'Sáng 5h–12h'; afternoon = 'Chiều 12h–17h'; evening = 'Tối 17h–22h'; night = 'Đêm 22h–5h' }
$slotPics = @{}; $script:slotNew = @{}; $script:slotRemove = @{}
for ($i = 0; $i -lt 4; $i++) {
    $key = $slotKeys[$i]
    $x = 6 + ($i % 2) * 226; $y = [Math]::Floor($i / 2) * 118
    $slotPics[$key] = New-Thumb $x $y 136 77 $pnlTime
    New-Ctl Label ($x + 142) $y 84 22 $pnlTime ($slotTitles[$key] -split ' ')[0] | Out-Null
    New-Ctl Label $x ($y + 80) 136 22 $pnlTime ($slotTitles[$key] -split ' ')[1] | ForEach-Object { $_.ForeColor = [System.Drawing.Color]::DimGray }
    $bp = New-Ctl Button ($x + 142) ($y + 22) 76 28 $pnlTime 'Chọn…'
    $bx = New-Ctl Button ($x + 142) ($y + 54) 76 28 $pnlTime 'Bỏ'
    $bp.Tag = $key; $bx.Tag = $key
    $bp.Add_Click({
        $k = $this.Tag
        $dlg = New-Object System.Windows.Forms.OpenFileDialog
        $dlg.Filter = 'Ảnh (*.jpg;*.jpeg;*.png;*.bmp;*.webp)|*.jpg;*.jpeg;*.png;*.bmp;*.webp'
        if ($dlg.ShowDialog() -eq 'OK') {
            try { Set-Thumb $k $dlg.FileName; $script:slotNew[$k] = $dlg.FileName; $script:slotRemove.Remove($k) }
            catch { [System.Windows.Forms.MessageBox]::Show("Không mở được ảnh này: $_") | Out-Null }
        }
    })
    $bx.Add_Click({ $k = $this.Tag; Set-Thumb $k $null; $script:slotNew.Remove($k); $script:slotRemove[$k] = $true })
}
$lblTimeHint = New-Ctl Label 6 216 444 20 $pnlTime 'Ô để trống dùng ảnh của khung giờ trước. TV tự đổi, không cần bật máy tính.'
$lblTimeHint.ForeColor = [System.Drawing.Color]::DimGray
$lblTimeHint.Font = New-Object System.Drawing.Font('Segoe UI', 8.5)
$rbTime.Add_CheckedChanged({ $pnlTime.Visible = $rbTime.Checked; $pnlSingle.Visible = -not $rbTime.Checked })
New-Ctl Label 14 302 200 24 $gWall 'Làm tối hình nền (%):' | Out-Null
$numDim = New-Ctl NumericUpDown 214 299 70 26 $gWall
$numDim.Minimum = 0; $numDim.Maximum = 90; $numDim.Increment = 5

# An ung dung
$gHide = New-Ctl GroupBox 498 430 470 266 $form 'Ẩn ứng dụng khỏi màn hình chính'
$lstHidden = New-Ctl CheckedListBox 14 28 442 226 $gHide
$lstHidden.CheckOnClick = $true

# Nut duoi
$btnApply = New-Ctl Button 12 710 180 44 $form 'Áp dụng lên TV'
$btnApply.Font = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
$btnShot = New-Ctl Button 200 710 160 44 $form 'Xem màn hình TV'
$btnReset = New-Ctl Button 368 710 140 44 $form 'Về mặc định'
$btnInstall = New-Ctl Button 548 710 220 44 $form 'Cài launcher lên TV'
$btnRestore = New-Ctl Button 776 710 192 44 $form 'Trả launcher gốc'
$lblHint = New-Ctl Label 12 762 956 26 $form 'Áp dụng xong, TV tự khởi động lại màn hình chính.'
$lblHint.ForeColor = [System.Drawing.Color]::DimGray
$editors = @($gDock, $gLook, $gWall, $gHide, $btnApply, $btnShot, $btnReset)
foreach ($c in $editors + @($btnInstall, $btnRestore)) { $c.Enabled = $false }

# ---------- Doc / ghi cau hinh ----------
function Show-Wallpaper([string]$path, [string]$caption) {
    if ($picWall.Image) { $picWall.Image.Dispose(); $picWall.Image = $null }
    if ($path -and (Test-Path $path)) {
        $bytes = [System.IO.File]::ReadAllBytes($path)
        $picWall.Image = [System.Drawing.Image]::FromStream((New-Object System.IO.MemoryStream(, $bytes)))
    }
    $lblWall.Text = $caption
}

function Set-Thumb([string]$slot, [string]$path) {
    $p = $slotPics[$slot]
    if ($p.Image) { $p.Image.Dispose(); $p.Image = $null }
    if ($path -and (Test-Path $path)) {
        $bytes = [System.IO.File]::ReadAllBytes($path)
        $p.Image = [System.Drawing.Image]::FromStream((New-Object System.IO.MemoryStream(, $bytes)))
    }
}

function Fill-Form($cfg, [string[]]$apps) {
    $labels = @($apps | Sort-Object { (Label-Of $_) } | ForEach-Object { Label-Of $_ })
    for ($i = 0; $i -lt 4; $i++) {
        $cmbDock[$i].Items.Clear()
        $cmbDock[$i].Items.Add('(trống)') | Out-Null
        foreach ($l in $labels) { $cmbDock[$i].Items.Add($l) | Out-Null }
        $p = if ($i -lt @($cfg.dock_apps).Count) { @($cfg.dock_apps)[$i] } else { '' }
        $cmbDock[$i].Text = if ($p) { Label-Of $p } else { '(trống)' }
    }
    $lstHidden.Items.Clear()
    $all = @($apps) + @($cfg.hidden_apps | Where-Object { $apps -notcontains $_ })
    foreach ($p in ($all | Sort-Object { (Label-Of $_) })) {
        $idx = $lstHidden.Items.Add((Label-Of $p))
        if (@($cfg.hidden_apps) -contains $p) { $lstHidden.SetItemChecked($idx, $true) }
    }
    $txtGreeting.Text = $cfg.greeting_name
    $numColumns.Value = [Math]::Max(4, [Math]::Min(7, [int]$cfg.columns))
    $numRows.Value = [Math]::Max(0, [Math]::Min(3, [int]$cfg.rows_below_dock))
    $trkSky.Value = [Math]::Max(0, [Math]::Min(100, [int]$cfg.sky_intensity))
    $lblSky.Text = "$($trkSky.Value)"
    try { $pnlColor.BackColor = [System.Drawing.ColorTranslator]::FromHtml($cfg.background_color) } catch { $pnlColor.BackColor = [System.Drawing.Color]::Black }
    $chkTitle.Checked = [bool]$cfg.dock_title
    $chkAnim.Checked = [bool]$cfg.animations
    $chkReduced.Checked = [bool]$cfg.reduced_motion
    $chkStats.Checked = [bool]$cfg.show_stats
    $chkWeather.Checked = -not [string]::IsNullOrWhiteSpace($cfg.weather_city)
    $chkAdb.Checked = [bool]$cfg.keep_adb_on
    $numDim.Value = [Math]::Max(0, [Math]::Min(90, [int]$cfg.wallpaper_dim))
    $rbTime.Checked = [bool]$cfg.wallpaper_by_time
    $rbSingle.Checked = -not $rbTime.Checked
}

function Load-FromTv {
    # Android 6 cannot list apps by intent from the shell: offer the user-installed apps instead
    $leanback = if (Is-OldAndroid) { (Tv 'pm list packages -3') -replace 'package:(\S+)', '$1/' } else { Tv 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER' }
    $apps = @($leanback -split "`n" | ForEach-Object { if ($_ -match '^\s*([A-Za-z0-9_.]+)/') { $Matches[1] } } |
        Where-Object { $_ -ne $pkg } | Sort-Object -Unique)

    $cfg = @{}; foreach ($k in $defaults.Keys) { $cfg[$k] = $defaults[$k] }
    $script:extraKeys = [ordered]@{}
    $raw = Tv "cat $tvDir/config.json 2>/dev/null"
    if ($raw -and $raw.TrimStart().StartsWith('{')) {
        try {
            $json = $raw | ConvertFrom-Json
            foreach ($prop in $json.PSObject.Properties) {
                if ($cfg.ContainsKey($prop.Name)) { $cfg[$prop.Name] = $prop.Value } else { $script:extraKeys[$prop.Name] = $prop.Value }
            }
        } catch { [System.Windows.Forms.MessageBox]::Show("config.json trên TV bị lỗi, dùng giá trị mặc định.`n$_") | Out-Null }
    }
    if ($script:extraKeys.Contains('wallpaper')) { $script:extraKeys.Remove('wallpaper') }
    Fill-Form $cfg $apps
    $script:tvTz = (Tv 'getprop persist.sys.timezone').Trim()
    $match = $tzList | Where-Object { (Tz-Of $_) -eq $script:tvTz } | Select-Object -First 1
    $cmbTz.Text = if ($match) { $match } else { $script:tvTz }

    $script:wallpaperFile = $null; $script:removeWallpaper = $false
    $size = Tv "stat -c %s $tvDir/wallpaper.jpg 2>/dev/null"
    if ($size -match '^\d+$' -and [int64]$size -gt 0) {
        $local = Join-Path $work 'tv-wallpaper.jpg'
        Invoke-Adb "-s $script:dev pull $tvDir/wallpaper.jpg `"$local`"" | Out-Null
        Show-Wallpaper $local 'Hình nền hiện tại trên TV'
    } else { Show-Wallpaper $null 'Không có hình nền' }

    # Anh theo gio dang co tren TV
    $script:slotNew = @{}; $script:slotRemove = @{}
    $onTv = @((Tv "ls $tvDir/wallpapers 2>/dev/null") -split "`n" | ForEach-Object { $_.Trim() })
    foreach ($k in $slotKeys) {
        $name = $onTv | Where-Object { $_ -match "^$k\.(jpg|jpeg|png|webp)$" } | Select-Object -First 1
        if ($name) {
            $local = Join-Path $work "tv-$name"
            Invoke-Adb "-s $script:dev pull $tvDir/wallpapers/$name `"$local`"" | Out-Null
            Set-Thumb $k $local
        } else { Set-Thumb $k $null }
    }
}

# Anh nen: cat vua 1920x1080 va luu JPEG, nhe cho TV 1 GB RAM.
function Convert-Wallpaper([string]$src, [string]$dst) {
    $img = [System.Drawing.Image]::FromFile($src)
    try {
        $W = 1920; $H = 1080
        $scale = [Math]::Max($W / $img.Width, $H / $img.Height)
        $w = [int]($img.Width * $scale); $h = [int]($img.Height * $scale)
        $bmp = New-Object System.Drawing.Bitmap($W, $H)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = 'HighQualityBicubic'
        $g.DrawImage($img, [int](($W - $w) / 2), [int](($H - $h) / 2), $w, $h)
        $g.Dispose()
        $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
        $ep = New-Object System.Drawing.Imaging.EncoderParameters(1)
        $ep.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]88)
        $bmp.Save($dst, $codec, $ep)
        $bmp.Dispose()
    } finally { $img.Dispose() }
}

# Khoi dong lai Home de launcher doc lai config.json. Nhu theme.ps1: force-stop lam Android go cac dich vu
# tro nang cua launcher, nen ghi lai danh sach neu co dich vu cua no.
$restartCmd = 'a=$(settings get secure enabled_accessibility_services); am force-stop ' + $pkg + '; am start -n ' + $pkg + '/.MainActivity >/dev/null; case $a in *' + $pkg + '*) sleep 1; settings put secure enabled_accessibility_services ''''; settings put secure enabled_accessibility_services $a; settings put secure accessibility_enabled 1 ;; esac; echo restarted'

function Push-Revision { Tv ("printf %s " + [guid]::NewGuid().ToString('N') + " > $tvDir/theme_revision") | Out-Null }

function Apply-ToTv {
    $dock = @()
    foreach ($cb in $cmbDock) { $p = Pkg-Of $cb.Text; if ($p -and $p -ne '(trống)' -and $dock -notcontains $p) { $dock += $p } }
    $hidden = @()
    foreach ($item in $lstHidden.CheckedItems) { $hidden += (Pkg-Of $item) }
    $c = $pnlColor.BackColor
    $cfg = [ordered]@{}
    foreach ($k in $script:extraKeys.Keys) { $cfg[$k] = $script:extraKeys[$k] }
    $cfg.dock_apps = $dock
    $cfg.hidden_apps = $hidden
    $cfg.greeting_name = $txtGreeting.Text.Trim()
    $cfg.columns = [int]$numColumns.Value
    $cfg.rows_below_dock = [int]$numRows.Value
    $cfg.sky_intensity = [int]$trkSky.Value
    $cfg.background_color = '#{0:X2}{1:X2}{2:X2}' -f $c.R, $c.G, $c.B
    $cfg.dock_title = $chkTitle.Checked
    $cfg.animations = $chkAnim.Checked
    $cfg.reduced_motion = $chkReduced.Checked
    $cfg.show_stats = $chkStats.Checked
    # Phu Dong (Gia Lam, Ha Noi); ten rong tat thoi tiet (khong dung mang)
    $cfg.weather_city = if ($chkWeather.Checked) { 'Phù Đổng' } else { '' }
    $cfg.weather_latitude = 21.0571
    $cfg.weather_longitude = 105.9632
    $cfg.wallpaper_dim = [int]$numDim.Value
    $cfg.wallpaper_by_time = $rbTime.Checked
    $cfg.keep_adb_on = $chkAdb.Checked

    $local = Join-Path $work 'config.json'
    $text = ConvertTo-Json -InputObject $cfg -Depth 5
    [System.IO.File]::WriteAllText($local, $text, (New-Object System.Text.UTF8Encoding $false))

    Tv "mkdir -p $tvDir" | Out-Null
    # Quyen de launcher tu bat lai ADB (AdbKeeper); chi cap duoc qua ADB
    if ($chkAdb.Checked) { Tv "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS" | Out-Null }
    $r = Invoke-Adb "-s $script:dev push `"$local`" $tvDir/config.json"
    if ($r.Code -ne 0) { throw "Không ghi được config.json: $($r.Err)" }
    if ($script:wallpaperFile) {
        $jpg = Join-Path $work 'wallpaper.jpg'
        Convert-Wallpaper $script:wallpaperFile $jpg
        $r = Invoke-Adb "-s $script:dev push `"$jpg`" $tvDir/wallpaper.jpg"
        if ($r.Code -ne 0) { throw "Không ghi được hình nền: $($r.Err)" }
    } elseif ($script:removeWallpaper) {
        Tv "rm -f $tvDir/wallpaper.jpg" | Out-Null
    }
    if ($script:slotNew.Count -or $script:slotRemove.Count) { Tv "mkdir -p $tvDir/wallpapers" | Out-Null }
    foreach ($k in @($script:slotNew.Keys)) {
        $jpg = Join-Path $work "$k.jpg"
        Convert-Wallpaper $script:slotNew[$k] $jpg
        Tv "rm -f $tvDir/wallpapers/$k.jpg $tvDir/wallpapers/$k.jpeg $tvDir/wallpapers/$k.png $tvDir/wallpapers/$k.webp" | Out-Null
        $r = Invoke-Adb "-s $script:dev push `"$jpg`" $tvDir/wallpapers/$k.jpg"
        if ($r.Code -ne 0) { throw "Không ghi được hình nền $($slotTitles[$k]): $($r.Err)" }
    }
    foreach ($k in @($script:slotRemove.Keys)) {
        Tv "rm -f $tvDir/wallpapers/$k.jpg $tvDir/wallpapers/$k.jpeg $tvDir/wallpapers/$k.png $tvDir/wallpapers/$k.webp" | Out-Null
    }
    # Mui gio: tat tu do roi dat tay, nhu chon trong Cai dat cua TV
    $tz = Tz-Of $cmbTz.Text
    if ($tz -and $tz -ne $script:tvTz) {
        if ($tz -notmatch '^[A-Za-z_]+(/[A-Za-z0-9_+-]+)+$|^UTC$') { throw "Múi giờ không hợp lệ: $tz (ví dụ Asia/Ho_Chi_Minh)" }
        Tv 'settings put global auto_time_zone 0' | Out-Null
        if ($script:sdk -ge 31) {
            Tv 'cmd time_zone_detector set_auto_detection_enabled false' | Out-Null
            Tv "cmd time_zone_detector suggest_manual_time_zone --zone_id $tz" | Out-Null
        }
        $now = (Tv 'getprop persist.sys.timezone').Trim()
        # Android 6 to 11: no time_zone_detector; AlarmManager.setTimeZone is binder call 3 on those versions
        if ($now -ne $tz) { Tv "service call alarm 3 s16 $tz" | Out-Null; $now = (Tv 'getprop persist.sys.timezone').Trim() }
        if ($now -ne $tz) { throw "TV không nhận múi giờ $tz (hiện là $now)." }
        $script:tvTz = $now
    }    Push-Revision
    Tv $restartCmd | Out-Null
    $script:wallpaperFile = $null; $script:removeWallpaper = $false
    $script:slotNew = @{}; $script:slotRemove = @{}
}

# ---------- Tu tim TV ----------
# Quet cong 5555 (ADB mang) tren cac mang IPv4 cua may tinh, roi hoi tung may ten model.
function Find-Tvs {
    $nets = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notmatch '^(127|169\.254)\.' -and $_.PrefixOrigin -in 'Dhcp', 'Manual' } |
        ForEach-Object { ($_.IPAddress -split '\.')[0..2] -join '.' } | Sort-Object -Unique)
    $probes = foreach ($n in $nets) {
        foreach ($i in 1..254) {
            $c = New-Object System.Net.Sockets.TcpClient
            [pscustomobject]@{ Ip = "$n.$i"; C = $c; R = $c.BeginConnect("$n.$i", 5555, $null, $null) }
        }
    }
    Start-Sleep -Milliseconds 1500
    $open = foreach ($p in $probes) { if ($p.R.IsCompleted -and $p.C.Connected) { $p.Ip }; $p.C.Close() }
    if (-not $open) { return @() }
    Start-AdbServer
    foreach ($ip in $open) {
        $dev = "${ip}:5555"
        Invoke-Adb "connect $dev" | Out-Null
        $st = Invoke-Adb "-s $dev get-state"
        $model = if ($st.Out -eq 'device') { (Invoke-Adb "-s $dev shell getprop ro.product.model").Out }
                 elseif ("$($st.Out) $($st.Err)" -match 'unauthorized') { '(bấm Cho phép trên TV)' } else { '(không trả lời)' }
        [pscustomobject]@{ Ip = $ip; Model = $model; Ready = ($st.Out -eq 'device') }
    }
}

# Tim TV va dien vao o chon; dung 1 TV san sang thi tu ket noi.
function Search-AndConnect {
    $lblStatus.Text = 'Đang tìm TV trên mạng…'; $lblStatus.ForeColor = [System.Drawing.Color]::DimGray; $form.Refresh()
    $found = @(Find-Tvs)
    $txtIp.Items.Clear()
    foreach ($t in $found) { $txtIp.Items.Add("$($t.Ip)  —  $($t.Model)") | Out-Null }
    $ready = @($found | Where-Object { $_.Ready })
    if ($found.Count -eq 0) {
        $lblStatus.Text = 'Không tìm thấy TV'; $lblStatus.ForeColor = [System.Drawing.Color]::Firebrick
        throw "Không tìm thấy TV nào bật gỡ lỗi mạng (ADB, cổng 5555) trong mạng Wi-Fi này.`nKiểm tra TV đã bật và cùng Wi-Fi, hoặc gõ IP của TV."
    }
    if ($ready.Count -eq 1) {
        $txtIp.Text = "$($ready[0].Ip)  —  $($ready[0].Model)"
        Connect-Tv $txtIp.Text
    } else {
        $txtIp.Text = $txtIp.Items[0]
        $lblStatus.Text = "Tìm thấy $($found.Count) thiết bị: chọn một rồi bấm Kết nối"
        $txtIp.DroppedDown = $true
    }
}

# ---------- Cai / tra launcher tren TV ----------
$apkFile = Join-Path $root 'tvhome.apk'
if (-not (Test-Path $apkFile)) { $apkFile = Join-Path $root 'launcher\app\build\outputs\apk\release\app-release.apk' }
$stockFile = '/data/local/tmp/cleantv-stock-launcher'
$knownStock = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher',
    'com.mitv.tvhome', 'com.xgimi.home')
$homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
$script:sdk = 99
# Android 6 (API 23) has no 'cmd package' (it arrived in Android 7): no shell way to read or set the default
# Home there. Home is then found by starting it, and set by disabling the stock launcher.
function Is-OldAndroid { $script:sdk -lt 24 }

function Get-HomeComponent([string]$forPkg = '') {
    if (Is-OldAndroid) {
        $p = if ($forPkg) { " -p $forPkg" } else { '' }
        $out = Tv "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME$p"
        $m = [regex]::Match($out, 'Activity:\s*(\S+)')
        if ($m.Success) { return $m.Groups[1].Value }
        return ''
    }
    ((Tv "$homeQuery $forPkg") -split "`n")[-1].Trim()
}
function Set-HomeActivity([string]$component) {
    if (-not (Is-OldAndroid) -and $component) { Tv "cmd package set-home-activity --user 0 $component" | Out-Null }
}
function Get-HomePkg { (Get-HomeComponent).Split('/')[0] }

function Set-LauncherState([bool]$installed) {
    $script:hasLauncher = $installed
    foreach ($c in $editors) { $c.Enabled = $installed }
    $btnInstall.Enabled = $true; $btnRestore.Enabled = $true
    $btnInstall.Text = if ($installed) { 'Cập nhật launcher' } else { 'Cài launcher lên TV' }
}

function Install-LauncherToTv {
    if (-not (Test-Path $apkFile)) { throw "Thiếu file launcher: $apkFile" }
    # Launcher dang giu Home: cai nay se tat no sau khi launcher moi chay duoc
    $before = Get-HomePkg
    $stock = $null
    if ($before -eq 'com.google.android.tungsten.setupwraith') {
        throw "Màn hình chính đang do trình thiết lập Google TV (setupwraith) giữ. Tắt nó có thể làm TV mất Wi-Fi; xem examples\ trong bộ CleanTV trước."
    } elseif ($before -eq 'android') {
        $claim = if (Is-OldAndroid) { (Tv 'pm list packages -e') -replace 'package:(\S+)', '$1/' } else { Tv 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' }
        $found = @($knownStock | Where-Object { $claim -match ([regex]::Escape($_) + '/') })
        if ($found.Count -eq 1) { $stock = $found[0] }
    } elseif ($before -ne $pkg) { $stock = $before }

    $lblHint.Text = 'Đang chép launcher lên TV…'; $form.Refresh()
    $r = Invoke-Adb "-s $script:dev push `"$apkFile`" /data/local/tmp/tvhome.apk"
    if ($r.Code -ne 0) { throw "Không chép được APK: $($r.Err)" }
    # pm install qua shell: 'adb install' tren Android 14 co the treo cho xac nhan tren man hinh
    $out = Tv 'pm install -r /data/local/tmp/tvhome.apk'
    Tv 'rm -f /data/local/tmp/tvhome.apk' | Out-Null
    if ($out -notmatch 'Success') {
        $why = if ($out -match 'VERIFICATION') { "`nTV này chặn mọi ứng dụng cài ngoài; không thể thay launcher." } else { '' }
        throw "Cài launcher thất bại: $out$why"
    }
    Set-HomeActivity "$pkg/.MainActivity"
    $start = Tv "am start -W -n $pkg/.MainActivity"
    if ($start -notmatch 'Status: ok') { throw "Launcher không khởi động được; launcher gốc được giữ nguyên.`n$start" }
    $lblHint.Text = 'Đang tối ưu launcher (có thể mất một phút)…'; $form.Refresh()
    Tv "pm grant $pkg android.permission.ACCESS_FINE_LOCATION" | Out-Null
    Tv "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS" | Out-Null
    Tv "pm grant $pkg android.permission.RECORD_AUDIO" | Out-Null   # nut Tim kiem giong noi
    # Phim micro tren remote mo tim kiem giong noi tu moi ung dung: bat InputKeyService (giu cac dich vu khac)
    Tv "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow" | Out-Null
    $acc = (Tv 'settings get secure enabled_accessibility_services').Trim()
    if ($acc -notmatch 'InputKeyService') {
        $acc = if ($acc -and $acc -ne 'null') { "${acc}:$pkg/.InputKeyService" } else { "$pkg/.InputKeyService" }
        Tv "settings put secure enabled_accessibility_services $acc" | Out-Null
        Tv 'settings put secure accessibility_enabled 1' | Out-Null
    }
    # Android 6 compiles apps ahead of time at install; "cmd package compile" exists from Android 7
    if (-not (Is-OldAndroid)) { Tv "cmd package compile -m speed -f $pkg" | Out-Null }

    $disabled = $null
    # Giao dien XGIMI (com.xgimi.home) luon giu bat: cac chuc nang XGIMI goi den no, tat di gay loi
    if ($stock -and $stock -ne 'com.xgimi.home') {
        $ans = [System.Windows.Forms.MessageBox]::Show("Tắt launcher gốc ($stock) để nút Home luôn mở launcher mới?`nCó thể bật lại bằng nút ""Trả launcher gốc"".", 'Launcher gốc', 'YesNo', 'Question')
        if ($ans -eq 'Yes') {
            Tv "pm disable-user --user 0 $stock" | Out-Null
            Tv "echo $stock > $stockFile" | Out-Null
            $disabled = $stock
        }
    }
    Set-HomeActivity "$pkg/.MainActivity"
    $after = Get-HomePkg
    if ($after -eq 'android' -and (Is-OldAndroid)) {
        # Android 6 with two launchers left and no default: the TV asks once which one to use
        [System.Windows.Forms.MessageBox]::Show("TV đang hỏi chọn màn hình chính. Trên TV, chọn launcher mới rồi chọn ""Luôn luôn"" (Always), sau đó bấm OK ở đây.", 'Chọn màn hình chính', 'OK', 'Information') | Out-Null
        $after = Get-HomePkg
    }
    if ($after -ne $pkg) {
        if ($disabled) {
            Tv "pm enable --user 0 $disabled" | Out-Null
            Set-HomeActivity (Get-HomeComponent $disabled)
        }
        throw "Đã cài launcher nhưng nút Home vẫn mở '$after'; launcher gốc đã được bật lại."
    }
    Tv 'input keyevent KEYCODE_HOME' | Out-Null
}

function Restore-StockLauncher {
    $stock = (Tv "cat $stockFile 2>/dev/null").Trim()
    if (-not $stock) {
        $off = @((Tv 'pm list packages -d') -split "`n" | ForEach-Object { $_.Trim() })
        $stock = $knownStock | Where-Object { $off -contains "package:$_" } | Select-Object -First 1
    }
    $what = if ($stock) { "bật lại launcher gốc ($stock) và gỡ launcher mới" } else { 'gỡ launcher mới (không tìm thấy launcher gốc bị tắt)' }
    $ans = [System.Windows.Forms.MessageBox]::Show("Sẽ $what khỏi TV. Tiếp tục?", 'Trả launcher gốc', 'YesNo', 'Question')
    if ($ans -ne 'Yes') { return $false }
    if ($stock) {
        Tv "pm enable --user 0 $stock" | Out-Null
        Set-HomeActivity (Get-HomeComponent $stock)
    }
    $out = Tv "pm uninstall $pkg"
    Tv "rm -f $stockFile" | Out-Null
    Tv 'input keyevent KEYCODE_HOME' | Out-Null
    if ($out -notmatch 'Success') { throw "Không gỡ được launcher: $out" }
    $true
}

function Busy([scriptblock]$work) {
    $form.Cursor = 'WaitCursor'; $form.Enabled = $false
    try { & $work } catch { [System.Windows.Forms.MessageBox]::Show("$_", 'Lỗi', 'OK', 'Error') | Out-Null }
    finally { $form.Enabled = $true; $form.Cursor = 'Default' }
}

# ---------- Su kien ----------
function Connect-Tv([string]$text) {
        if (-not $script:adb) { throw 'Không tìm thấy adb.exe (tools\platform-tools\adb.exe).' }
        $ip = ([regex]::Match($text, '\d{1,3}(\.\d{1,3}){3}(:\d+)?')).Value
        if (-not $ip) { throw 'Nhập địa chỉ IP của TV, hoặc bấm "Tự tìm".' }
        $plainIp = $ip.Split(':')[0]
        if ($ip -notmatch ':') { $ip = "${ip}:5555" }
        $lblStatus.Text = "Đang kết nối $ip…"; $form.Refresh()
        Start-AdbServer
        $r = Invoke-Adb "connect $ip"
        $state = (Invoke-Adb "-s $ip get-state").Out
        if ($state -ne 'device') {
            $lblStatus.Text = 'Không kết nối được'; $lblStatus.ForeColor = [System.Drawing.Color]::Firebrick
            throw "Không kết nối được $ip.`n$($r.Out) $($r.Err)`nNếu TV hỏi ""Cho phép gỡ lỗi USB?"", hãy chọn Cho phép rồi bấm Kết nối lại."
        }
        $script:dev = $ip
        Set-Content -Path $lastIpFile -Value $plainIp -Encoding ascii -ErrorAction SilentlyContinue
        $model = Tv 'getprop ro.product.model'
        $sdkText = (Tv 'getprop ro.build.version.sdk').Trim()
        $script:sdk = if ($sdkText -match '^\d+$') { [int]$sdkText } else { 99 }
        if ($script:sdk -lt 23) { throw "TV chạy Android quá cũ (API $script:sdk). Launcher cần Android 6 trở lên." }
        $lblStatus.Text = "Đã kết nối: $model ($ip)"; $lblStatus.ForeColor = [System.Drawing.Color]::ForestGreen
        if (-not (Tv "pm path $pkg")) {
            Set-LauncherState $false
            $lblHint.Text = 'TV chưa có launcher. Bấm "Cài launcher lên TV".'
            $ans = [System.Windows.Forms.MessageBox]::Show("TV này chưa có launcher. Cài ngay?", 'Cài launcher', 'YesNo', 'Question')
            if ($ans -ne 'Yes') { return }
            Install-LauncherToTv
            $lblHint.Text = 'Đã cài launcher. Giờ có thể chỉnh giao diện.'
        }
        Load-FromTv
        Set-LauncherState $true
}

$btnConnect.Add_Click({ Busy { Connect-Tv $txtIp.Text } })
$btnFind.Add_Click({ Busy { Search-AndConnect } })

$btnPick.Add_Click({
    $dlg = New-Object System.Windows.Forms.OpenFileDialog
    $dlg.Filter = 'Ảnh (*.jpg;*.jpeg;*.png;*.bmp;*.webp)|*.jpg;*.jpeg;*.png;*.bmp;*.webp'
    if ($dlg.ShowDialog() -eq 'OK') {
        try {
            Show-Wallpaper $dlg.FileName ("Ảnh mới: " + [System.IO.Path]::GetFileName($dlg.FileName))
            $script:wallpaperFile = $dlg.FileName; $script:removeWallpaper = $false
        } catch { [System.Windows.Forms.MessageBox]::Show("Không mở được ảnh này: $_") | Out-Null }
    }
})
$btnNoWall.Add_Click({
    $script:wallpaperFile = $null; $script:removeWallpaper = $true
    Show-Wallpaper $null 'Sẽ bỏ hình nền khi áp dụng'
})

$btnApply.Add_Click({
    Busy {
        Apply-ToTv
        $lblHint.Text = 'Đã áp dụng lúc ' + (Get-Date -Format 'HH:mm:ss') + '. Bấm "Xem màn hình TV" để kiểm tra.'
    }
})

$btnReset.Add_Click({
    $ans = [System.Windows.Forms.MessageBox]::Show('Xóa cấu hình và hình nền đã đẩy lên TV, quay về giao diện gốc của launcher?', 'Về mặc định', 'YesNo', 'Question')
    if ($ans -ne 'Yes') { return }
    Busy {
        Tv "rm -f $tvDir/config.json $tvDir/wallpaper.jpg" | Out-Null
        Push-Revision
        Tv $restartCmd | Out-Null
        Load-FromTv
        $lblHint.Text = 'Đã về mặc định.'
    }
})

$btnInstall.Add_Click({
    Busy {
        Install-LauncherToTv
        Load-FromTv
        Set-LauncherState $true
        $lblHint.Text = 'Đã cài / cập nhật launcher lúc ' + (Get-Date -Format 'HH:mm:ss') + '.'
    }
})

$btnRestore.Add_Click({
    Busy {
        if (Restore-StockLauncher) {
            Set-LauncherState $false
            $lblHint.Text = 'Đã trả launcher gốc.'
        }
    }
})

$btnShot.Add_Click({
    Busy {
        $ms = Get-Screencap
        if ($ms.Length -lt 1000) { throw 'TV trả về ảnh trống (thường gặp khi đang xem HDMI / Live TV). Bấm Home rồi thử lại.' }
        $img = [System.Drawing.Image]::FromStream($ms)
        $f = New-Object System.Windows.Forms.Form
        $f.Text = 'Màn hình TV'; $f.ClientSize = New-Object System.Drawing.Size(960, 540); $f.StartPosition = 'CenterParent'
        $pb = New-Object System.Windows.Forms.PictureBox
        $pb.Dock = 'Fill'; $pb.SizeMode = 'Zoom'; $pb.Image = $img; $pb.BackColor = [System.Drawing.Color]::Black
        $f.Controls.Add($pb)
        $f.Add_FormClosed({ $img.Dispose() })
        $f.ShowDialog($form) | Out-Null
    }
})

# ---------- Khoi dong ----------
if ($TvIp) { $txtIp.Text = $TvIp }
elseif (Test-Path $lastIpFile) { $txtIp.Text = (Get-Content $lastIpFile -TotalCount 1).Trim() }
$form.AcceptButton = $btnConnect
# Mo len: thu IP da nho; khong co hoac khong ket noi duoc thi tu tim TV tren mang
$form.Add_Shown({
    Busy {
        $ok = $false
        if ($txtIp.Text) { try { Connect-Tv $txtIp.Text; $ok = $true } catch { } }
        if (-not $ok) { Search-AndConnect }
    }
})
[System.Windows.Forms.Application]::Run($form)
