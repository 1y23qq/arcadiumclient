#!/bin/sh
set -eu
parent_pid=$1
staged=$2
target=$3
work=$4
version=$5
arch=$6
exec >> "$work/updater.log" 2>&1
case "$parent_pid" in ''|*[!0-9]*) exit 2;; esac
case "$target" in /*.app) ;; *) exit 2;; esac
parent_dir=$(dirname "$target")
token=$(basename "$work")
case "$token" in ''|*[!a-zA-Z0-9-]*) exit 2;; esac
pending="$parent_dir/.Arcadium-update-$token.app"
backup="$parent_dir/.Arcadium-backup-$token.app"
boot="$work/app-ready"
[ ! -e "$pending" ] && [ ! -e "$backup" ]
cleanup() {
    if [ -e "$pending" ]; then rm -rf "$pending"; fi
}
trap cleanup EXIT
identifier=$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$staged/Contents/Info.plist")
actual_version=$(/usr/libexec/PlistBuddy -c 'Print CFBundleShortVersionString' "$staged/Contents/Info.plist")
[ "$identifier" = 'io.github.arcadiumclient.launcher' ] && [ "$actual_version" = "$version" ]
/usr/bin/lipo "$staged/Contents/MacOS/Arcadium" -verify_arch "$arch"
/usr/bin/codesign --verify --deep --strict "$staged"
/usr/bin/ditto "$staged" "$pending"
/usr/bin/codesign --verify --deep --strict "$pending"
touch "$work/installer-ready"
i=0
while kill -0 "$parent_pid" 2>/dev/null; do
    i=$((i + 1))
    if [ "$i" -ge 60 ]; then rm -rf "$pending"; exit 3; fi
    sleep 1
done
rollback() {
    if [ -d "$backup" ]; then
        rm -rf "$target"
        mv "$backup" "$target"
    fi
    ARCADIUM_UPDATE_FAILED="$version" "$target/Contents/MacOS/Arcadium" >/dev/null 2>&1 &
}
if ! mv "$target" "$backup"; then rollback; exit 4; fi
if ! mv "$pending" "$target"; then rollback; exit 4; fi
ARCADIUM_UPDATE_BOOT_READY="$boot" "$target/Contents/MacOS/Arcadium" >> "$work/restarted.log" 2>&1 &
child=$!
i=0
while [ ! -f "$boot" ]; do
    if ! kill -0 "$child" 2>/dev/null; then rollback; exit 5; fi
    i=$((i + 1))
    if [ "$i" -ge 60 ]; then kill "$child" 2>/dev/null || true; rollback; exit 5; fi
    sleep 1
done
sleep 3
if ! kill -0 "$child" 2>/dev/null; then rollback; exit 5; fi
rm -rf "$backup"
