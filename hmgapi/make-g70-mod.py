#!/usr/bin/env python3
"""make-g70-mod.py - derive the G70 variant of HMG-API's Wireless CarPlay + Android Auto mod.

usage: make-g70-mod.py <HMG-API checkout> <G70 stock dir | g70-stock.md5> <out mod dir>

Copies mods/wireless-carplay-aa from an HMG-API checkout and changes only what
a Korean Genesis G70 (IKFL.KOR.*.260902) needs. Java hooks, recipes and
settings stay the upstream ones.

  mod.json
    id/name             its own id (wireless-carplay-g70), so it never collides
                        with the upstream mod's signature or updates
    system              IKFL / KOR / 260902 with a Wi-Fi chip (hmgapi.wifi)
    feature_flags       + com.hkmc.software.wifihotspot, android.hardware.wifi:
                        Korean firmware lists them with "-" in featurexml.cfg and
                        the car deletes those permission files at boot, so they
                        are missing on the unit (dump: permfile ...=0). The engine
                        writes each file and comments its featurexml line, as it
                        does for the CarPlay/AA wireless flags. Leafia did the same
                        on Korean firmware (hotspot_was_removed).
  patches.json
    cars                IKFL.KOR. -> set g70 (without a matching entry the engine
                        skips the mod: "doesn't support this head unit")
    changes             the upstream featurexml "lines" change for wifihotspot is
                        dropped: the feature flag above comments the same line
  settings.json         + g70_tbt_style (choice, dipo), g70_icon_test (choice, off, with the
                        HUD junction test) and g70_debug (toggle, on from 2.2.1): src-patches
                        04, 03, 09, 06, 07. The phone-lanes settings of 2.2.2 are gone (10).
                        + g70_media_info (toggle, on) and g70_agree_notice (toggle, off): 13
                        + g70_mute_pause (toggle, on): 15
                        (g70_sport_temps, g70_sport_temps_pos: 16, 19; removed with the sport display in 21)
                        + g70_carplay_dark (toggle, on), g70_hud_name (choice, always): 18
                        + g70_call_state (toggle, off; the call-state half of g70_media_info): 20
  hooks.points          + framework.navigation_cluster.update_lane_info (observe): the
                        research log reads the car navigation's lane frames (07)
  patches/g70/stock     md5s of the G70 stock files, same names as upstream sets
  patches/g70/expect    empty: this variant rebuilds no app

Reads only; writes the out dir (which must not exist yet).
"""
import hashlib, json, os, shutil, sys

UPSTREAM_ID = "wireless-carplay-aa"
ID = "wireless-carplay-g70"
PREFIX = "IKFL.KOR."
SET = "g70"
EXTRA_FLAGS = ["com.hkmc.software.wifihotspot", "android.hardware.wifi"]
VERSION = "2.7.3"   # 2.7.3: the CarPlay picture waits up to 10 s for a late video frame instead of ending (the G70 library's 200 ms read timeout, a memory recipe, setting g70_video_wait on): the picture froze while the sound went on; 2.7.2: Naver Map's "N o'clock" turns and roundabouts (no turn with an exit angle) read as the turn the angle says, not straight on; 2.7.1: a junction picture (or a tollgate bar) ends only 50 m past where it started (no on-off at the boundary, as Korea Projection 1.7.3), the HUD name never cut in the middle of an emoji, an unanswered route guidance request asked once more and the log points at Naver Map's CarPlay HUD setting; 2.7.0: a third turn style to compare on the car, "kp": the junction picture and distance bar as Korea Projection computes them (inside the stock style's flow); a log line when the phone navigates but its map app sends no route guidance (Naver Map, 11/10-11); 2.6.7: CarPlay always dark off by default and gentler (only where the car would pick day, once per change, on DiPO's thread, no timer): the picture froze on 2.6.5; 2.6.6: the HUD distance bar on the car's navigation's own scale (a late picture starts low, 35 m steps in town), near distances rounded as it does (hundreds from 300 m, km from 1 km); 2.6.5: the sport display on the CarPlay screen removed (owner: no use unless the cluster shows it; the cluster can't be drawn on), temperatures and driving data stay in the research log; 2.6.4: call state no longer asked for by default (setting g70_call_state, off; KakaoTalk voice calls dropped with it), song info unchanged; the sport display ignores the speed signal's 511 km/h; 2.6.3: the sport display at the top left by default, two lines: the mode, oil and coolant; the accelerator pedal and G from the speed; tyres and fuel in the research log once a minute; 2.6.2: CarPlay always dark (the car sets CarPlay's night mode; now kept on), the turn's name above a junction picture at any distance on the HUD (setting), the startup consent screen dismissed with the car's factory call in P (as HMG-API's startup_notice); 2.6.1: sport mode read from the car's field as it is spelled (mCurrentDrviceMode; 2.6.0 never saw sport), the startup agreement pressed again every 2 s while the car says not agreed, the screen in front logged; 2.6.0: oil and coolant temperatures on the CarPlay screen in sport/sport+ (blue while the oil warms up), drive mode and temperatures readable in the research log, driving data (G, pedal, brake, steering, gear) in the research log; 2.5.3: mute pauses CarPlay music and unmute resumes it, as with Bluetooth audio (setting, on); 2.5.2: song and call info asked for again until the iAP2 server takes it (it said false while the channel authenticated), the navigation's consent (occupation) with the startup agreement setting; 2.5.1 dropped; 2.5.0: song and call info on wireless CarPlay (DiPO asks only on USB), the startup agreement pressed for the owner (setting, off), engine and drive mode data in the research log; 2.4.1: real junction shapes from Apple Maps angles, passed IC/JC/rest areas skipped, ramp + exit as one exit, tollgate bar from 1 km, distances rounded as the car navigation; 2.4.0: stock style (as the car navigation: junction pictures, bar, tollgate 178, arrival), the default; 2.3.0: guidance end clears the cluster/HUD, late update after DiPO's stop ignored, lanes dropped (the G70 cluster drew none); 2.2.3: HUD junction test, lanes off by default (the G70 cluster drew none); 2.2.2: the phone's lanes to the cluster, lane test, Wi-Fi stats in the research log; 2.2.1: research log on by default (iAP2 traffic, notifications, car navigation cluster calls); 2.2.0: release: quiet log, g70_debug setting; 2.1.5: TMAP destination; 2.1.4: GPM icon meanings, next turn, gpm style; 2.1.3: icon test, DiPO stop; 2.1.2: G70 icons; 2.1.1: debug (src-patches/)
STOCK_FILES = [
    "app/HKMC_DiPO.apk", "app/HKMC_DiPO.odex", "app/HKMC_GPM.apk", "app/HKMC_GPM.odex",
    "app/HKMC_Navi_ExSL.apk", "app/HKMC_Navi_ExSL.odex", "app/Iap2ServerApp.apk", "app/Iap2ServerApp.odex",
    "lib/libautoreceiver_infobank.so", "lib/libCarPlayCore.so", "lib/libCarPlayAudio.so",
    "lib/libCarPlayUtils.so", "app/HKMC_BtSettings.apk",
]
SKIP = {"src", "test.sh", "tools", "native", "build.sh", "stubs"}
VIDEO_WAIT = {
    "key": "g70_video_wait",
    "type": "toggle",
    "default": True,
    "apply": "restart",
    "title": "G70: CarPlay picture waits through Wi-Fi stalls",
    "description": "The car's CarPlay library ends the picture when a video frame is about 200 ms late and never "
                   "starts it again: the screen froze while the sound went on. On, it waits up to 10 s for the "
                   "frame (patched in memory only, the file stays stock), as HMG-API's newer wireless mod and "
                   "NEMESIS's enhanced projection do. Off: the car's own behaviour.",
}
# G70 2.7.3: our own memory recipes (hmgapi/recipes), put first in their library's list (HMG-API uses
# the first of a mod's recipes that fits the library)
G70_RECIPES = [("carplay-core-read-wait-g70.recipe", "libCarPlayCore.so")]
DEBUG = {
    "key": "g70_debug",
    "type": "toggle",
    "default": True,
    "apply": "restart",
    "title": "G70: research log",
    "description": "Records what the phone and the car exchange, to find what else can be done: route guidance "
                   "with its texts (destinations, road names), every iAP2 message id between the iPhone and the "
                   "head unit, every notification DiPO gets (now playing, calls), the car navigation's own "
                   "cluster/HUD calls, and the iAP2 server's feature switches. Short lines go to this app's log; "
                   "the full record to g70-research.log (DiPO) and g70-navi.log (navigation) in their cache "
                   "folders, which the G70 log kit copies to USB. Phone numbers and addresses are masked; song "
                   "titles, contact names and places are not. Off stops it and deletes those files.",
}
MEDIA = {
    "key": "g70_media_info",
    "type": "toggle",
    "default": True,
    "apply": "restart",
    "title": "G70: song info on wireless CarPlay",
    "description": "CarPlay asks the iPhone for the song playing only over USB; over Wi-Fi it skips it, so the HUD "
                   "shows no song. This asks for it once the wireless connection is up, as CarPlay does over USB. "
                   "The call state is a separate setting (below).",
}
CALL = {
    "key": "g70_call_state",
    "type": "toggle",
    "default": False,
    "apply": "restart",
    "title": "G70: call state on wireless CarPlay (test)",
    "description": "Also asks the iPhone for the call state over Wi-Fi, as CarPlay does over USB. CarPlay's own "
                   "wireless code leaves it out, it showed nothing on the cluster, and KakaoTalk voice calls dropped "
                   "with it on (11/07). Off by default; on only to compare.",
}
AGREE = {
    "key": "g70_agree_notice",
    "type": "toggle",
    "default": False,
    "apply": "restart",
    "title": "G70: press the startup agreement for me",
    "description": "The consent screen the car shows at every start: when this is on, it is dismissed for you, in P "
                   "only, through the car's own factory call (what HMG-API's 'Startup notice' uses). Turn this on only if "
                   "you'd accept that screen at every start yourself.",
}
MUTE = {
    "key": "g70_mute_pause",
    "type": "toggle",
    "default": True,
    "apply": "restart",
    "title": "G70: mute pauses CarPlay music",
    "description": "As with Bluetooth audio: when you mute while CarPlay music plays, the iPhone pauses, and it "
                   "plays on when you unmute (also by turning the volume up), so you don't miss part of the song. "
                   "Only CarPlay music: the radio and calls are left alone, and it doesn't resume if you switched "
                   "to another source meanwhile.",
}
DARK = {
    "key": "g70_carplay_dark",
    "type": "toggle",
    "default": False,
    "apply": "restart",
    "title": "G70: CarPlay always dark",
    "description": "The car, not the iPhone, tells CarPlay whether it is night (from the screen brightness mode "
                   "and the lights), so by day CarPlay's own screens are light even with dark maps. Simplest: set "
                   "the car's screen brightness mode to night, and CarPlay stays dark with this off. On: where the "
                   "car would pick day, CarPlay is asked for night once after each change (it may flash light first). "
                   "Off by default from 2.6.7 (the CarPlay picture froze with 2.6.2-2.6.6's way).",
}
HUD_NAME = {
    "key": "g70_hud_name",
    "type": "choice",
    "default": "always",
    "apply": "restart",
    "title": "G70: the turn's name on the HUD",
    "description": "Always: the junction picture with the exit or road name above it at any distance (the HUD "
                   "shows the name only with the picture), the distance bar for the last stretch. Near: as the car's "
                   "navigation, the icon far away and the picture with the name from 300 m (1 km on highways).",
    "options": [
        {"value": "always", "title": "Always (name and picture at any distance)"},
        {"value": "near", "title": "Near only (as the car's navigation)"},
    ],
}
EXTRA_POINTS = {"framework.navigation_cluster.update_lane_info": "observe"}
LANES = {
    "key": "g70_lanes",
    "type": "toggle",
    "default": False,
    "apply": "restart",
    "title": "G70: lanes from the phone to the cluster (test)",
    "description": "The phone sends lane guidance that the car's iAP2 server drops; this reads it from the CarPlay "
                   "connection and sends it to the cluster. Off by default: in the lane test (11/04) the G70 cluster "
                   "drew none. While on and the phone guides, the car navigation's own lanes are held back.",
}
LANE_HI = {
    "key": "g70_lane_highlight",
    "type": "choice",
    "default": "2",
    "apply": "restart",
    "title": "G70: lane status for the lane to take",
    "description": "The value the cluster gets for a recommended lane. Pick the one the lane test showed highlighted.",
    "options": [
        {"value": "2", "title": "2 (default)"},
        {"value": "3", "title": "3"},
        {"value": "4", "title": "4"},
        {"value": "5", "title": "5"},
    ],
}
TBT_STYLE = {
    "key": "g70_tbt_style",
    "type": "choice",
    "default": "stock",
    "apply": "restart",
    "title": "G70: how turns go to the cluster/HUD",
    "description": "Stock: as the car's own navigation sends them (recorded 11/04-05): the icon far from the turn, "
                   "the junction picture with the distance bar near it, the tollgate icon, the arrival. "
                   "Korea Projection: the same flow with the junction picture and the bar as the Korea Projection "
                   "project computes them (the road you take only, the bar in tenths of 350 m or 1050 m), to compare. "
                   "DiPO: as CarPlay's own turn-by-turn sends them (icon only). "
                   "GPM: as Hyundai's Android Auto one does (side street 65535, turn direction, distance bar).",
    "options": [
        {"value": "stock", "title": "Stock (as the car's navigation, default)"},
        {"value": "kp", "title": "Korea Projection picture and bar (test)"},
        {"value": "dipo", "title": "DiPO (icon only)"},
        {"value": "gpm", "title": "GPM (Android Auto style)"},
    ],
}
ICON_TEST = {
    "key": "g70_icon_test",
    "type": "choice",
    "default": "off",
    "apply": "restart",
    "title": "G70: HUD icon / lane test",
    "description": "Starts whenever route guidance starts (set a destination while parked): icon codes one by one, "
                   "3 s each, the code as the HUD distance; or the HUD junction test: a right turn with side streets and "
                   "the distance bar varied, 24 steps, the step as the HUD distance. Stops when the car's driving restriction begins. Set back "
                   "to Off afterwards, or it runs at every route.",
    "options": [
        {"value": "off", "title": "Off"},
        {"value": "junctions", "title": "HUD junction test (24 steps)"},
        {"value": "known", "title": "Icons the turn table uses (33)"},
        {"value": "64-127", "title": "Codes 64-127"},
        {"value": "128-191", "title": "Codes 128-191"},
        {"value": "192-255", "title": "Codes 192-255"},
        {"value": "1-63", "title": "Codes 1-63 (cluster only)"},
    ],
}


def md5(p):
    h = hashlib.md5()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def main(hmgapi, stock, out):
    src = os.path.join(hmgapi, "mods", UPSTREAM_ID)
    if os.path.exists(out):
        sys.exit(f"{out} exists; remove it first")
    os.makedirs(out)
    for n in sorted(os.listdir(src)):
        if n in SKIP or n == "patches":
            continue
        s = os.path.join(src, n)
        (shutil.copytree if os.path.isdir(s) else shutil.copy2)(s, os.path.join(out, n))

    mod = json.load(open(os.path.join(src, "mod.json"), encoding="utf-8"))
    mod["id"] = ID
    mod["version"] = VERSION
    mod["name"] = "Wireless CarPlay + Android Auto (Genesis G70 KR)"
    mod["description"] = ("G70 (IKFL.KOR 260902) port of HMG-API's Wireless CarPlay + Android Auto: "
                          + mod.get("description", ""))
    mod["authors"] = list(mod.get("authors", [])) + ["Hwangpark (G70 port)"]
    mod["system"] = [{"model": ["IKFL"], "region": "KOR", "firmware": "260902", "features": ["hmgapi.wifi"]}]
    flags = list(mod.get("feature_flags", []))
    for f in EXTRA_FLAGS:
        if f not in flags:
            flags.append(f)
    mod["feature_flags"] = flags
    points = mod.setdefault("hooks", {}).setdefault("points", {})
    for k, v in EXTRA_POINTS.items():
        points.setdefault(k, v)
    here = os.path.dirname(os.path.abspath(__file__))
    native = mod["hooks"].setdefault("native", {})
    for name, lib in G70_RECIPES:
        os.makedirs(os.path.join(out, "recipes"), exist_ok=True)
        shutil.copy2(os.path.join(here, "recipes", name), os.path.join(out, "recipes", name))
        native.setdefault(lib, []).insert(0, "recipes/" + name)
    with open(os.path.join(out, "mod.json"), "w", encoding="utf-8") as f:
        json.dump(mod, f, indent=2, ensure_ascii=False)
        f.write("\n")

    # the G70 icon test (src-patches/03): a choice in the mod's settings, off by default
    st = json.load(open(os.path.join(src, "settings.json"), encoding="utf-8"))
    st["settings"] = [x for x in st.get("settings", []) if x.get("key") not in ("g70_icon_test", "g70_tbt_style", "g70_debug", "g70_lanes", "g70_lane_highlight", "g70_media_info", "g70_call_state", "g70_agree_notice", "g70_mute_pause", "g70_sport_temps", "g70_sport_temps_pos", "g70_carplay_dark", "g70_hud_name", "g70_video_wait")] + [TBT_STYLE, HUD_NAME, MEDIA, CALL, MUTE, DARK, VIDEO_WAIT, AGREE, ICON_TEST, DEBUG]
    with open(os.path.join(out, "settings.json"), "w", encoding="utf-8") as f:
        json.dump(st, f, indent=2, ensure_ascii=False)
        f.write("\n")

    pj = json.load(open(os.path.join(src, "patches.json"), encoding="utf-8"))
    pj["cars"] = {PREFIX: SET}
    kept = []
    for ch in pj.get("changes", []):
        if ch.get("type") == "lines" and ch.get("comment") == ["-/system/etc/permissions/com.hkmc.software.wifihotspot.xml"]:
            continue
        kept.append(ch)
    pj["changes"] = kept
    with open(os.path.join(out, "patches.json"), "w", encoding="utf-8") as f:
        json.dump(pj, f, indent=2, ensure_ascii=False)
        f.write("\n")

    d = os.path.join(out, "patches", SET)
    os.makedirs(d)
    with open(os.path.join(d, "stock"), "w") as f:
        if os.path.isfile(stock):   # a list of "<file> <md5>" (hmgapi/g70-stock.md5): no dump needed
            names = {os.path.basename(r) for r in STOCK_FILES}
            for line in open(stock, encoding="utf-8"):
                parts = line.split()
                if len(parts) == 2 and parts[0] in names and len(parts[1]) == 32:
                    f.write(f"{parts[0]} {parts[1]}\n")
        else:
            for rel in STOCK_FILES:
                p = os.path.join(stock, rel)
                if os.path.isfile(p):
                    f.write(f"{os.path.basename(rel)} {md5(p)}\n")
    open(os.path.join(d, "expect"), "w").close()
    print(f"{out}: {ID} from {UPSTREAM_ID}, set {SET} for {PREFIX}, flags {flags}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
