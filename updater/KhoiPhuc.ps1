# Khoi phuc TV tu may tinh: CapNhat.ps1 -Restore (ung dung tren TV bam "Khoi phuc": bat lai ung dung da tat,
# tra man hinh chinh goc, bo Viet hoa). Launcher moi van duoc giu lai.
param([string]$TvHost = '')
& (Join-Path $PSScriptRoot 'CapNhat.ps1') -TvHost $TvHost -Restore
exit $LASTEXITCODE
