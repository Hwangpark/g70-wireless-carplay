#!/bin/sh
# Off-car test of the G70 wireless mod on HMG-API's patch engine, against the
# G70 stock dump (LEAFIA_G70/stock). Adapted from HMG-API's
# mods/wireless-carplay-aa/test.sh.
#   hmgapi/test-g70-mod.sh <HMG-API checkout>
# A: every part on, apply must give the feature files, featurexml lines, props
#    and the audio converter; restore must give back every stock byte.
# B: the engine's own checks with this car's facts: the mod is accepted for
#    IKFL.KOR.*.260902 (and refused for a US EV6), and the G70 hook map loads,
#    matches the stock framework/app md5s and runs with testing_maps on.
# Hooks themselves (Dalvik) can't run off-car.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/.." && pwd)
API=$(cd "${1:?usage: test-g70-mod.sh <HMG-API checkout>}" && pwd)
S=$REPO/LEAFIA_G70/stock
[ -d "$S/app" ] || { echo "skipped: needs a G70 stock dump in LEAFIA_G70/stock (the dump kit's output)"; exit 0; }
MOD=$HERE/mods/wireless-carplay-g70
ID=wireless-carplay-g70
PRODUCT=IKFL.KOR.0000.V039.001.260902
ONEBIN=ikfl_kr
[ -d "$MOD" ] || { echo "no $MOD: run hmgapi/build-g70-mod.sh first"; exit 1; }
CP=$(mktemp -d); T=$(mktemp -d); trap 'rm -rf "$T" "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -d "$CP" "$API"/core/runtime/java/hmgapi/engine/*.java 2>/dev/null
R=$T/root
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "FAIL  $1: wanted [$2], got [$3]"; fail=1; fi; }
has() { case "$3" in *"$2"*) echo "ok    $1" ;; *) echo "FAIL  $1: wanted [$2] in [$3]"; fail=1 ;; esac; }
hasnt() { case "$3" in *"$2"*) echo "FAIL  $1: [$2] in [$3]"; fail=1 ;; *) echo "ok    $1" ;; esac; }
eng() { set +e; out=$(env HMGAPI_ROOT=$R HMGAPI_HOST=1 ${ANY:+HMGAPI_TEST_ANY_CAR=1} HMGAPI_PRODUCT=$P HMGAPI_ONEBIN=$ONEBIN \
        java -cp "$CP" hmgapi.engine.Engine "$@" 2>&1); rc=$?; out=$(echo "$out" | grep -v JAVA_TOOL); set -e; }
tree() { (cd $R/system && find . -type f | sort | while read f; do echo "$(md5sum < "$f" | cut -c1-32) $f"; done) }
setup() {
    rm -rf $R
    mkdir -p $R/system/app $R/system/lib $R/system/framework $R/system/etc/permissions $R/system/onebin/$ONEBIN \
        $R/sys/bus/sdio/devices/mmc1:0001:1 $R/data/hmgapi/mods $R/data/hmgapi/settings
    cp $S/app/* $R/system/app/; cp $S/lib/* $R/system/lib/; cp $S/framework/* $R/system/framework/
    cp $S/onebin/featurexml.cfg $R/system/onebin/$ONEBIN/
    cp $S/etc/permissions/* $R/system/etc/permissions/
    printf 'ro.build.product=%s\npersist.ium.support=none\n' "$P" > $R/system/build.prop
    printf 'ro.build.product=%s\nro.board.platform=tcc803x\nro.build.version.release=4.4.2\nro.mobis.onebindir=%s\n' "$P" "$ONEBIN" > $R/props.txt
    cp -r "$MOD" $R/data/hmgapi/mods/$ID
}

echo "== A: apply and restore (patch engine)"
P=$PRODUCT; ANY=1; setup
BEFORE=$(tree)
eng plan
check "plan has no problems" 0 $rc
[ $rc = 0 ] || echo "$out"
eng apply
check "apply (every part)" 0 $rc
[ $rc = 0 ] || echo "$out"
for f in com.hkmc.software.wifihotspot android.hardware.wifi com.hkmc.software.connectivity.carplay_wireless \
         com.hkmc.software.connectivity.androidauto_wireless; do
    has "feature file $f" "<feature name=\"$f\" />" "$(cat $R/system/etc/permissions/$f.xml 2>/dev/null)"
done
X=$(cat $R/system/onebin/$ONEBIN/featurexml.cfg)
for f in com.hkmc.software.wifihotspot com.hkmc.software.connectivity.carplay_wireless com.hkmc.software.connectivity.androidauto_wireless; do
    has "featurexml: $f line commented" "#-/system/etc/permissions/$f.xml" "$X"
done
has "featurexml: other lines kept (telematics.ev)" "-/system/etc/permissions/com.hkmc.software.telematics.ev.xml" "$X"
has "audio converter added" "/system/lib/libleafiaaac.so" "$out"
check "CarPlay/AA libraries untouched (patched in memory)" "" "$(echo "$out" | grep 'libautoreceiver\|libCarPlay')"
check "DiPO, GPM, BtSettings untouched (hooks)" "" "$(echo "$out" | grep 'HKMC_DiPO\|HKMC_GPM\|HKMC_BtSettings')"
has "props live: ium" "prop/persist.ium.support=carplay" "$(cat $R/settings.txt)"
has "props live: iap2 wifi" "prop/persist.sys.iap2.wifi=t" "$(cat $R/settings.txt)"
has "props live: iap2 wireless" "prop/persist.iap2.iden.wireless=t" "$(cat $R/settings.txt)"
check "build.prop untouched" "persist.ium.support=none" "$(grep persist.ium $R/system/build.prop)"
eng restore
check "restore exit" 0 $rc
check "/system byte-identical to stock" "$BEFORE" "$(tree)"
check "props back to stock" "" "$(cat $R/settings.txt 2>/dev/null)"

echo "== B: this car's facts (no test shortcuts)"
P=$PRODUCT; ANY=; setup
mkdir -p $R/data/hmgapi/maps
cp -r "$HERE/maps/." $R/data/hmgapi/maps/
# the catalogue list HMG-API's payload installs (build.sh payload)
python3 -I -c 'import glob,json,sys; [print(p["id"]) for f in sorted(glob.glob(sys.argv[1]+"/core/hookpoints/*.json")) for p in json.load(open(f))["points"]]' "$API" > $R/data/hmgapi/maps/points.txt
eng map
has "map found for this product" "Genesis G70" "$out"
hasnt "map matches the stock framework/apps" "mismatch" "$out"
echo "      $out"
printf '{"testing_maps": true}\n' > $R/data/hmgapi/settings/hmgapi.json
eng map
has "map runs with testing_maps" "testing_maps is on" "$out"
eng plan
check "plan accepts the mod on IKFL.KOR 260902" 0 $rc
[ $rc = 0 ] || echo "$out"
hasnt "no 'doesn't support this head unit'" "doesn't support" "$out"
hasnt "every hook point of the mod is known and mapped" "won't load" "$out"
hasnt "hooks on" "hooks are off" "$out"
echo "$out" | grep -i "hook" | while read -r l; do echo "      $l"; done
P=CVEV.USA.0000.V015.012.260507; setup
eng plan
has "a US EV6 is refused (system check)" "needs model IKFL" "$out"
exit $fail
