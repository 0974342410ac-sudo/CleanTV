#!/system/bin/sh
# CleanTV (tools/xgimi-vi/dohko-boot-vi.ps1 or the USB updater put this in /data/local/tmp/cleantv): the XGIMI
# DOHKO drops fabricated overlays at every boot; the launcher runs this after boot through the TV's own adbd
# (boot_script). Creates each overlay again unless it is on: Android can keep listing one whose .frro file is gone
# (state NO_IDMAP, shown as "---"), and enabling that does nothing.
d=/data/local/tmp/cleantv
fab() {
  if ! cmd overlay list "$1" | grep -q "\[x\] com.android.shell:$2\$"; then
    cmd overlay fabricate --target "$1" --name "$2" --file "$d/$3"
    cmd overlay enable --user 0 "com.android.shell:$2"
    # These two started before this and keep the text they loaded: restart them so they read the translation. Not the
    # others: they are the firmware's own services and player, Android refreshes them, and stopping them all during
    # boot left a black screen, no window focus for minutes, and com.xgimi.xrmservice crashing (2026-10-30, .12)
    case "$1" in
      com.android.newsettings|com.xgimi.shutdown) am force-stop "$1" ;;
    esac
  fi
}
fab com.android.newsettings VietHoa settings-vi.xml
fab com.xgimi.shutdown VietHoaTatMay shutdown-vi.xml
# Other XGIMI apps (dohko-apps-xml.ps1): ov-<package>.xml -> overlay VietHoa_<last part of the package>
for f in "$d"/ov-*.xml; do
  [ -f "$f" ] || continue
  p=${f##*/ov-}; p=${p%.xml}
  fab "$p" "VietHoa_${p##*.}" "${f##*/}"
done
# XGIMI's Chinese voice assistant takes the remote's mic button and closes the launcher's voice search, and it adds
# itself back to the accessibility list: turn it off again if anything (a firmware update) turned it on
if pm list packages -e com.xgimi.duertts | grep -q duertts; then
  pm disable-user --user 0 com.xgimi.duertts
fi
# Once on, it replaces the whole accessibility list: put the launcher's mic key service back too
k=com.gathin.tvhome/.InputKeyService
a=$(settings get secure enabled_accessibility_services)
[ "$a" = null ] && a=
b=$(echo "$a" | sed -E 's#(^|:)com\.xgimi\.duertts/[^:]*##g; s#^:##')
case "$b" in
  *"$k"*) ;;
  *) b="${b:+$b:}$k" ;;
esac
if [ "$a" != "$b" ]; then
  settings put secure enabled_accessibility_services "$b"
  settings put secure accessibility_enabled 1
fi
# Last line: the updater reads this count
cmd overlay list | grep -c '\[x\] com.android.shell:VietHoa'
