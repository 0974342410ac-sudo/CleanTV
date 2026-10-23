@echo off
rem Mo ung dung chinh giao dien launcher tren TV (LauncherEditor.ps1).
start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0LauncherEditor.ps1" %*
