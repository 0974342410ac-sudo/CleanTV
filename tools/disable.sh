#!/usr/bin/env bash
# Disable every package listed in a debloat file on the TV (one package per line, # comments allowed).
# Packages are disabled for user 0, never uninstalled. Undo with tools/restore.sh <same file>.
#
# Usage: TV_HOST=192.168.1.50 tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
set -euo pipefail
LIST="${1:?usage: disable.sh <debloat list>}"
# Resolve the list against the caller's directory before moving into the repo. Git Bash's pwd says
# /c/Users/..., which the Windows Python cannot open with MSYS_NO_PATHCONV=1 set; pwd -W gives
# C:/Users/..., the same path (and so the same record) as the PowerShell scripts.
LIST_DIR="$(cd "$(dirname "$LIST")" && { pwd -W 2>/dev/null || pwd; })"
LIST="$LIST_DIR/$(basename "$LIST")"
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
# Lists come from contributors, and the package name is pasted into a shell command on the TV.
# Refuse the whole list unless every entry is a bare package name (#165).
# Normalise first: Notepad on Windows saves a BOM and CRLF, and without this the first "# TV" line
# arrives as a package name and every entry keeps a trailing \r (#208).
PKGS=$(LC_ALL=C awk 'NR==1{sub(/^\357\273\277/,"")} {sub(/\r$/,""); print}' "$LIST" \
  | grep -vE '^\s*(#|$)' | sed -E 's/\s+#.*$//' | awk '{print $1}')
BAD=$(printf '%s\n' "$PKGS" | grep -vE '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || true)
if [ -n "$BAD" ]; then
  { echo "refusing $LIST: not a package name:"; printf '%s\n' "$BAD" | sed 's/^/  /'; } >&2
  exit 1
fi
# A Wi-Fi network saved during the first-run wizard belongs to setupwraith, and Android deletes it the
# moment setupwraith is disabled: the TV drops off Wi-Fi, and ADB and the phone remote go with it.
# Skip it until the network has been forgotten and re-joined in Settings (it then belongs to
# android.uid.system). FORCE_SETUPWRAITH=1 disables it anyway, e.g. on Ethernet.
SETUP=com.google.android.tungsten.setupwraith
if printf '%s\n' "$PKGS" | grep -x "$SETUP" >/dev/null && [ -z "${FORCE_SETUPWRAITH:-}" ]; then
  # Be safe: if we cannot read the owners (tv.py failed, timed out, or showed no network at all),
  # do not disable setupwraith, so the TV cannot lose the network it is on.
  WIFI="$("$PY" -I tools/tv.py "dumpsys wifi")" || WIFI=""
  OWNERS="$(printf '%s\n' "$WIFI" | grep -o 'cname=[^ ]*' || true)"
  if [ -z "$OWNERS" ]; then
    echo "Skipping $SETUP: could not check which app owns the saved Wi-Fi networks (dumpsys wifi failed" >&2
    echo "or showed none). Try again, or set FORCE_SETUPWRAITH=1 if the TV uses Ethernet." >&2
    PKGS="$(printf '%s\n' "$PKGS" | grep -vx "$SETUP" || true)"
  # no grep -q here: with pipefail its early exit would SIGPIPE the grep before it and fail open
  elif printf '%s\n' "$OWNERS" | grep setupwraith >/dev/null; then
    echo "Skipping $SETUP: the saved Wi-Fi network belongs to it, and disabling it would delete that" >&2
    echo "network and take the TV off Wi-Fi. On the TV, forget the network and join it again in" >&2
    echo "Settings > Network & Internet, then run this again (or set FORCE_SETUPWRAITH=1 on Ethernet)." >&2
    PKGS="$(printf '%s\n' "$PKGS" | grep -vx "$SETUP" || true)"
  fi
fi

# On firmware with ro.adb.secure=0, port 5555 gives a shell to any device on the network without the
# "Allow" prompt. Read it now and say so once the list is done; a failed read only skips the note.
ADB_SECURE="$("$PY" -I tools/tv.py "getprop ro.adb.secure" 2>/dev/null | tr -d '[:space:]')" || ADB_SECURE=""

# Home guard (#239): a list that disables the app owning Home leaves the TV with nothing to start the
# next time Home is pressed, and on Google TV the setup wizard can take the category instead and
# crash-loop system_server (docs/TROUBLESHOOTING.md). Skip the owner while nothing else on the TV can
# answer HOME; FORCE_HOME=1 disables it anyway, for a TV reachable another way.
# The extra query is worth it only when the list holds Home's own package, which is how a list that
# leaves the TV without a launcher looks before anything is disabled.
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
HOME_PKG=$("$PY" -I tools/tv.py "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true)
# grep without -q: an early exit could SIGPIPE printf and fail open under pipefail (see above).
if [ -n "$HOME_PKG" ] && printf '%s\n' "$PKGS" | grep -xF "$HOME_PKG" >/dev/null && [ -z "${FORCE_HOME:-}" ]; then
  # Every package with a HOME activity the TV can start now: disabled packages do not answer, so this
  # is "what could take Home if the list went through".
  CLAIMS=$("$PY" -I tools/tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null \
    | tr -d '\r' | grep -o '^ *[A-Za-z0-9_.]*/' | tr -d ' /' | sort -u || true)
  # Nothing left to answer Home once the list is applied, or the TV could not say who does: either
  # way the owner is the only Home this run knows about, so it stays enabled.
  # The setup wizard and the settings apps answer HOME too, but they are not launchers: on Google TV,
  # setupwraith taking Home is the crash loop this guard exists to prevent. They don't count.
  OTHERS=$(printf '%s\n' "$CLAIMS" | grep -vxF "$HOME_PKG" | grep -vxF "$PKGS" \
    | grep -vxF -e com.google.android.tungsten.setupwraith -e com.android.tv.settings -e com.android.settings -e com.android.provision || true)
  if [ -z "$OTHERS" ]; then
    echo "Skipping $HOME_PKG: it owns Home, and no other app that can answer Home is installed and" >&2
    echo "staying enabled. Make the new launcher Home first (launcher/build-and-install.sh), then run" >&2
    echo "this list again to disable the old one; or set FORCE_HOME=1 if you can still reach the TV." >&2
    PKGS="$(printf '%s\n' "$PKGS" | grep -vxF "$HOME_PKG" || true)"
  fi
fi

# The state undo needs goes to disk before the first command reaches the TV, and a failed save stops
# here with the TV untouched: a record written afterwards would describe a TV that is already broken,
# and a retry must not overwrite the one taken before the first change (#239).
"$PY" -I tools/recovery.py --save "$LIST"

{
  [ -n "$PKGS" ] && printf '%s\n' "$PKGS" | sed 's/^/pm disable-user --user 0 /'
  echo "pm list packages -d"
} | "$PY" -I tools/tv.py --batch

if [ "$ADB_SECURE" = "0" ]; then
  echo "Warning: this TV reports ro.adb.secure=0, so network ADB on port 5555 accepts any device on" >&2
  echo "your network without the \"Allow\" prompt. When you are done, turn USB debugging and" >&2
  echo "Network/ADB debugging off on the TV and reboot it (README: \"Isn't ADB access a security hole?\")." >&2
fi
