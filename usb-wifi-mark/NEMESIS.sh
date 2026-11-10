#!/system/bin/sh
# G70: tell HMG-API that this unit's Wi-Fi chip answered on SDIO.
#
# HMG-API's hmgapi.wifi check looks at /sys/bus/sdio/devices, or at the marker
# /data/hmgapi/public/wifi-chip ("a Wi-Fi chip answered on SDIO on this unit
# once", which its engine writes as root when it sees the chip). On the Korean
# G70 (IKFL.KOR 260902) the chip comes up at ~9.6 s (mmc1: new ... SDIO card,
# Firmware up), then the stock firmware powers it off at ~10.5 s (WIFI OFF,
# mmc1: card 0001 removed) because the hotspot feature is missing. The engine
# never sees it, so the G70 wireless mod is refused ("no Wi-Fi chip found").
#
# This writes that same marker, but only if this boot's kernel log shows the
# SDIO card and the Wi-Fi firmware coming up. Changes nothing in /system.
# Remove the marker: delete /data/hmgapi/public/wifi-chip (HMG-API's uninstall
# removes /data/hmgapi).
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022
LOG=$(pwd)/G70-WIFI-MARK.txt
M=/data/hmgapi/public/wifi-chip
say() { echo "$1" >> "$LOG"; }
: > "$LOG"
say "g70-wifi-mark 1 $(date)"
say "uptime $(cat /proc/uptime 2>/dev/null)"
case "$(id)" in
    "uid=0("*) ;;
    *) say "FAIL: not root ($(id))"; exit 2 ;;
esac
if [ ! -d /data/hmgapi/public ]; then
    say "FAIL: HMG-API is not installed (no /data/hmgapi/public)"
    exit 3
fi
sdio=no; fw=no
dmesg 2>/dev/null | grep -q 'new .*SDIO card' && sdio=yes
dmesg 2>/dev/null | grep -q 'Firmware up' && fw=yes
say "kernel log: SDIO card=$sdio, Wi-Fi firmware up=$fw"
if [ $sdio != yes ] || [ $fw != yes ]; then
    say "FAIL: no Wi-Fi chip in this boot's kernel log (it may have rolled over)."
    say "Restart the head unit and run this again within a few minutes of the restart."
    exit 4
fi
: > $M && chmod 644 $M || { say "FAIL: could not write $M"; exit 5; }
say "ok: wrote $M ($(ls -l $M 2>/dev/null))"
sync
exit 0
