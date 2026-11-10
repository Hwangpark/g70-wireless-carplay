#!/bin/sh
# Host test of G70 2.5.0 (src-patches/13), no car or dump needed: song and call info on
# wireless CarPlay, the startup agreement, the car's engine and drive mode data.
#   hmgapi/test-g70-extra.sh <HMG-API checkout>
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
API=$(cd "${1:?usage: test-g70-extra.sh <HMG-API checkout>}" && pwd)
UP=$API/mods/wireless-carplay-aa
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp -r "$UP/src" "$T/src"
for p in "$HERE"/src-patches/*.patch; do
    [ -f "$p" ] || continue
    (cd "$T" && patch -p1 --no-backup-if-mismatch < "$p") >/dev/null || { echo "patch failed: $p"; exit 1; }
done
D=$T/src/hmgapi/mods/wireless/dipo
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$T/cls" $(find "$HERE/test-research/stubs" -name '*.java') \
    $(find "$HERE/test-extra/fakes" -name '*.java') "$UP/stubs/com/tcc/iap2/Iap2NotiMsg.java" \
    "$D/Research.java" "$D/G70Extra.java" "$D/G70Car.java" "$D/G70Mute.java" "$D/G70Drive.java" "$D/G70Dark.java" $(find "$HERE/test-extra/test" -name '*.java')
java -cp "$T/cls" hmgapi.mods.wireless.dipo.G70ExtraTest
