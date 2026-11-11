package hmgapi.mods.wireless.dipo;

import com.tcc.iap2.Iap2NotiMsg;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/**
 * Host test of the G70 stock style (src-patches/11): TMAP's turns sent the way the car's own
 * navigation sends them (its log, 11/04 evening and 11/05 morning): the icon far from the
 * turn, the junction picture with its bar near it, the tollgate, the arrival.
 */
public final class G70StockTest {
    static int fail;
    static Cluster c;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) {
            fail++;
            System.out.println("  calls: " + c.calls);
            System.out.println("  log: " + Dipo.logs);
        }
    }

    static final int U16 = 5, U32 = 6, ENUM = 8, BOOL = 9, STR = 10;

    /** a TMAP maneuver: no angles; distance in metres, units 2 (m) or 0 (km) */
    static Iap2NotiMsg man(int index, int type, String desc, String after, int distance, int units) {
        Iap2NotiMsg m = new Iap2NotiMsg();
        m.getItemGroup().add(0, U16, new int[] { 5 }).add(1, U16, new int[] { index }).add(2, STR, new String[] { desc })
                .add(3, ENUM, new int[] { type }).add(4, STR, new String[] { after }).add(5, U32, new int[] { distance })
                .add(7, ENUM, new int[] { units }).add(8, ENUM, new int[] { 0 }).add(9, ENUM, new int[] { 0 });
        return m;
    }

    /** an Apple Maps maneuver: the other roads' angles (item 10, one item each) and the exit angle (item 11) */
    static Iap2NotiMsg manA(int index, int type, String after, int distance, int exit, int... arms) {
        Iap2NotiMsg m = man(index, type, "d", after, distance, 2);
        for (int a : arms) m.getItemGroup().add(10, 1, new int[] { a });
        m.getItemGroup().add(11, 1, new int[] { exit });
        return m;
    }

    /** a Naver Map maneuver (Gen5W KR's study of Naver Map 6.12.1): the type, the junction (0 or 1) and the exit angle only */
    static Iap2NotiMsg manN(int index, int type, String after, int distance, int exit, int junction) {
        Iap2NotiMsg m = man(index, type, "d", after, distance, 2);
        m.getItemGroup().add(9, ENUM, new int[] { junction }).add(11, 1, new int[] { exit });
        return m;
    }

    /** a RouteGuidanceUpdate: state, current maneuver, metres to it (item 10, 11, 12 as TMAP) */
    static Iap2NotiMsg rg(int state, int current, int meters) {
        String s = meters >= 1000 ? String.valueOf(Math.round(meters / 100.0) / 10.0) : String.valueOf(meters);
        Iap2NotiMsg m = new Iap2NotiMsg();
        m.getItemGroup().add(0, U16, new int[] { 5 }).add(1, ENUM, new int[] { state }).add(10, U32, new int[] { meters })
                .add(11, STR, new String[] { s }).add(12, ENUM, new int[] { meters >= 1000 ? 0 : 2 })
                .add(13, U16, new int[] { current }).add(19, STR, new String[] { "TMAP" });
        return m;
    }

    static boolean has(String s) {
        return c.calls.contains(s);
    }

    static boolean starts(String s) {
        for (String x : c.calls) if (x.startsWith(s)) return true;
        return false;
    }

    public static void main(String[] a) throws Exception {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.stockStyle = true;

        // a right turn 800 m ahead, then a left 200 m after it, then a tollgate
        t.maneuver(man(0, 2, "a road", "Gana-ro", 800, 2));
        t.maneuver(man(1, 1, "200m", "Dara-ro", 200, 2));
        t.maneuver(man(2, 3, "Maba TG / toll 2,400", "Maba TG", 3000, 0));
        t.routeGuidance(rg(1, 0, 800));
        check("far: the icon, side streets 0, direction none (1912.5), combined 15, bar none (-1)",
                has("tbt 67 0 1912.5 800.0 0 15 0 0 -1"));
        check("far: no name above the picture", has("road []"));
        check("far: the HUD list with 3 icons (right, left, tollgate 178) and the road name", starts("hud f2034346b2"));
        check("far: the HUD distances (800 m, 200 m, 3.0 km)", has("2c 0 0 800 0 0 200 1 0 3"));

        c.calls.clear();
        t.routeGuidance(rg(1, 0, 300));
        // 2.6.6: the bar on the car's navigation's scale (its pictures: 90 above about 320 m, 80 at 300 m)
        check("300 m: the junction picture for a right turn (13, side streets 2065, 90 deg), bar 80",
                has("tbt 13 2065 90.0 300.0 0 0 0 80 0"));
        check("300 m: the road name above it, the list empty", has("road [Gana-ro]") && has("hud f2"));
        c.calls.clear();
        t.routeGuidance(rg(1, 0, 150));
        check("150 m: the bar at 40 (the car's navigation: 150 m 40)", has("tbt 13 2065 90.0 150.0 0 0 0 40 0"));
        check("150 m: the name and list not resent", !starts("road") && !starts("hud"));
        c.calls.clear();
        t.routeGuidance(rg(1, 0, 10));
        check("10 m: the bar at 10", has("tbt 13 2065 90.0 10.0 0 0 0 10 0"));

        // the next maneuver: the left 200 m on, straight into its picture
        c.calls.clear();
        t.routeGuidance(rg(1, 1, 200));
        check("the left turn at 200 m: its picture (13, 2065, 270 deg) starting at 50, as the car's navigation's late pictures (180-200 m: 50)",
                has("tbt 13 2065 270.0 200.0 0 0 0 50 0")
                && has("road [Dara-ro]"));

        // the tollgate: 178 far, then with a bar from 500 m
        c.calls.clear();
        t.routeGuidance(rg(1, 2, 2400));
        check("tollgate far: icon 178", has("tbt 178 0 1912.5 2.4 0 15 1 0 -1"));
        c.calls.clear();
        t.routeGuidance(rg(1, 2, 400));
        check("tollgate at 400 m: 178 with bar 30 (the car's navigation: 400 m 30) and its name", has("tbt 178 0 1912.5 400.0 0 15 0 30 0") && has("road [Maba TG]"));

        // a highway exit right: 164 far, the picture 17 from 1 km
        CpTurns h = new CpTurns(new android.content.Context(), c, null);
        h.stockStyle = true;
        c.calls.clear();
        h.maneuver(man(5, 23, "7.5km", "Saja IC / Chaka", 7505, 0));
        h.routeGuidance(rg(1, 5, 7500));
        check("highway exit right far: 164 (the car's navigation's icon)", has("tbt 164 0 1912.5 7.5 0 15 1 0 -1"));
        c.calls.clear();
        h.routeGuidance(rg(1, 5, 900));
        check("highway exit right at 900 m: picture 17 at 90 deg, bar 80 (the car's navigation: 1.0 km 90, 900 m 80)", has("tbt 17 0 90.0 900.0 0 0 0 80 0"));

        // straight ahead: no picture, icon only, even near
        CpTurns s = new CpTurns(new android.content.Context(), c, null);
        s.stockStyle = true;
        c.calls.clear();
        s.maneuver(man(7, 3, "1.3km", "Net rest area", 1331, 0));
        s.routeGuidance(rg(1, 7, 200));
        check("straight (a rest area): the icon only", has("tbt 65 0 1912.5 200.0 0 15 0 0 -1"));

        // arrival, once; then the end
        c.calls.clear();
        t.routeGuidance(rg(2, 2, 0));
        t.routeGuidance(rg(2, 2, 0));
        int arrived = 0;
        for (String x : c.calls) if (x.startsWith("tbt 3 65535 1912.5")) arrived++;
        check("arrived: type 3 with no values, once; destination and name cleared",
                arrived == 1 && has("tbt 3 65535 1912.5 65535.15 -3 15 15 0 -1") && has("dest 0 []") && has("road []"));
        c.calls.clear();
        Iap2NotiMsg off = new Iap2NotiMsg();
        off.getItemGroup().add(0, U16, new int[] { 5 }).add(1, ENUM, new int[] { 0 }).add(19, STR, new String[] { "TMAP" });
        t.routeGuidance(off);
        check("then no guidance (type 8)", has("tbt 8 0 0.0 0.0 0 0 0 0 -1"));

        // 2.4.1: an IC TMAP sends as straight on is passed over: the next maneuver shows, with both distances;
        // a slight right onto a ramp followed by the exit of the same name is that exit (164)
        CpTurns p = new CpTurns(new android.content.Context(), c, null);
        p.stockStyle = true;
        c.calls.clear();
        p.maneuver(man(10, 3, "1.5km", "Set IC", 1514, 0));
        p.maneuver(man(11, 50, "9.8km", "Tapa-gyo / Hana Park", 9776, 0));
        p.maneuver(man(12, 23, "305m", "Tapa-gyo / Hana Park", 305, 2));
        p.routeGuidance(rg(1, 10, 1500));
        check("passed IC: the exit after it, 1.5 + 9.8 km, as the highway exit icon 164", has("tbt 164 0 1912.5 11.3 0 15 1 0 -1"));
        c.calls.clear();
        p.routeGuidance(rg(1, 11, 900));
        check("that exit at 900 m: picture 17 at 90 deg", has("tbt 17 0 90.0 900.0 0 0 0 80 0"));
        c.calls.clear();
        p.routeGuidance(rg(1, 11, 748));
        check("2.6.6: near distances as the car's navigation: hundreds from 300 m (748 -> 700), bar 60 (its 700 m: 60)", has("tbt 17 0 90.0 700.0 0 0 0 60 0"));
        c.calls.clear();
        p.routeGuidance(rg(1, 11, 1000));
        check("2.6.6: from 1 km in km with one decimal (1.0 km), bar 90", has("tbt 17 0 90.0 1.0 0 0 1 90 0") && starts("2c 1 0 1 "));
        c.calls.clear();
        p.routeGuidance(rg(1, 11, 268));
        check("2.6.6: tens of metres under 300 m (268 -> 260)", has("tbt 17 0 90.0 260.0 0 0 0 10 0"));

        // 2.4.1: Apple Maps sends the junction's roads: the real shape (side street bits as the car's navigation)
        CpTurns ap = new CpTurns(new android.content.Context(), c, null);
        ap.stockStyle = true;
        c.calls.clear();
        ap.maneuver(manA(0, 2, "A road", 300, 90, 0, -45, -90, -135));
        ap.routeGuidance(rg(1, 0, 300));
        check("Apple Maps right turn with 4 other roads: bits 0, 13, 11, 9 and 4 (10769), 90 deg",
                has("tbt 13 10769 90.0 300.0 0 0 0 80 0"));
        CpTurns ap2 = new CpTurns(new android.content.Context(), c, null);
        ap2.stockStyle = true;
        c.calls.clear();
        ap2.maneuver(manA(0, 2, "A road", 300, 90, -90, 0));
        ap2.routeGuidance(rg(1, 0, 300));
        check("Apple Maps plain crossroad right: 2065, as the car's navigation", has("tbt 13 2065 90.0 300.0 0 0 0 80 0"));
        CpTurns r = new CpTurns(new android.content.Context(), c, null);
        r.stockStyle = true;
        c.calls.clear();
        r.maneuver(man(0, 3, "a", "Road", 748, 2));
        r.routeGuidance(rg(1, 0, 748));
        check("far 748 m: 700 m", has("tbt 65 0 1912.5 700.0 0 15 0 0 -1"));

        // 2.6.2: the name always (setting g70_hud_name "always"): the junction picture with the name at any distance, no bar far away
        CpTurns n = new CpTurns(new android.content.Context(), c, null);
        n.stockStyle = true;
        n.hudNameAlways = true;
        c.calls.clear();
        n.maneuver(man(0, 2, "a road", "Gana-ro", 2400, 0));
        n.maneuver(man(1, 1, "200m", "Dara-ro", 200, 2));
        n.routeGuidance(rg(1, 0, 2400));
        check("name always, 2.4 km: the picture (13, 2065, 90 deg) in km, no bar (-1), the name above, the list empty",
                has("tbt 13 2065 90.0 2.4 0 0 1 0 -1") && has("road [Gana-ro]") && has("hud f2"));
        c.calls.clear();
        n.routeGuidance(rg(1, 0, 250));
        check("name always, 250 m (first inside the picture's 300 m): the bar at 70 (2.6.6: the car's navigation's scale)", has("tbt 13 2065 90.0 250.0 0 0 0 70 0"));
        CpTurns ns = new CpTurns(new android.content.Context(), c, null);
        ns.stockStyle = true;
        ns.hudNameAlways = true;
        c.calls.clear();
        ns.maneuver(man(7, 3, "1.3km", "Dul-ro", 1331, 0));
        ns.routeGuidance(rg(1, 7, 1300));
        check("name always, straight (no picture): the icon, and the name too", has("tbt 65 0 1912.5 1.3 0 15 1 0 -1") && has("road [Dul-ro]"));

        // 2.7.0: setting kp: the junction picture and the bar as Korea Projection computes them, in the stock flow
        CpTurns k = new CpTurns(new android.content.Context(), c, null);
        k.stockStyle = true;
        k.kpStyle = true;
        c.calls.clear();
        k.maneuver(man(0, 2, "a road", "Road A", 800, 2));
        k.maneuver(man(1, 1, "200m", "Road B", 200, 2));
        k.maneuver(man(2, 3, "Gate TG / toll", "Gate TG", 3000, 0));
        k.routeGuidance(rg(1, 0, 800));
        check("kp: far from the turn, the icon as in the stock style", has("tbt 67 0 1912.5 800.0 0 15 0 0 -1"));
        c.calls.clear();
        k.routeGuidance(rg(1, 0, 300));
        check("kp: right turn inside 350 m: 13 with only the road taken (bit 4), 90 deg, bar 80 (300 of 350)",
                has("tbt 13 16 90.0 300.0 0 0 0 80 0") && has("road [Road A]"));
        c.calls.clear();
        k.routeGuidance(rg(1, 1, 200));
        check("kp: left turn at 200 m: bit 12, 270 deg, bar 50", has("tbt 13 4096 270.0 200.0 0 0 0 50 0"));
        c.calls.clear();
        k.routeGuidance(rg(1, 2, 400));
        check("kp: a tollgate keeps the car's own 178 and bar", has("tbt 178 0 1912.5 400.0 0 15 0 30 0"));
        check("kp: bar in tenths of D, 10 to 90", CpTurns.kpBar(349, 350) == 90 && CpTurns.kpBar(315, 350) == 90 && CpTurns.kpBar(314, 350) == 80
                && CpTurns.kpBar(34, 350) == 10 && CpTurns.kpBar(1049, 1050) == 90 && CpTurns.kpBar(900, 1050) == 80);
        check("kp: side street bits: straight 0, right 4, left 12, behind none", CpTurns.kpStreetBit(0) == 0 && CpTurns.kpStreetBit(90) == 4
                && CpTurns.kpStreetBit(270) == 12 && CpTurns.kpStreetBit(180) == -1);
        int[] ex = CpTurns.kpDisplay(23, 0, 0, 0, 164), ut = CpTurns.kpDisplay(4, 0, 0, 0, 72), no = CpTurns.kpDisplay(10, 0, 0, 0, 3);
        check("kp: highway exit right 17 at 90 deg, U-turn 31 at 270 deg, no picture for the destination",
                ex[0] == 17 && ex[2] * 7.5f == 90f && ut[0] == 31 && ut[2] * 7.5f == 270f && no == null);
        CpTurns kn = new CpTurns(new android.content.Context(), c, null);
        kn.stockStyle = true;
        kn.kpStyle = true;
        kn.hudNameAlways = true;
        c.calls.clear();
        kn.maneuver(man(0, 2, "a road", "Road A", 2400, 0));
        kn.routeGuidance(rg(1, 0, 2400));
        check("kp with the name always: its picture in km, no bar, the name above", has("tbt 13 16 90.0 2.4 0 0 1 0 -1") && has("road [Road A]"));

        // 2.7.1 (src-patches/25): near-turn hysteresis as Korea Projection 1.7.3 (N-3): the picture
        // starts at its distance (300 m for a city right turn) and ends only 50 m past it
        CpTurns hy = new CpTurns(new android.content.Context(), c, null);
        hy.stockStyle = true;
        hy.maneuver(man(0, 2, "a road", "Road A", 400, 2));
        c.calls.clear();
        hy.routeGuidance(rg(1, 0, 320));
        check("hysteresis: 320 m before the picture started: far (icon)", starts("tbt 67 ") && !starts("tbt 13 "));
        c.calls.clear();
        hy.routeGuidance(rg(1, 0, 290));
        check("hysteresis: 290 m: the picture with its bar", starts("tbt 13 2065 90.0 290.0 ") && !starts("tbt 67 "));
        c.calls.clear();
        hy.routeGuidance(rg(1, 0, 330));
        check("hysteresis: back to 330 m (GPS at a light): the picture stays, bar 90", starts("tbt 13 2065 90.0 ") && has("tbt 13 2065 90.0 300.0 0 0 0 90 0") && !starts("tbt 67 "));
        c.calls.clear();
        hy.routeGuidance(rg(1, 0, 360));
        check("hysteresis: 360 m, more than 50 m past: far again", starts("tbt 67 ") && !starts("tbt 13 "));
        c.calls.clear();
        hy.routeGuidance(rg(1, 0, 330));
        check("hysteresis: 330 m after leaving: stays far until 300 m", starts("tbt 67 ") && !starts("tbt 13 "));
        // 2.7.1: the HUD name is never cut in the middle of an emoji (Korea Projection 1.7.2, M-15)
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 37; i++) longName.append('\uAC00');
        longName.append("\uD83D\uDE00\uB05D");
        c.calls.clear();
        hy.stockHud(67, 0, 0, longName.toString());
        String hud = c.calls.isEmpty() ? "" : c.calls.get(c.calls.size() - 1);
        check("HUD name: 37 Hangul then an emoji at the 76-byte cut: the emoji left out whole", hud.startsWith("hud f2014300") && hud.contains("00ac") && !hud.contains("3dd8"));

        // 2.7.2 (src-patches/26): Naver Map's "N o'clock" turns and roundabouts come as no turn (0) with an exit angle
        CpTurns nv = new CpTurns(new android.content.Context(), c, null);
        nv.stockStyle = true;
        nv.maneuver(manN(0, 0, "Road A", 800, 90, 0));     // 3 o'clock
        nv.maneuver(manN(1, 0, "Road B", 250, -120, 0));   // 8 o'clock
        nv.maneuver(manN(2, 0, "Road C", 800, 270, 1));    // roundabout, its exit at 270 (left)
        nv.maneuver(manN(3, 0, "Road D", 800, 0, 0));      // no turn, no angle
        nv.maneuver(manN(4, 0, "Road E", 800, -45, 0));    // 11 o'clock
        c.calls.clear();
        nv.routeGuidance(rg(1, 0, 800));
        check("Naver 3 o'clock (type 0 at 90 deg), far: the right turn icon 67, not straight 65", has("tbt 67 0 1912.5 800.0 0 15 0 0 -1"));
        c.calls.clear();
        nv.routeGuidance(rg(1, 0, 290));
        check("Naver 3 o'clock near: the right turn picture", starts("tbt 13 2065 90.0 290.0 "));
        c.calls.clear();
        nv.routeGuidance(rg(1, 1, 250));
        check("Naver 8 o'clock (-120 deg) near: the sharp left picture", starts("tbt 13 1041 247.5 250.0 "));
        c.calls.clear();
        nv.routeGuidance(rg(1, 2, 800));
        check("Naver roundabout exit at 270: the left icon 70", starts("tbt 70 "));
        c.calls.clear();
        nv.routeGuidance(rg(1, 3, 800));
        check("no turn without an angle: straight 65 as before", starts("tbt 65 "));
        c.calls.clear();
        nv.routeGuidance(rg(1, 4, 800));
        check("Naver 11 o'clock (-45 deg): the slight left icon 71", starts("tbt 71 "));
        check("turn type by angle: 0 straight, 45 slight, 90 normal, 120 sharp, 180 U-turn",
                CpTurns.g70TurnType(10) == 0 && CpTurns.g70TurnType(45) == 50 && CpTurns.g70TurnType(-45) == 49 && CpTurns.g70TurnType(90) == 2
                && CpTurns.g70TurnType(-90) == 1 && CpTurns.g70TurnType(120) == 48 && CpTurns.g70TurnType(-120) == 47 && CpTurns.g70TurnType(180) == 4);

        // the other styles are unchanged
        CpTurns d = new CpTurns(new android.content.Context(), c, null);
        c.calls.clear();
        d.maneuver(man(0, 2, "a road", "Gana-ro", 800, 2));
        d.routeGuidance(rg(1, 0, 300));
        check("DiPO style unchanged: icon only at 300 m", has("tbt 67 0 0.0 300.0 0 0 0 0 0"));

        System.out.println(fail == 0 ? "G70 stock style: all passed" : "G70 stock style: FAILED");
        System.exit(fail);
    }
}
