#!/bin/sh
# Build the signed G70 wireless mod package from an HMG-API checkout.
#   hmgapi/build-g70-mod.sh <HMG-API checkout> <author key> [out dir]
# 1. regenerates hmgapi/mods/wireless-carplay-g70 from the upstream mod (make-g70-mod.py)
# 2. builds HMG-API's runtime classes (./build.sh runtime) and the upstream mod's
#    hooks.jar (same javac + d8 steps as HMG-API's build.sh mods, without the
#    example apps that need aapt2)
# 3. signs and zips it the way tools/mkmodpkg.sh does
# Needs a JDK and the Android SDK pieces HMG-API's tools/env.sh looks for
# (build-tools/*/d8, platforms/android-*/android.jar).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/.." && pwd)
API=$(cd "${1:?usage: build-g70-mod.sh <HMG-API checkout> <author key> [out dir]}" && pwd)
KEY=${2:?usage: build-g70-mod.sh <HMG-API checkout> <author key> [out dir]}
OUT=${3:-$REPO/out}
MOD=$HERE/mods/wireless-carplay-g70
ID=wireless-carplay-g70
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

rm -rf "$MOD"
# the G70 stock md5s: from a dump if there is one, else the list kept in the repo
STOCK=$REPO/LEAFIA_G70/stock
[ -d "$STOCK" ] || STOCK=$HERE/g70-stock.md5
python3 -I "$HERE/make-g70-mod.py" "$API" "$STOCK" "$MOD"

. "$API/tools/env.sh"
[ -x "$D8" ] && [ -f "$ANDROID_JAR" ] || { echo "no d8/android.jar (see $API/tools/env.sh)"; exit 1; }
B=$OUT/build
rm -rf "$B" && mkdir -p "$B/stubs" "$B/classes"
(cd "$API" && OUT="$B/api" sh ./build.sh runtime >/dev/null)
UP=$API/mods/wireless-carplay-aa
# upstream Java + this repo's G70 changes (hmgapi/src-patches/*.patch, in name order)
cp -r "$UP/src" "$B/src"
for p in "$HERE"/src-patches/*.patch; do
    [ -f "$p" ] || continue
    (cd "$B" && patch -p1 --no-backup-if-mismatch < "$p") >/dev/null || { echo "patch failed: $p"; exit 1; }
done
# compile-time stand-ins: the upstream mod's (DiPO's libraries) and this repo's (G70 car framework
# classes, hmgapi/stubs, src-patches/13); classpath only, never in hooks.jar
javac --release 8 -Xlint:-options -nowarn -cp "$ANDROID_JAR" -d "$B/stubs" $(find "$UP/stubs" "$HERE/stubs" -name '*.java')
javac --release 8 -Xlint:-options -nowarn -cp "$ANDROID_JAR:$B/api/classes:$B/stubs" -d "$B/classes" $(find "$B/src" -name '*.java')
"$D8" --release --min-api 19 --lib "$ANDROID_JAR" --classpath "$B/api/classes" --classpath "$B/stubs" \
    --output "$B/hooks.jar" $(find "$B/classes" -name '*.class')

W=$B/pkg/$ID
mkdir -p "$W"
cp -r "$MOD"/. "$W/"
cp "$B/hooks.jar" "$W/"
CP=$B/engine-host
mkdir -p "$CP" && javac --release 8 -Xlint:-options -nowarn -d "$CP" "$API"/core/runtime/java/hmgapi/engine/*.java 2>/dev/null
java -cp "$CP" hmgapi.engine.Signing sign "$W" "$KEY"
VER=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$MOD/mod.json")
Z=$OUT/$ID-$VER.zip
rm -f "$Z"
# fixed order and times, as tools/mkmodpkg.sh: the same mod and key give the same zip
(cd "$W" && find . -type f | sort | TZ=UTC xargs touch -d 2026-01-01T00:00:00 && find . -type f | sort | zip -q -X -D "$Z" -@)
java -cp "$CP" hmgapi.engine.Signing verify "$W"
md5sum "$B/hooks.jar" "$Z"
