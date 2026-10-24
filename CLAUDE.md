# CleanTV

Debloats Android TV / Google TV / projectors and replaces their home screen with an Apple-TV-style launcher, in
Vietnamese. Everything is reversible (restore scripts, "Khôi phục" in the USB and phone updater). The owner talks to Claude in
Vietnamese; user-facing text (launcher, updater, guides) is Vietnamese.

## Layout
- `launcher/` Gradle project (JDK 17: `E:\buildtools\jdk\jdk-17.0.20.1+1`, SDK: `E:\buildtools\android-sdk`).
  - `app/` the launcher `com.gathin.tvhome` (Java, minSdk 23). Options in `res/values/config.xml`, overridden per TV
    by `/sdcard/Android/data/com.gathin.tvhome/files/config.json` (see `Config.java`). `WebRemote.java` serves a
    phone/iPhone remote page (`res/raw/remote.html`) on port 8686 behind a PIN and injects keys through the TV's
    own adbd (copies of the updater's `LocalAdb`/`AdbKey`; the connection closes after 20 s idle).
  - `updater/` the updater app: talks to the TV's own adbd on 127.0.0.1:5555 (`LocalAdb.java`). Flavors:
    `chung` (every known TV, `USB-CapNhat`), `xgimi` (XGIMI DOHKO only, `USB-CapNhat-XGIMI-DOHKO`) and
    `dienthoai` (`BuildConfig.REMOTE`: runs on an Android phone, reaches the TV's network ADB by IP, streams files
    into /data/local/tmp, self-updates from the latest GitHub Release, `Updates.java`).
- `updater/build-usb.ps1` builds the launcher, fills the updater assets and writes `dist/USB-CapNhat`. The XGIMI
  DOHKO package (`dist/USB-CapNhat-XGIMI-DOHKO`) is rebuilt only with `-XgimiDohko`; otherwise the old one stays.
- `updater/build-phone.ps1` builds `dist/DienThoai` (phone APK + version.json); `-Publish` makes the GitHub Release
  the phones update from (public repo 0974342410ac-sudo/CleanTV).
- `tools/` command-line tools for a PC: `disable.ps1` / `restore.ps1` (debloat lists), `theme.ps1`, `tv.py`;
  `launcher/build-and-install.ps1` builds and installs the launcher.
- `tools/xgimi-vi/` Vietnamese translations for XGIMI system apps and the scripts that apply them: fabricated
  overlays on the DOHKO, installed overlay APKs for RS10/H6 (m7642) and IRONMAN (`rs10-overlays.ps1 -Device`).
  `examples/<device>/debloat.txt` hold each device's removal list and its notes.
- Not in Git (see `.gitignore`): `backup/` (per-TV data), `dist/`, `prebuilt/`, build outputs, `tools/platform-tools`
  (download Google's platform-tools there), `tools/venv`, and `tools/adbkey` (this PC's ADB key; each PC has its own).

## Build
    powershell -ExecutionPolicy Bypass -File updater\build-usb.ps1      # launcher + USB-CapNhat
    powershell -ExecutionPolicy Bypass -File updater\build-usb.ps1 -XgimiDohko  # ... + the XGIMI DOHKO package
    powershell -ExecutionPolicy Bypass -File updater\build-phone.ps1    # phone app (-Publish: GitHub Release)
APKs are signed with `%USERPROFILE%\.android\debug.keystore`. Every PC that builds must use the same keystore
(shared privately, never committed), or the APK will not install over the copy already on the TVs
("signatures do not match").

## Devices (home LAN 192.168.31.x, ADB over TCP 5555)
- .12  XGIMI DOHKO, Android 14, adb root. Fabricated overlays are dropped at every boot: the launcher reruns
  `/data/local/tmp/cleantv/boot.sh` after boot (`boot_script`). XGIMI's home is kept enabled; Home relies on the
  chooser's "始终" (always) preference, which `cmd package resolve-activity -c HOME` turns back to not-always:
  check it with `dumpsys package preferred-activities` only (see UpdaterActivity).
- .239 / .150  XGIMI Android 6 (synsepalum_YN): static overlays in /system/vendor/overlay, HomeKeyService redirect;
  adbd serves one network connection at a time.
- .147 onn 4K box (Google TV 14): ADB needs the key prompt; the mic stays with Google Assistant.

## Conventions
- PowerShell 5.1 scripts: ASCII comments (Vietnamese text only in UTF-8-with-BOM files), Windows paths.
- Before sending remote keys or rebooting a TV, check `dumpsys window | grep mCurrentFocus`: someone may be watching.
- Keep changes reversible and note per-device findings in that device's `examples/*/debloat.txt`.
