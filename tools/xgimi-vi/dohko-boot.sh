#!/system/bin/sh
# CleanTV (tools/xgimi-vi/dohko-boot-vi.ps1 or the USB updater put this in /data/local/tmp/cleantv): the XGIMI
# DOHKO drops fabricated overlays at every boot; the launcher runs this after boot through the TV's own adbd
# (boot_script). Creates each overlay again only when it is missing.
d=/data/local/tmp/cleantv
fab() {
  if ! cmd overlay list "$1" | grep -q "shell:$2\$"; then
    cmd overlay fabricate --target "$1" --name "$2" --file "$d/$3"
    cmd overlay enable --user 0 "com.android.shell:$2"
    # The app started before this and keeps the text it loaded: restart it so it reads the translation
    am force-stop "$1"
  fi
  cmd overlay enable --user 0 "com.android.shell:$2"
}
fab com.android.newsettings VietHoa settings-vi.xml
fab com.xgimi.shutdown VietHoaTatMay shutdown-vi.xml
# Other XGIMI apps (dohko-apps-xml.ps1): ov-<package>.xml -> overlay VietHoa_<last part of the package>
for f in "$d"/ov-*.xml; do
  [ -f "$f" ] || continue
  p=${f##*/ov-}; p=${p%.xml}
  fab "$p" "VietHoa_${p##*.}" "${f##*/}"
done
cmd overlay list | grep -c 'shell:VietHoa'
