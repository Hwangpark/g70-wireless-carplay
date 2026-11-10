#!/bin/sh
# Host test of the G70 CarPlay turn-by-turn changes (hmgapi/src-patches/*.patch on the
# upstream mod's dipo/CpTurns.java):
#   1. hmgapi/test-turns: the G70 log of 11/03 replayed (two source names, start route,
#      keep right, the 30 s stop) and the G70 icon fallbacks
#   2. the upstream mod's own CpTurns test (tools/cpturns), to show what changed from
#      upstream: its failures are listed, each must be one of the G70 changes
#   hmgapi/test-g70-turns.sh <HMG-API checkout>
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
API=$(cd "${1:?usage: test-g70-turns.sh <HMG-API checkout>}" && pwd)
UP=$API/mods/wireless-carplay-aa
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp -r "$UP/src" "$T/src"
for p in "$HERE"/src-patches/*.patch; do
    [ -f "$p" ] || continue
    (cd "$T" && patch -p1 --no-backup-if-mismatch < "$p") >/dev/null || { echo "patch failed: $p"; exit 1; }
done
STUBS=$(find "$HERE/test-turns/stubs" "$UP/tools/aaturns/stubs" -name '*.java')
javac --release 8 -Xlint:-options -nowarn -d "$T/cls" $STUBS "$UP/stubs/com/tcc/iap2/Iap2NotiMsg.java" \
    "$T/src/hmgapi/mods/wireless/dipo/CpTurns.java" "$T/src/hmgapi/mods/wireless/dipo/G70Lanes.java" \
    $(find "$HERE/test-turns/test" -name '*.java')
echo "== G70 replay"
java -cp "$T/cls" hmgapi.mods.wireless.dipo.G70TurnsTest
echo "== G70 lanes (src-patches/08)"
java -cp "$T/cls" hmgapi.mods.wireless.dipo.G70LanesTest
echo "== G70 stock style (src-patches/11)"
java -cp "$T/cls" hmgapi.mods.wireless.dipo.G70StockTest
echo "== upstream CpTurns test on the G70 CpTurns (differences only)"
javac --release 8 -Xlint:-options -nowarn -cp "$T/cls" -d "$T/cls" $(find "$UP/tools/cpturns/test" -name '*.java')
java -cp "$T/cls" hmgapi.mods.wireless.dipo.CpTurnsTest > "$T/up.txt" 2>&1 || true
grep -c '^ok' "$T/up.txt" | sed 's/^/ok: /'
grep -A12 '^FAIL' "$T/up.txt" || echo "no differences"
