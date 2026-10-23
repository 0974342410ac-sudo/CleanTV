# [BAN DA GIAI MA] Noi dung giong het ban ma hoa trong JustClick.ps1, chi bo lop ma hoa/kiem tra hash.
# Cac cho de sua danh sach app:
#   - Debloat-Tv        : duong dan debloat.txt (examples\...\debloat.txt), moi dong 1 ten goi
#   - Get-Dock-App-Label: ten hien thi cua cac app trong Dock
#   - Install-Launcher  : $currentDock (4 app Dock mac dinh) va danh sach app de chon
#   - Remove-App        : $protected (cac goi he thong khong cho go)
# Chay: powershell -ExecutionPolicy Bypass -File .\JustClick_readable.ps1
# ==============================================================================
# Bo cong cu toi uu & cai dat Apple TV Launcher cho moi Android TV
# Tuong thich: Sony, Xiaomi, TCL, Hisense, Casper, Coocaa, Chromecast, Onn, v.v.
# ==============================================================================

param(
    [string]$TvIp = ""
)

$Host.UI.RawUI.WindowTitle = "Android TV"

# Xac dinh thu muc goc repoRoot
$repoRoot = if ($global:repoRoot -and (Test-Path $global:repoRoot)) {
    $global:repoRoot
} elseif ($PSScriptRoot -and (Test-Path $PSScriptRoot)) {
    $PSScriptRoot
} elseif ($MyInvocation.MyCommand.Path) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
} else {
    (Get-Location).Path
}

# Xac dinh duong dan ADB
$script:adb = ""
$bundledAdb = Join-Path $repoRoot "tools\platform-tools\adb.exe"
if (Test-Path $bundledAdb) {
    $script:adb = $bundledAdb
} else {
    $cmdAdb = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmdAdb) {
        $script:adb = $cmdAdb.Source
    } else {
        $altAdb = Join-Path (Get-Location).Path "tools\platform-tools\adb.exe"
        if (Test-Path $altAdb) {
            $script:adb = $altAdb
        } else {
            Write-Host "Dang tu dong tai cong cu ADB tu Google (chuyen dung cho may moi)..." -ForegroundColor Cyan
            $zipUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
            $zipDest = Join-Path $repoRoot "tools\platform-tools.zip"
            $toolsDir = Join-Path $repoRoot "tools"
            New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $zipUrl -OutFile $zipDest
            Expand-Archive -Path $zipDest -DestinationPath $toolsDir -Force
            Remove-Item $zipDest -Force
            if (Test-Path $bundledAdb) { $script:adb = $bundledAdb } else { $script:adb = "adb" }
        }
    }
}
$global:adb = $script:adb
$adb = $script:adb

function Show-Header {
    Clear-Host
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "          BO CONG CU TOI UU HOA ANDROID TV            " -ForegroundColor Yellow
    Write-Host "     Toi uu muot ma - Tiet kiem RAM - Giao dien Apple TV 4K       " -ForegroundColor Green
    Write-Host "==================================================================" -ForegroundColor Cyan
    if ($script:CurrentTv) {
        Write-Host " [TV Hien Tai]: $($script:CurrentTv) (Model: $($script:TvModel))" -ForegroundColor Magenta
        Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    }
}

function Connect-Tv {
    param([string]$ip)
    if (-not $ip) {
        $ip = Read-Host "Nhap dia chi IP cua TV (vi du: 192.168.2.21, hoac 0 de quay lai)"
    }
    $ip = $ip.Trim()
    if ($ip.Contains(':')) { $ip = $ip.Split(':')[0].Trim() }
    if (-not $ip -or $ip -eq "0" -or $ip -eq "b") { return $false }

    Write-Host "`nDang ket noi den $($ip):5555..." -ForegroundColor Cyan
    $res = & $script:adb connect "$($ip):5555"
    Write-Host $res
    Start-Sleep -Milliseconds 200
    
    $model = (& $script:adb -s "$($ip):5555" shell "getprop ro.product.model").Trim()
    $androidVer = (& $script:adb -s "$($ip):5555" shell "getprop ro.build.version.release").Trim()
    $mfr = (& $script:adb -s "$($ip):5555" shell "getprop ro.product.manufacturer").Trim()

    if ($model) {
        $script:CurrentTv = "$($ip):5555"
        $script:TvIpOnly = $ip
        $script:TvModel = "$mfr $model (Android $androidVer)"
        Write-Host "`n[Thanh Cong] Da ket noi: $($script:TvModel)" -ForegroundColor Green
        return $true
    } else {
        Write-Host "`n[Loi] Khong the giao tiep qua ADB voi $($ip):5555." -ForegroundColor Red
        Write-Host "Hay chac chan rang:" -ForegroundColor Yellow
        Write-Host " 1. TV va may tinh dang cung mang Wi-Fi hoac day LAN."
        Write-Host " 2. Da bat 'Go loi USB' (USB Debugging) tren TV."
        Write-Host " 3. Da tich 'Luon cho phep' va bam 'Cho phep / Allow' tren man hinh TV."
        return $false
    }
}

function Scan-And-Connect-Tv {
    Show-Header
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "                 SCAN TV TRONG MANG & KET NOI                     " -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host " 1. Quet tu dong TV trong mang LAN / Wi-Fi (cong 5555)"
    Write-Host " 2. Nhap truc tiep dia chi IP cua TV (Direct IP)"
    Write-Host " 0. Quay lai menu chinh"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $mode = Read-Host "Chon che do (1-2, hoac 0 de quay lai menu, Enter de tu dong quet)"
    if ($mode -eq "0" -or $mode -eq "b") { return }
    if ($mode -eq "2") {
        Connect-Tv
        return
    }

    Write-Host "`nDang quet tim cac TV Android tren mang noi bo (cong 5555)..." -ForegroundColor Cyan

    $localIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { 
        $_.InterfaceAlias -notmatch 'Loopback|vEthernet' -and 
        $_.IPAddress -notlike '169.254*' -and 
        $_.IPAddress -ne '127.0.0.1' 
    } | Select-Object -First 1).IPAddress

    $foundIps = @()
    if ($localIp) {
        $subnetPrefix = $localIp.Substring(0, $localIp.LastIndexOf('.'))
        Write-Host "Dai mang: $subnetPrefix.* (IP may tinh: $localIp)" -ForegroundColor Gray

        # 1. Kiem tra nhanh cac IP trong bang ARP
        $arpIps = @(arp -a | ForEach-Object { if ($_ -match '^\s*([0-9.]+)\s+') { $Matches[1] } } | Where-Object { 
            $_ -like "$subnetPrefix.*" -and $_ -notmatch '(\.255$|\.0$)' 
        } | Sort-Object -Unique)

        foreach ($ip in $arpIps) {
            try {
                $tcp = [System.Net.Sockets.TcpClient]::new()
                $ar = $tcp.BeginConnect($ip, 5555, $null, $null)
                if ($ar.AsyncWaitHandle.WaitOne(450, $false) -and $tcp.Connected) {
                    $foundIps += $ip
                }
                $tcp.Close()
            } catch {}
        }

        # 2. Neu chua thay, broadcast ping de kich hoat ARP
        if ($foundIps.Count -eq 0) {
            Start-Process ping -ArgumentList "-n 1 -w 150 $subnetPrefix.255" -WindowStyle Hidden -Wait
            $arpIps2 = @(arp -a | ForEach-Object { if ($_ -match '^\s*([0-9.]+)\s+') { $Matches[1] } } | Where-Object { 
                $_ -like "$subnetPrefix.*" -and $_ -notmatch '(\.255$|\.0$)' 
            } | Sort-Object -Unique)

            foreach ($ip in $arpIps2) {
                if ($arpIps -notcontains $ip) {
                    try {
                        $tcp = [System.Net.Sockets.TcpClient]::new()
                        $ar = $tcp.BeginConnect($ip, 5555, $null, $null)
                        if ($ar.AsyncWaitHandle.WaitOne(450, $false) -and $tcp.Connected) {
                            $foundIps += $ip
                        }
                        $tcp.Close()
                    } catch {}
                }
            }
        }
    }

    if ($foundIps.Count -gt 0) {
        Write-Host "`n[Tim thay $($foundIps.Count) TV Android trong mang]:" -ForegroundColor Green
        for ($i = 0; $i -lt $foundIps.Count; $i++) {
            $tvIp = $foundIps[$i]
            & $script:adb connect "$($tvIp):5555" | Out-Null
            $model = (& $script:adb -s "$($tvIp):5555" shell "getprop ro.product.model").Trim()
            $mfr = (& $script:adb -s "$($tvIp):5555" shell "getprop ro.product.manufacturer").Trim()
            $desc = if ($model) { "$mfr $model" } else { "Android TV" }
            Write-Host (" [{0}] {1} - {2}" -f ($i + 1), $tvIp, $desc) -ForegroundColor Yellow
        }
        Write-Host " [m] Nhap dia chi IP khac thu cong" -ForegroundColor Gray
        Write-Host " [0] Quay lai menu chinh" -ForegroundColor Gray
        $pick = Read-Host "`nChon so thu tu TV muon ket noi (1-$($foundIps.Count), m=Nhap IP, 0=Quay lai, Enter = chon 1)"
        if ($pick -eq "0" -or $pick -eq "b") { return }
        if ($pick -eq "m" -or $pick -eq "M") { Connect-Tv; return }
        if (-not $pick) { $pick = "1" }
        
        $choiceNum = 0
        if ([int]::TryParse($pick, [ref]$choiceNum) -and $choiceNum -ge 1 -and $choiceNum -le $foundIps.Count) {
            $chosenIp = $foundIps[$choiceNum - 1]
            Connect-Tv $chosenIp
            return
        }
    } else {
        Write-Host "`nKhong tu dong tim thay TV nao dang mo cong 5555 tren mang." -ForegroundColor Yellow
        Write-Host "Luu y: TV phai bat 'Go loi USB' (USB debugging) trong Cai dat TV." -ForegroundColor Gray
        Write-Host " 1. Nhap dia chi IP thu cong"
        Write-Host " 0. Quay lai menu chinh"
        $manual = Read-Host "Chon (1 de nhap IP, 0 de quay lai menu)"
        if ($manual -eq "0" -or $manual -eq "b" -or $manual -eq "n") { return }
        Connect-Tv
    }
}

function Backup-Tv {
    if (-not $script:CurrentTv) { Write-Host "Vui long ket noi TV truoc!" -ForegroundColor Yellow; return }
    Show-Header
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "                   SAO LUU HE THONG TV (BACKUP)                   " -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host " 1. Bat dau sao luu toan bo he thong TV (packages, props, screensaver)"
    Write-Host " 0. Quay lai menu chinh"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $choice = Read-Host "Nhap lua chon (1 de sao luu, 0 de quay lai menu)"
    if ($choice -ne "1") { return }

    $folderName = ($script:TvModel -replace '[^A-Za-z0-9_-]', '_').Trim('_')
    $backupDir = Join-Path $repoRoot "backup\$folderName"
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

    Write-Host "`nDang sao luu he thong TV vao: $backupDir ..." -ForegroundColor Cyan
    & $script:adb -s $script:CurrentTv shell "pm list packages -f" > "$backupDir\packages-full.txt"
    & $script:adb -s $script:CurrentTv shell "pm list packages -e" > "$backupDir\packages-enabled.txt"
    & $script:adb -s $script:CurrentTv shell "pm list packages -d" > "$backupDir\packages-disabled.txt"
    & $script:adb -s $script:CurrentTv shell "getprop" > "$backupDir\getprop.txt"
    & $script:adb -s $script:CurrentTv shell "settings list global" > "$backupDir\settings-global.txt"
    & $script:adb -s $script:CurrentTv shell "settings list secure" > "$backupDir\settings-secure.txt"
    & $script:adb -s $script:CurrentTv shell "settings list system" > "$backupDir\settings-system.txt"
    & $script:adb -s $script:CurrentTv shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME" > "$backupDir\home-activities.txt"
    & $script:adb -s $script:CurrentTv shell screencap -p /data/local/tmp/backup_screen.png
    & $script:adb -s $script:CurrentTv pull /data/local/tmp/backup_screen.png "$backupDir\screen_before.png"

    Write-Host "[Xong] Da hoan tat sao luu an toan 100%!" -ForegroundColor Green
}

function Debloat-Tv {
    if (-not $script:CurrentTv) { Write-Host "Vui long ket noi TV truoc!" -ForegroundColor Yellow; return }
    Show-Header
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "             TOI UU & DON DEP UNG DUNG RAC (DEBLOAT)              " -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "Chon cau hinh Debloat phu hop:" -ForegroundColor Cyan
    Write-Host " 1. Sony Bravia (VU1, X-Series, Google TV / Android TV)"
    Write-Host " 2. Xiaomi TV / Mi Box (Mi TV 4A, Box S, PatchWall)"
    Write-Host " 3. TCL TV (BeyondTV, C-Series, P-Series)"
    Write-Host " 4. Chon file debloat.txt tuy chinh khac tu thu muc examples/"
    Write-Host " 0. Quay lai menu chinh"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $choice = Read-Host "Nhap lua chon (1-4, hoac 0 de quay lai menu)"
    if ($choice -eq "0" -or $choice -eq "b" -or -not $choice) { return }

    $listPath = ""
    switch ($choice) {
        "1" { $listPath = Join-Path $repoRoot "examples\sony-bravia-vu1\debloat.txt" }
        "2" { $listPath = Join-Path $repoRoot "examples\xiaomi-mi-tv-4a\debloat.txt" }
        "3" { $listPath = Join-Path $repoRoot "examples\tcl-43c645\debloat.txt" }
        "4" {
            $custom = Read-Host "Nhap duong dan (vi du: examples/sony-bravia-4k-gb/debloat.txt, hoac 0 de quay lai)"
            if ($custom -eq "0" -or $custom -eq "b" -or -not $custom) { return }
            $listPath = Join-Path $repoRoot $custom
        }
    }

    if (-not (Test-Path $listPath)) {
        Write-Host "Khong tim thay file danh sach: $listPath" -ForegroundColor Red
        return
    }

    $confirm = Read-Host "`nXac nhan ap dung debloat tu $listPath? (y de dong y, 0 de quay lai menu)"
    if ($confirm -ne 'y') { return }

    Write-Host "`nDang ap dung danh sach: $listPath" -ForegroundColor Cyan
    $pkgs = Get-Content $listPath | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ } | ForEach-Object { ($_ -split '\s+')[0] }
    
    foreach ($pkg in $pkgs) {
        $out = (& $script:adb -s $script:CurrentTv shell "pm disable-user --user 0 $pkg").Trim()
        Write-Host " - Tat $pkg : $out" -ForegroundColor Gray
    }
    Write-Host "`n[Thanh cong] Da don dep cac ung dung rac & dich vu chay ngam!" -ForegroundColor Green
}

function Get-Dock-App-Label {
    param([string]$pkg)
    $names = @{
        "com.viettel.tv360.tv"          = "TV360"
        "com.google.android.youtube.tv" = "YouTube"
        "com.netflix.ninja"             = "Netflix"
        "org.xbmc.kodi"                 = "Kodi"
        "net.fptplay.ottbox"            = "FPT Play"
        "com.teamsmart.videomanager.tv" = "SmartTube"
        "com.monster.tv"                = "Monster TV"
        "com.vieon"                     = "VieON"
        "com.vtvgo.client"              = "VTV Go"
        "vn.vtv.vtvgo"                  = "VTV Go"
        "com.yablio.sendfilestotv"      = "Send Files to TV"
        "nextapp.fx"                    = "FX File Explorer"
        "com.boost.airplay.receiver"    = "AirPlay Receiver"
        "com.android.vending"           = "CH Play Store"
        "com.frostnerd.dnschanges"      = "DNS Changer"
    }
    if ($names.ContainsKey($pkg)) { return $names[$pkg] }
    return $pkg
}

function Install-Launcher {
    if (-not $script:CurrentTv) { Write-Host "Vui long ket noi TV truoc!" -ForegroundColor Yellow; return }
    Show-Header
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "       CAI DAT APPLE TV LAUNCHER & SAP XEP 4 APP THANH DOCK       " -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Cyan

    # 1. Doc danh sach 4 dock apps hien tai
    $currentDock = @("com.viettel.tv360.tv", "com.google.android.youtube.tv", "com.netflix.ninja", "org.xbmc.kodi")
    try {
        $rawJson = (& $script:adb -s $script:CurrentTv shell "cat /sdcard/Android/data/com.gathin.tvhome/files/config.json 2>/dev/null").Trim()
        if ($rawJson -and $rawJson -match '\{') {
            $parsed = $rawJson | ConvertFrom-Json -ErrorAction SilentlyContinue
            if ($parsed -and $parsed.dock_apps -and $parsed.dock_apps.Count -ge 4) {
                $currentDock = @($parsed.dock_apps[0..3])
            }
        }
    } catch {}

    Write-Host "Thu tu 4 ung dung Dock hien tai:" -ForegroundColor Green
    for ($i = 0; $i -lt 4; $i++) {
        $pkg = $currentDock[$i]
        $label = Get-Dock-App-Label $pkg
        Write-Host ("  [{0}] {1,-14} ({2})" -f ($i + 1), $label, $pkg) -ForegroundColor Yellow
    }
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host " 1. Giu nguyen 4 ung dung Dock nhu tren va tien hanh cai dat"
    Write-Host " 2. Sap xep lai vi tri (doi thu tu 1, 2, 3, 4 giua 4 app tren)"
    Write-Host " 3. Tuy chon 4 app Dock tu danh sach ung dung da cai tren TV"
    Write-Host " 0. Quay lai menu chinh"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $dockChoice = Read-Host "Chon thao tac (1-3, Enter = 1, 0 de quay lai menu)"
    if ($dockChoice -eq "0" -or $dockChoice -eq "b") { return }

    $targetDock = @($currentDock)

    if ($dockChoice -eq "2") {
        Write-Host "`nNhap thu tu vi tri moi bang cach go cac so 1,2,3,4." -ForegroundColor Cyan
        Write-Host "Vi du: go '2,1,4,3' de dua app so 2 len vi tri 1..." -ForegroundColor Gray
        $orderInput = Read-Host "Nhap thu tu moi (vi du: 2,1,4,3 hoac 0 de quay lai)"
        if ($orderInput -eq "0" -or $orderInput -eq "b" -or -not $orderInput) { return }

        $orderNums = @($orderInput -split '[, ]+' | ForEach-Object { 
            $n = 0
            if ([int]::TryParse($_.Trim(), [ref]$n)) { $n }
        } | Where-Object { $_ -ge 1 -and $_ -le 4 })

        if ($orderNums.Count -eq 4) {
            $targetDock = @(
                $currentDock[$orderNums[0] - 1],
                $currentDock[$orderNums[1] - 1],
                $currentDock[$orderNums[2] - 1],
                $currentDock[$orderNums[3] - 1]
            )
        } else {
            Write-Host "[Loi] Ban phai nhap du 4 vi tri tu 1 den 4 (vi du: 2,1,4,3)!" -ForegroundColor Red
            return
        }
    } elseif ($dockChoice -eq "3") {
        Write-Host "`nDang lay danh sach ung dung tren TV..." -ForegroundColor Cyan
        $allApps = @()
        $candidates = @(
            "com.viettel.tv360.tv",
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "org.xbmc.kodi",
            "net.fptplay.ottbox",
            "com.teamsmart.videomanager.tv",
            "com.monster.tv",
            "com.vieon",
            "com.vtvgo.client",
            "vn.vtv.vtvgo",
            "com.yablio.sendfilestotv",
            "nextapp.fx",
            "com.boost.airplay.receiver",
            "com.android.vending"
        )
        $installedList = (& $script:adb -s $script:CurrentTv shell "pm list packages").Trim() -split "`n" | ForEach-Object { ($_ -replace '^package:', '').Trim() }
        foreach ($c in $candidates) {
            if ($installedList -contains $c) { $allApps += $c }
        }
        $thirdParty = (& $script:adb -s $script:CurrentTv shell "pm list packages -3").Trim() -split "`n" | ForEach-Object { ($_ -replace '^package:', '').Trim() }
        foreach ($p in $thirdParty) {
            if ($p -and $p -ne "com.gathin.tvhome" -and ($allApps -notcontains $p)) {
                $allApps += $p
            }
        }

        if ($allApps.Count -lt 4) {
            Write-Host "Khong tim du 4 ung dung kha dung tren TV." -ForegroundColor Yellow
            return
        }

        Write-Host "`nDanh sach ung dung kha dung tren TV:" -ForegroundColor Yellow
        for ($i = 0; $i -lt $allApps.Count; $i++) {
            $p = $allApps[$i]
            $lbl = Get-Dock-App-Label $p
            Write-Host (" [{0,2}] {1,-16} ({2})" -f ($i + 1), $lbl, $p)
        }
        Write-Host " [ 0] Quay lai menu chinh" -ForegroundColor Gray

        $pickInput = Read-Host "`nNhap 4 so thu tu ung dung muon ghim vao Dock (vi du: 1,2,5,4 hoac 0 de quay lai)"
        if ($pickInput -eq "0" -or $pickInput -eq "b" -or -not $pickInput) { return }

        $pickNums = @($pickInput -split '[, ]+' | ForEach-Object { 
            $n = 0
            if ([int]::TryParse($_.Trim(), [ref]$n)) { $n }
        } | Where-Object { $_ -ge 1 -and $_ -le $allApps.Count })

        if ($pickNums.Count -eq 4) {
            $targetDock = @(
                $allApps[$pickNums[0] - 1],
                $allApps[$pickNums[1] - 1],
                $allApps[$pickNums[2] - 1],
                $allApps[$pickNums[3] - 1]
            )
        } else {
            Write-Host "[Loi] Ban phai chon dung 4 ung dung (vi du: 1,2,3,5)!" -ForegroundColor Red
            return
        }
    }

    Write-Host "`nThu tu 4 ung dung Dock se duoc ap dung:" -ForegroundColor Green
    for ($i = 0; $i -lt 4; $i++) {
        $p = $targetDock[$i]
        $lbl = Get-Dock-App-Label $p
        Write-Host ("  Vi tri {0}: {1} ({2})" -f ($i + 1), $lbl, $p) -ForegroundColor Cyan
    }

    $apk = Join-Path $repoRoot "prebuilt\tvhome-release.apk"
    if (-not (Test-Path $apk)) {
        $apk = Join-Path $repoRoot "launcher\app\build\outputs\apk\release\app-release.apk"
    }

    if (-not (Test-Path $apk)) {
        Write-Host "Chua tim thay APK prebuilt. Tien hanh bien dich..." -ForegroundColor Yellow
        $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
        Set-Location (Join-Path $repoRoot "launcher")
        & .\gradlew.bat assembleRelease --console=plain
        Set-Location $repoRoot
    }

    Write-Host "`nDang cai dat / cap nhat Apple TV Launcher len TV..." -ForegroundColor Cyan
    & $script:adb -s $script:CurrentTv install -r $apk
    & $script:adb -s $script:CurrentTv shell "cmd package compile -m speed -f com.gathin.tvhome" | Out-Null
    & $script:adb -s $script:CurrentTv shell "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity" | Out-Null

    # Tao config.json va push vao TV
    $dockJsonObj = @{
        "dock_apps" = @($targetDock)
        "columns" = 4
    }
    $jsonStr = $dockJsonObj | ConvertTo-Json -Compress
    $tmpJsonPath = Join-Path $repoRoot "tools\config.json"
    [IO.File]::WriteAllText($tmpJsonPath, $jsonStr, [Text.Encoding]::UTF8)

    Write-Host "Dang dong bo cau hinh thanh Dock len TV..." -ForegroundColor Cyan
    & $script:adb -s $script:CurrentTv shell "mkdir -p /sdcard/Android/data/com.gathin.tvhome/files"
    & $script:adb -s $script:CurrentTv push $tmpJsonPath "/sdcard/Android/data/com.gathin.tvhome/files/config.json" | Out-Null
    $rev = [guid]::NewGuid().ToString('N')
    & $script:adb -s $script:CurrentTv shell "printf '%s' '$rev' > /sdcard/Android/data/com.gathin.tvhome/files/theme_revision"
    Remove-Item $tmpJsonPath -Force -ErrorAction SilentlyContinue

    # Khoi dong lai launcher de nhan dien ngay
    & $script:adb -s $script:CurrentTv shell "am force-stop com.gathin.tvhome"
    & $script:adb -s $script:CurrentTv shell "am start -W -n com.gathin.tvhome/.MainActivity" | Out-Null

    Write-Host "`n[Thanh cong] Apple TV Launcher da duoc cai dat & 4 ung dung Dock da duoc sap xep thanh cong!" -ForegroundColor Green
}

function Toggle-AdguardDns {
    if (-not $script:CurrentTv) { Write-Host "Vui long ket noi TV truoc!" -ForegroundColor Yellow; return }
    Show-Header
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "               CHAN QUANG CAO (ADGUARD PRIVATE DNS)               " -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Cyan
    $current = (& $script:adb -s $script:CurrentTv shell "settings get global private_dns_mode").Trim()
    Write-Host "Che do DNS hien tai: $current" -ForegroundColor Cyan
    Write-Host " 1. Bat AdGuard DNS (dns.adguard-dns.com) chan quang cao & theo doi"
    Write-Host " 2. Tat va quay lai DNS mac dinh cua nha mang (opportunistic)"
    Write-Host " 0. Quay lai menu chinh"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $dnsChoice = Read-Host "Chon (1-2, hoac 0 de quay lai menu)"
    if ($dnsChoice -eq "0" -or $dnsChoice -eq "b" -or -not $dnsChoice) { return }

    if ($dnsChoice -eq "1") {
        & $script:adb -s $script:CurrentTv shell "settings put global private_dns_specifier dns.adguard-dns.com"
        & $script:adb -s $script:CurrentTv shell "settings put global private_dns_mode hostname"
        Write-Host "[Da Bat] AdGuard Private DNS chan quang cao thanh cong!" -ForegroundColor Green
    } elseif ($dnsChoice -eq "2") {
        & $script:adb -s $script:CurrentTv shell "settings put global private_dns_mode opportunistic"
        Write-Host "[Da Tat] Tro ve DNS mac dinh." -ForegroundColor Green
    }
}

function Take-Screenshot {
    if (-not $script:CurrentTv) { Write-Host "Vui long ket noi TV truoc!" -ForegroundColor Yellow; return }
    Show-Header
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "                    CHUP ANH MAN HINH TV                          " -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host " 1. Chup anh man hinh TV va luu ve may tinh"
    Write-Host " 0. Quay lai menu chinh"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $choice = Read-Host "Nhap lua chon (1 de chup, 0 de quay lai menu)"
    if ($choice -ne "1") { return }

    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $shotPath = Join-Path $repoRoot "screenshot_$ts.png"
    & $script:adb -s $script:CurrentTv shell screencap -p /data/local/tmp/sc.png
    & $script:adb -s $script:CurrentTv pull /data/local/tmp/sc.png $shotPath
    Write-Host "`n[Da chup] Anh man hinh luu tai: $shotPath" -ForegroundColor Green
}

function Restore-Stock {
    if (-not $script:CurrentTv) { Write-Host "Vui long ket noi TV truoc!" -ForegroundColor Yellow; return }
    Show-Header
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "               KHOI PHUC TV VE TRANG THAI GOC                     " -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host " 1. Kich hoat lai toan bo ung dung goc va Launcher ban dau"
    Write-Host " 0. Quay lai menu chinh"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $choice = Read-Host "Nhap lua chon (1 de khoi phuc, 0 de quay lai menu)"
    if ($choice -ne "1") { return }

    $confirm = Read-Host "`nBan co chac chan 100% muon khoi phuc? (y de dong y, 0 de quay lai menu)"
    if ($confirm -ne 'y') { return }

    Write-Host "`nDang khoi phuc toan bo cac goi da tat..." -ForegroundColor Cyan
    $disabled = (& $script:adb -s $script:CurrentTv shell "pm list packages -d" | ForEach-Object { $_ -replace "^package:", "" }).Trim()
    foreach ($pkg in $disabled) {
        if ($pkg) {
            & $script:adb -s $script:CurrentTv shell "pm enable --user 0 $pkg"
            Write-Host " - Da kich hoat lai: $pkg" -ForegroundColor Gray
        }
    }
    # Reset launcher
    $stockCandidates = @("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.mitv.tvhome")
    foreach ($cand in $stockCandidates) {
        $candInstalled = (& $script:adb -s $script:CurrentTv shell "pm list packages $cand").Trim()
        if ($candInstalled) {
            & $script:adb -s $script:CurrentTv shell "cmd package set-home-activity --user 0 $cand"
            Write-Host "[Khoi phuc Launcher] Da dat lai launcher mac dinh: $cand" -ForegroundColor Green
            break
        }
    }
    & $script:adb -s $script:CurrentTv shell "settings put global private_dns_mode opportunistic"
    Write-Host "[Xong] TV da duoc khoi phuc ve trang thai goc." -ForegroundColor Green
}

function Remove-App {
    if (-not $script:CurrentTv) { Write-Host "Vui long ket noi TV truoc!" -ForegroundColor Yellow; return }
    Show-Header
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "                    GO BO UNG DUNG TREN TV                        " -ForegroundColor Yellow
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "Chon kieu go bo ung dung:" -ForegroundColor Cyan
    Write-Host " 1. Xem danh sach ung dung ben thu 3 da cai va chon de go bo"
    Write-Host " 2. Nhap ten goi bat ky de go bo (Package name)"
    Write-Host " 0. Quay lai menu chinh"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $mode = Read-Host "Chon che do (1-2, hoac 0 de quay lai menu)"
    if ($mode -eq "0" -or $mode -eq "b" -or -not $mode) { return }

    if ($mode -eq "1") {
        Write-Host "`nDang lay danh sach ung dung da cai dat..." -ForegroundColor Cyan
        $rawApps = & $script:adb -s $script:CurrentTv shell "pm list packages -3"
        $apps = @($rawApps | ForEach-Object { ($_ -replace '^package:', '').Trim() } | Where-Object { $_ })
        
        if ($apps.Count -eq 0) {
            Write-Host "Khong tim thay ung dung ben thu 3 nao." -ForegroundColor Yellow
            return
        }

        Write-Host "`nDanh sach ung dung ben thu 3 tren TV:" -ForegroundColor Yellow
        for ($i = 0; $i -lt $apps.Count; $i++) {
            Write-Host (" [{0,2}] {1}" -f ($i + 1), $apps[$i])
        }
        Write-Host " [ 0] Quay lai menu chinh" -ForegroundColor Gray

        $sel = Read-Host "`nNhap so thu tu ung dung can go (vi du: 2 hoac nhieu app: 1,3,4, hoac 0 de quay lai)"
        if (-not $sel -or $sel -eq "0" -or $sel -eq "b") { return }

        $indices = $sel -split ',' | ForEach-Object { 
            $num = 0
            if ([int]::TryParse($_.Trim(), [ref]$num)) { $num - 1 } else { -1 }
        }

        foreach ($idx in $indices) {
            if ($idx -ge 0 -and $idx -lt $apps.Count) {
                $pkg = $apps[$idx]
                if ($pkg -eq "com.gathin.tvhome") {
                    Write-Host "[Canh bao] Khong the go bo Launcher hien tai!" -ForegroundColor Red
                    continue
                }
                $confirm = Read-Host "Ban co chac muon go bo $pkg? (y de go bo, 0 hoac n de bo qua)"
                if ($confirm -eq 'y') {
                    $res = (& $script:adb -s $script:CurrentTv shell "pm uninstall $pkg").Trim()
                    Write-Host " - Go bo $pkg : $res" -ForegroundColor Green
                }
            }
        }
    } elseif ($mode -eq "2") {
        $customPkg = (Read-Host "Nhap chinh xac ten goi can go bo (vi du: com.example.app, hoac 0 de quay lai)").Trim()
        if (-not $customPkg -or $customPkg -eq "0" -or $customPkg -eq "b") { return }
        
        $protected = @("com.android.systemui", "com.android.tv.settings", "com.android.vending", "com.google.android.gms", "android", "com.gathin.tvhome")
        if ($protected -contains $customPkg -or $customPkg -like "com.realtek.*" -or $customPkg -like "com.sony.dtv.sonysystemservice*") {
            Write-Host "[Tu choi] Day la goi he thong quan trong, khong duoc phep go bo!" -ForegroundColor Red
            return
        }

        $confirm = Read-Host "Ban co chac muon go bo $customPkg? (y de go bo, 0 hoac n de quay lai)"
        if ($confirm -eq 'y') {
            $res = (& $script:adb -s $script:CurrentTv shell "pm uninstall --user 0 $customPkg").Trim()
            Write-Host " - Go bo $customPkg : $res" -ForegroundColor Green
        }
    }
}

# --- VONG LAP CHINH ---
if ($TvIp) { $null = Connect-Tv $TvIp }

while ($true) {
    Show-Header
    Write-Host " 1. Scan TV trong mang (hoac nhap dia chi IP)"
    Write-Host " 2. Sao luu toan bo TV (Backup truoc khi sua)"
    Write-Host " 3. Toi uu & Don dep ung dung rac (Debloat)"
    Write-Host " 4. Cai dat Launcher & Sap xep 4 app o thanh Dock"
    Write-Host " 5. Bat / Tat chan quang cao (AdGuard Private DNS)"
    Write-Host " 6. Go bo ung dung tren TV (Remove/Uninstall Apps)"
    Write-Host " 7. Chup anh man hinh TV (Screenshot)"
    Write-Host " 8. Khoi dong lai TV (Reboot)"
    Write-Host " 9. Khoi phuc TV ve nguyen ban (Restore All)"
    Write-Host " 10. Thoat"
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    $opt = Read-Host "Chon tinh nang (1-10)"

    switch ($opt) {
        "1" { Scan-And-Connect-Tv }
        "2" { Backup-Tv }
        "3" { Debloat-Tv }
        "4" { Install-Launcher }
        "5" { Toggle-AdguardDns }
        "6" { Remove-App }
        "7" { Take-Screenshot }
        "8" { 
            if ($script:CurrentTv) { 
                Show-Header
                Write-Host "==================================================================" -ForegroundColor Cyan
                Write-Host "                       KHOI DONG LAI TV                           " -ForegroundColor Yellow
                Write-Host "==================================================================" -ForegroundColor Cyan
                Write-Host " 1. Khoi dong lai TV ($($script:CurrentTv))"
                Write-Host " 0. Quay lai menu chinh"
                Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
                $choice = Read-Host "Nhap lua chon (1 hoac 0 de quay lai menu)"
                if ($choice -eq "1") {
                    & $script:adb -s $script:CurrentTv reboot
                    Write-Host "TV dang khoi dong lai..." -ForegroundColor Green
                    $script:CurrentTv = ""
                }
            } else { Write-Host "Chua ket noi TV!" -ForegroundColor Yellow }
        }
        "9" { Restore-Stock }
        "10" { exit }
    }
    Write-Host "`nNhan phim bat ky de quay lai menu chinh..." -ForegroundColor DarkGray
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}
