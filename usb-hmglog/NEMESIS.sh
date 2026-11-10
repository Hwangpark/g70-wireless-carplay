#!/system/bin/sh
# G70 HMG-API log copy - READ ONLY. Changes nothing.
# Copies HMG-API's whole kept log (what the Log tab shows the end of: every HMG-API
# line since the unit started, and the previous start's in log.log.1) to this USB
# drive, so a drive's route guidance lines can be read in full afterwards, and the
# G70 mod's research log files (2.2.1 and later, setting "G70: research log").
# Writes G70_HMGLOG/ on this USB drive:
#   info.txt     time, uptime, file sizes, the log keeper's state
#   log.log      HMG-API's log since this start (keep private: can hold phone names)
#   log.log.1    the one before, if there is one
#   <app>-g70-research.log(.1), <app>-g70-navi.log(.1)
#                the research log from DiPO's and the car navigation's cache folders
#                (keep private: destinations, song titles, contact names)
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022
OUT=$(pwd)/G70_HMGLOG
L=/data/hmgapi/logs/hmgapi
I=$OUT/info.txt
rm -r "$OUT" 2>/dev/null
mkdir -p "$OUT" || exit 1
echo "g70-hmglog 2 $(date) uptime $(cat /proc/uptime 2>/dev/null)" > "$I"
case "$(id)" in "uid=0("*) ;; *) echo "not root" >> "$I"; exit 2 ;; esac
ls -l $L >> "$I" 2>&1
/system/bin/hmgapid services >> "$I" 2>&1
for f in log.log log.log.1; do
    [ -f "$L/$f" ] && cat "$L/$f" > "$OUT/$f"
done
for f in /data/data/*/cache/g70-*.log /data/data/*/cache/g70-*.log.1; do
    [ -f "$f" ] || continue
    a=${f#/data/data/}
    a=${a%%/*}
    ls -l "$f" >> "$I" 2>&1
    cat "$f" > "$OUT/$a-${f##*/}"
done
echo "end lines log.log=$(grep -c '' "$OUT/log.log" 2>/dev/null) log.log.1=$(grep -c '' "$OUT/log.log.1" 2>/dev/null)" >> "$I"
am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "G70 log copy: done, you can unplug the USB" --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
sync
exit 0
