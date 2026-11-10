#!/bin/sh
# Host test of the G70 research log (src-patches/07), no car or dump needed:
# iAP2 packet reading, notification dumps and masking (DiPO's part), the car navigation's
# cluster calls and the iAP2 server's feature switches (the mod's main part).
#   hmgapi/test-g70-research.sh <HMG-API checkout>
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
API=$(cd "${1:?usage: test-g70-research.sh <HMG-API checkout>}" && pwd)
UP=$API/mods/wireless-carplay-aa
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp -r "$UP/src" "$T/src"
for p in "$HERE"/src-patches/*.patch; do
    [ -f "$p" ] || continue
    (cd "$T" && patch -p1 --no-backup-if-mismatch < "$p") >/dev/null || { echo "patch failed: $p"; exit 1; }
done
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$T/cls" $(find "$HERE/test-research/stubs" -name '*.java') \
    "$UP/stubs/com/tcc/iap2/Iap2NotiMsg.java" "$T/src/hmgapi/mods/wireless/dipo/Research.java" \
    "$T/src/hmgapi/mods/wireless/G70Research.java" $(find "$HERE/test-research/test" -name '*.java')
java -cp "$T/cls" hmgapi.mods.wireless.dipo.ResearchTest
java -cp "$T/cls" hmgapi.mods.wireless.G70ResearchTest
