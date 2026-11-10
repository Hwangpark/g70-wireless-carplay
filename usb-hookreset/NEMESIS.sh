#!/system/bin/sh
# G70 HMG-API boot guard reset. Writes one file: /data/hmgapi/hook/starts -> 0.
# HMG-API's zygote wrapper counts each start in that file and resets it to 0 once a
# start has run for 2 minutes; at 3 or more it leaves the hooks off ("boot guard"),
# and with the hooks off nothing resets it again. Restarting the head unit three
# times within 2 minutes of each start (installing, changing settings) trips it as a
# real boot loop would. This is HMG-API's own fix (wiki Troubleshooting, "The head
# unit restarted over and over": echo 0 > /data/hmgapi/hook/starts), without adb.
# Also writes, read-only, what it found to G70_HOOKRESET/result.txt on this USB drive:
# the counter before, the hook switch files, the boot guard lines in the kernel log,
# the hook map files and HMG-API's own "engine map" and "engine status" answers.
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022
OUT=$(pwd)/G70_HOOKRESET
H=/data/hmgapi/hook
R=$OUT/result.txt
rm -r "$OUT" 2>/dev/null
mkdir -p "$OUT" || exit 1
echo "g70-hookreset 1 $(date) uptime $(cat /proc/uptime 2>/dev/null)" > "$R"
case "$(id)" in "uid=0("*) ;; *) echo "not root" >> "$R"; exit 2 ;; esac
sec() { echo "" >> "$R"; echo "== $1" >> "$R"; }
sec "hook files"
ls -l $H >> "$R" 2>&1
echo "starts before: $(cat $H/starts 2>/dev/null)" >> "$R"
[ -e $H/disable ] && echo "NOTE: $H/disable exists (hooks switched off by hand or by an uninstall); left as it is" >> "$R"
sec "boot guard lines (kernel log)"
dmesg 2>/dev/null | grep hmgapi-hook >> "$R" 2>&1
sec "reset"
if [ -d $H ]; then
    if echo 0 > $H/starts; then
        echo "starts after: $(cat $H/starts 2>/dev/null) (hooks on again at the next restart)" >> "$R"
        MSG="G70: HMG-API boot guard reset. Unplug the USB and restart the unit"
    else
        echo "FAIL: can't write $H/starts" >> "$R"
        MSG="G70: boot guard reset FAILED, see G70_HOOKRESET on the USB"
    fi
else
    echo "FAIL: no $H (is HMG-API installed?)" >> "$R"
    MSG="G70: no HMG-API hook folder, see G70_HOOKRESET on the USB"
fi
sec "hook maps"
ls -lR /data/hmgapi/maps 2>&1 | grep -v '^$' | head -60 >> "$R"
sec "engine map"
/system/bin/hmgapid engine map >> "$R" 2>&1
sec "engine status"
/system/bin/hmgapid engine status >> "$R" 2>&1
am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$MSG" --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
sync
exit 0
