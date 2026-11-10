package hmgapi.mods.wireless.dipo;

import com.tcc.iap2.Iap2NotiMsg;

import java.util.ArrayList;
import java.util.List;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/**
 * Host test of the G70 changes to CpTurns (src-patches 02), replaying what a Korean
 * iPhone sent in the G70 log of 11/03 (16:40, Apple Maps, parked underground):
 * "Apple 지도" updates with state 0 or 3 between "Apple Maps" updates with the route
 * (state 6, current maneuver 27 = start route, type 11).
 */
public final class G70TurnsTest {
    static int fail;
    static Cluster c;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) {
            fail = 1;
            System.out.println("  calls: " + c.calls);
            System.out.println("  logs:  " + Dipo.logs);
        }
    }

    static boolean has(String prefix) {
        for (String s : c.calls) if (s.startsWith(prefix)) return true;
        return false;
    }

    static boolean logged(String part) {
        for (String s : Dipo.logs) if (s.contains(part)) return true;
        return false;
    }

    /** the HUD blank (hudBlank): HUD distance cleared */
    static boolean blanked() {
        return c.calls.contains("2c 0 0 0 0 0 0 0 0 0");
    }

    static void clear() {
        c.calls.clear();
        Dipo.logs.clear();
    }

    static final int U8 = 1, U16 = 5, U32 = 6, U64 = 7, ENUM = 8, BOOL = 9, STR = 10;

    static Iap2NotiMsg msg(Object... items) {
        Iap2NotiMsg m = new Iap2NotiMsg();
        for (int i = 0; i < items.length; i += 3) m.getItemGroup().add((Integer) items[i], (Integer) items[i + 1], items[i + 2]);
        return m;
    }

    static int[] i(int... v) {
        return v;
    }

    static long[] l(long... v) {
        return v;
    }

    static String[] s(String v) {
        return new String[] { v };
    }

    /** a maneuver for component 5, as the G70 log shows them (exit angle only when given) */
    static Iap2NotiMsg man(int index, int type, String after, int distance, int units, Integer exit) {
        List<Object> o = new ArrayList<Object>();
        Object[] base = { 0, U16, i(5), 1, U16, i(index), 2, STR, s("description"), 3, ENUM, i(type), 4, STR, s(after),
                5, U32, i(distance), 6, STR, s("1"), 7, ENUM, i(units), 8, ENUM, i(0), 9, ENUM, i(0) };
        for (Object x : base) o.add(x);
        if (exit != null) {
            o.add(11);
            o.add(U8);
            o.add(i(exit));
        }
        return msg(o.toArray());
    }

    /** a TMAP-like maneuver: no angles, a description */
    static Iap2NotiMsg manDesc(int index, int type, int distance, String desc) {
        return msg(0, U16, i(5), 1, U16, i(index), 2, STR, s(desc), 3, ENUM, i(type), 4, STR, s(""), 5, U32, i(distance),
                7, ENUM, i(2), 8, ENUM, i(0), 9, ENUM, i(0));
    }

    static Iap2NotiMsg korean(int state) {   // "Apple 지도": RG#2 / RG#6 of the log
        return msg(0, U16, i(5), 1, ENUM, i(state), 15, BOOL, i(1), 18, BOOL, i(0), 19, STR, s("Apple 지도"), 20, BOOL, i(1));
    }

    static Iap2NotiMsg bare(int state) {   // RG#1 / RG#3 / RG#7 / RG#8: component and state only
        return msg(0, U16, i(5), 1, ENUM, i(state));
    }

    static Iap2NotiMsg maps(int state, int[] list, String next) {   // "Apple Maps": RG#5 of the log
        return msg(0, U16, i(5), 1, ENUM, i(state), 4, STR, s("D"), 5, U64, l(1793692920L), 6, U64, l(1320L),
                7, U32, i(29156), 8, STR, s("29"), 9, ENUM, i(0), 10, U32, i(9), 11, STR, s(next), 12, ENUM, i(2),
                13, U16, list, 14, U16, i(28), 15, BOOL, i(1), 16, U16, i(0), 17, U16, i(0), 18, BOOL, i(0),
                19, STR, s("Apple Maps"), 20, BOOL, i(1));
    }

    public static void main(String[] a) throws Exception {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        CpTurns.debug = true;   // the detailed log (setting g70_debug); the quiet default is checked at the end
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        final int[] restarts = { 0 };
        CpTurns t = new CpTurns(new android.content.Context(), c, new Runnable() {
            @Override
            public void run() {
                restarts[0]++;
            }
        });

        // 16:40:15 the start of guidance
        t.routeGuidance(bare(0));
        t.routeGuidance(korean(0));
        t.routeGuidance(bare(0));
        t.routeGuidance(msg(0, U16, i(5), 1, ENUM, i(3), 14, U16, i(28), 15, BOOL, i(1), 17, U16, i(0), 18, BOOL, i(0),
                19, STR, s("Apple 지도"), 20, BOOL, i(1)));
        check("before any route: no turn sent", !has("tbt"));
        clear();

        t.maneuver(man(0, 11, "", 0, 2, null));
        t.maneuver(man(1, 2, "", 0, 2, 90));
        t.maneuver(man(2, 2, "road A", 60, 2, 90));
        t.maneuver(man(3, 1, "road A", 60, 2, -90));
        t.maneuver(man(8, 14, "road B", 1000, 0, null));
        t.maneuver(man(9, 14, "road C", 100, 2, null));
        t.maneuver(man(27, 11, "", 29000, 0, null));
        check("maneuvers alone: nothing sent", c.calls.isEmpty());

        // RG#5: Apple Maps, state 6 (to the route), current maneuver 27 = start route
        t.routeGuidance(maps(6, i(27), "9"));
        check("Apple 지도 -> Apple Maps is the same app: no reset, no restart", restarts[0] == 0 && logged("same app, kept"));
        check("state 6: destination name sent", has("dest 0 [D]"));
        check("start route (type 11) shows straight (65) on the cluster", has("tbt 65 0 0.0 9.0 0 0 0 0 0"));
        check("... and on the HUD list (0xF2, icon 0x41)", has("hud f20141"));
        check("G70 icon logged once", logged("G70 icon 65 for maneuver type 11"));
        clear();

        // RG#6, #7, #8: "Apple 지도" says no route, then two nameless state 0
        t.routeGuidance(korean(0));
        t.routeGuidance(bare(0));
        t.routeGuidance(bare(0));
        check("no route from Apple 지도 while Apple Maps guides: HUD not blanked", !blanked());
        check("... the turn stays on the cluster", has("tbt 65"));
        check("... logged as kept", logged("kept Apple Maps's route over 1 \"no route\" update(s) from Apple 지도"));
        check("... a stop is waiting for 30 s of quiet", Dipo.turnsHandler.delayed != null);
        clear();

        // driving: Apple Maps moves to maneuver 1 (right, 90 degrees), then a distance-only update
        android.os.SystemClock.now += 5000;
        t.routeGuidance(maps(1, i(1, 2, 3), "120"));
        check("route active: right turn (67) sent", has("tbt 67 0 0.0 120.0 0 0 0 0 0"));
        check("... the waiting stop is cancelled", Dipo.turnsHandler.delayed == null);
        clear();
        t.routeGuidance(msg(0, U16, i(5), 11, STR, s("80")));
        check("distance-only update: turn resent with the new distance", has("tbt 67 0 0.0 80.0 0 0 0 0 0"));
        clear();

        // keep right (14) as the current maneuver: slight right (66)
        t.routeGuidance(maps(1, i(8, 9), "1.0"));
        check("keep right (type 14) shows GPM's fork right (173)", has("tbt 173"));
        clear();

        // Apple 지도 says no route again, and Apple Maps then stays quiet for 30 s
        t.routeGuidance(korean(0));
        check("kept again", !blanked() && Dipo.turnsHandler.delayed != null);
        android.os.SystemClock.now += 10000;
        Runnable r = Dipo.turnsHandler.delayed;
        Dipo.turnsHandler.delayed = null;
        r.run();
        check("after 10 s the check waits again (Apple Maps may still talk)", !blanked() && Dipo.turnsHandler.delayed != null);
        android.os.SystemClock.now += 25000;
        r = Dipo.turnsHandler.delayed;
        Dipo.turnsHandler.delayed = null;
        r.run();
        check("30 s without Apple Maps: guidance ended, HUD blanked", blanked() && logged("guidance ended"));
        clear();

        // a new route from Apple Maps, then its own "no route": ends at once
        t.routeGuidance(maps(1, i(1, 2, 3), "200"));
        check("new route: turn sent", has("tbt 67"));
        clear();
        t.routeGuidance(msg(0, U16, i(5), 1, ENUM, i(0), 19, STR, s("Apple Maps")));
        check("Apple Maps' own state 0 ends guidance at once (HUD blanked)", blanked());
        clear();

        // another app takes over (a different app's name: reset and restart, as upstream)
        t.routeGuidance(msg(0, U16, i(5), 1, ENUM, i(1), 19, STR, s("TMAP")));
        Thread.sleep(200);
        check("a different app (TMAP): reset and restart as upstream", restarts[0] == 1 && logged("source changed from Apple Maps to TMAP"));
        clear();

        // 2.1.3: DiPO stops route guidance (route ended or another app): the next "no route" ends it at once
        t.routeGuidance(maps(1, i(1, 2, 3), "300"));   // back from TMAP: a new source, maneuvers cleared
        t.maneuver(man(1, 2, "", 0, 2, 90));
        t.routeGuidance(maps(1, i(1, 2, 3), "300"));
        clear();
        t.routeGuidance(korean(0));
        check("(again) a no route from the other name is kept", !blanked() && has("tbt 67"));
        clear();
        t.dipoStopped();
        t.stop();
        t.routeGuidance(korean(0));
        check("after DiPO's stop the other name's no route is taken: blanked, no 30 s wait",
                blanked() && Dipo.turnsHandler.delayed == null && !logged("kept"));
        clear();

        // 2.1.3: HUD icon test
        check("test codes: off -> none", CpTurns.testCodes("off") == null && CpTurns.testCodes(null) == null);
        check("test codes: known -> 33 from 3 to 176", CpTurns.testCodes("known").length == 33
                && CpTurns.testCodes("known")[0] == 3 && CpTurns.testCodes("known")[32] == 176);
        check("test codes: 64-127 -> 64 codes", CpTurns.testCodes("64-127").length == 64 && CpTurns.testCodes("64-127")[63] == 127);
        check("test codes: bad ranges -> none", CpTurns.testCodes("0-300") == null && CpTurns.testCodes("x-y") == null);
        Cluster c2 = new Cluster();
        CpTurns u = new CpTurns(new android.content.Context(), c2, null);
        u.testCodes = new int[] { 65, 200 };
        Cluster keep = c;
        c = c2;
        u.maneuver(man(1, 2, "", 100, 2, 90));
        u.routeGuidance(maps(1, i(1), "100"));
        check("icon test armed: the first route update starts it, no real turn sent", !has("tbt 67") && logged("HUD icon test: 2 codes"));
        Runnable step = Dipo.turnsHandler.delayed;
        Dipo.turnsHandler.delayed = null;
        step.run();
        check("step 1: code 65 on the cluster, HUD list and HUD distance",
                has("tbt 65 0 0.0 65.0 0 0 0 0 0") && has("hud f20141") && has("2c 0 0 65 0 0 0 0 0 0") && has("road [TEST 65]"));
        u.routeGuidance(maps(1, i(1), "90"));
        check("updates during the test don't send the real turn", !has("tbt 67"));
        step = Dipo.turnsHandler.delayed;
        Dipo.turnsHandler.delayed = null;
        step.run();
        check("step 2: code 200, then done", has("tbt 200 0 0.0 200.0 0 0 0 0 0") && has("hud f201c8") && logged("icon test done"));
        clear();
        u.routeGuidance(maps(1, i(1), "80"));
        check("after the test: the real turn again", has("tbt 67 0 0.0 80.0 0 0 0 0 0"));
        CpTurns v = new CpTurns(new android.content.Context(), c2, null);
        v.testCodes = new int[] { 65, 66, 67 };
        v.maneuver(man(1, 2, "", 100, 2, 90));
        v.routeGuidance(maps(1, i(1), "100"));
        Dipo.turnsHandler.delayed = null;
        clear();
        v.stopIconTest("driving restriction");
        v.routeGuidance(maps(1, i(1), "70"));
        check("driving restriction stops the test at once: the real turn again", has("tbt 67 0 0.0 70.0 0 0 0 0 0") && logged("stopped (driving restriction)"));
        c = keep;
        clear();

        // 2.1.4: TMAP-like route: straight maneuvers whose description says right, a one-entry list
        Cluster c3 = new Cluster();
        CpTurns w = new CpTurns(new android.content.Context(), c3, null);
        c = c3;
        w.maneuver(manDesc(4, 3, 748, "\uAC00\uB098\uB85C \uBC29\uBA74 \uC624\uB978\uCABD \uBC29\uD5A5"));
        w.maneuver(manDesc(5, 2, 4600, "\uC6B0\uD68C\uC804"));
        w.routeGuidance(msg(0, U16, i(5), 1, ENUM, i(1), 11, STR, s("748"), 12, ENUM, i(2), 13, U16, i(4), 19, STR, s("TMAP")));
        check("TMAP fork sent as straight, description says right: fork right 173", has("tbt 173 0 0.0 748.0 0 0 0 0 0"));
        check("... logged with its words, not its text", logged("from its description (\uC624\uB978\uCABD+\uBC29\uD5A5)") && !logged("\uAC00\uB098"));
        check("one-entry list: the next maneuver (index + 1) is the HUD's next turn (67)", has("hud f202ad43"));
        check("... and the cluster's turns ahead", has("list {1 67 0"));
        check("maneuver lines carry words only", logged("dbg man 4: type 3 exit 0 dist 748 desc ") && !logged("\uAC00\uB098\uB85C"));
        clear();
        w.gpmStyle = true;
        w.routeGuidance(msg(0, U16, i(5), 11, STR, s("700")));
        check("GPM style: side street 65535, direction 30, bar 32", has("tbt 173 65535 30.0 700.0 0 0 0 32 0"));
        c = keep;
        clear();

        // 2.1.5: TMAP's destination (item 4) reaches the cluster without state 6
        Cluster c4 = new Cluster();
        CpTurns d = new CpTurns(new android.content.Context(), c4, null);
        keep = c;
        c = c4;
        d.routeGuidance(msg(0, U16, i(5), 1, ENUM, i(3), 4, STR, s("\uC9D1"), 19, STR, s("TMAP")));
        check("state 3 with a destination: sent to the cluster once", c4.calls.contains("dest 0 [\uC9D1]") && logged("destination name to the cluster (1 chars, state 3)"));
        clear();
        d.routeGuidance(msg(0, U16, i(5), 1, ENUM, i(1), 4, STR, s("\uC9D1")));
        check("same destination again: not resent", !has("dest"));
        d.routeGuidance(msg(0, U16, i(5), 4, STR, s("\uD68C\uC0AC")));
        check("a new destination: sent", has("dest 0 [\uD68C\uC0AC]"));
        clear();
        d.routeGuidance(msg(0, U16, i(5), 1, ENUM, i(0)));
        d.routeGuidance(msg(0, U16, i(5), 1, ENUM, i(1), 4, STR, s("\uD68C\uC0AC")));
        check("after the route ended, the same destination is sent again", has("dest 0 [\uD68C\uC0AC]"));
        c = keep;
        clear();

        // icons the build's table leaves blank
        check("icon: keep left (13) -> fork left 174", CpTurns.g70Icon(13, 0, 0, 1) == 174);
        check("icon: off ramp (8) -> 86, right 81, left 82",
                CpTurns.g70Icon(8, 0, 0, 1) == 86 && CpTurns.g70Icon(8, 0, 45, 1) == 81 && CpTurns.g70Icon(8, 0, -45, 1) == 82);
        check("icon: on ramp (9) at an angle -> 173/174 (table: 172 straight)",
                CpTurns.icon(9, 0, 0, 0, 1) == 172 && CpTurns.g70Icon(9, 0, 30, 1) == 173 && CpTurns.g70Icon(9, 0, -30, 1) == 174);
        check("hint: right + direction -> fork right 173", CpTurns.g70Hint("\uC624\uB978\uCABD \uBC29\uD5A5") == 173);
        check("hint: left exit -> 82", CpTurns.g70Hint("\uC67C\uCABD \uCD9C\uAD6C") == 82);
        check("hint: both or neither side -> none",
                CpTurns.g70Hint("\uC67C\uCABD \uC624\uB978\uCABD") == 0 && CpTurns.g70Hint("\uC9C1\uC9C4") == 0 && CpTurns.g70Hint(null) == 0);
        check("GPM direction: 67 -> 90, 174 -> 330, 65 -> 0",
                CpTurns.direction(67) == 90f && CpTurns.direction(174) == 330f && CpTurns.direction(65) == 0f);
        check("icon: right at 75 degrees (table: 0) -> 67", CpTurns.icon(2, 0, 0, 75, 1) == 0 && CpTurns.g70Icon(2, 0, 75, 1) == 67);
        check("icon: left at -135 -> 69", CpTurns.g70Icon(1, 0, -135, 1) == 69);
        check("icon: roundabout exit (30) at -90 -> 70", CpTurns.g70Icon(30, 0, -90, 1) == 70);
        check("icon: U-turn when possible (26) -> 72", CpTurns.g70Icon(26, 0, 0, 1) == 72);
        check("icon: arrive end of directions (27) -> 112, arrived -> 3",
                CpTurns.g70Icon(27, 0, 0, 1) == 112 && CpTurns.g70Icon(27, 0, 0, 2) == 3);
        check("icon: the table's own answer wins (right 60 -> 66)", CpTurns.icon(2, 0, 0, 60, 1) == 66);

        // 2.2.0: with g70_debug off (the default) nothing per message is logged
        CpTurns.debug = false;
        Cluster c5 = new Cluster();
        CpTurns q = new CpTurns(new android.content.Context(), c5, null);
        c = c5;
        clear();
        q.routeGuidance(korean(0));
        q.maneuver(man(1, 2, "", 100, 2, 90));
        q.routeGuidance(maps(1, i(1), "100"));
        q.routeGuidance(maps(1, i(1), "90"));
        boolean noisy = false;
        for (String l : Dipo.logs) if (l.contains("dbg")) noisy = true;
        check("quiet log: no dbg lines, the turn still sent", !noisy && has("tbt 67 0 0.0 90.0 0 0 0 0 0"));
        check("quiet log: changes still logged (guiding app)", logged("route guidance state 1 from Apple Maps"));

        System.out.println(fail == 0 ? "G70 turn-by-turn: all passed" : "G70 turn-by-turn: FAILED");
        System.exit(fail);
    }
}
