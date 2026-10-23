# CleanTV

Debloats Android TV / Google TV / projectors and replaces their home screen with an Apple-TV-style launcher, in
Vietnamese. Everything is reversible (restore scripts, "Khôi phục" in the USB updater). The owner talks to Claude in
Vietnamese; user-facing text (launcher, updater, guides) is Vietnamese.

## Layout
- `launcher/` Gradle project (JDK 17: `E:\buildtools\jdk\jdk-17.0.20.1+1`, SDK: `E:\buildtools\android-sdk`).
  - `app/` the launcher `com.gathin.tvhome` (Java, minSdk 23). Options in `res/values/config.xml`, overridden per TV
    by `/sdcard/Android/data/com.gathin.tvhome/files/config.json` (see `Config.java`).
  - `updater/` the USB updater: talks to the TV's own adbd on 127.0.0.1:5555 (`LocalAdb.java`). Flavors:
    `chung` (every known TV, `USB-CapNhat`) and `xgimi` (XGIMI DOHKO only, `USB-CapNhat-XGIMI-DOHKO`).
- `updater/build-usb.ps1` builds the launcher, fills the updater assets and writes both USB folders into `dist/`.
- `installer/build-setup.ps1` builds `dist/CaiDat-ChinhSuaLauncher.exe` (Windows editor `LauncherEditor.ps1` + launcher).
- `tools/xgimi-vi/` Vietnamese translations for XGIMI system apps (Settings, quick panel, power menu, 20 apps) and
  the scripts that apply them. `examples/<device>/debloat.txt` hold each device's removal list and its notes.
- Not in Git (see `.gitignore`): `backup/` (per-TV data), `dist/`, `prebuilt/`, build outputs, `tools/platform-tools`
  (download Google's platform-tools there), `tools/venv`, and `tools/adbkey` (this PC's ADB key; each PC has its own).

## Build
    powershell -ExecutionPolicy Bypass -File updater\build-usb.ps1      # launcher + both USB packages
    powershell -ExecutionPolicy Bypass -File installer\build-setup.ps1  # Windows installer
APKs are signed with `%USERPROFILE%\.android\debug.keystore`. Every PC that builds must use the same keystore
(shared privately, never committed), or the APK will not install over the copy already on the TVs
("signatures do not match").

## Devices (home LAN 192.168.31.x, ADB over TCP 5555)
- .12  XGIMI DOHKO, Android 14, adb root. Fabricated overlays are dropped at every boot: the launcher reruns
  `/data/local/tmp/cleantv/boot.sh` after boot (`boot_script`). XGIMI's home is kept enabled; Home relies on the
  chooser's "始终" (always) preference, which `cmd package set-home-activity` would overwrite (see UpdaterActivity).
- .239 / .150  XGIMI Android 6 (synsepalum_YN): static overlays in /system/vendor/overlay, HomeKeyService redirect;
  adbd serves one network connection at a time.
- .147 onn 4K box (Google TV 14): ADB needs the key prompt; the mic stays with Google Assistant.

## Conventions
- PowerShell 5.1 scripts: ASCII comments (Vietnamese text only in UTF-8-with-BOM files), Windows paths.
- Before sending remote keys or rebooting a TV, check `dumpsys window | grep mCurrentFocus`: someone may be watching.
- Keep changes reversible and note per-device findings in that device's `examples/*/debloat.txt`.
