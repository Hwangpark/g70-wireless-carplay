package hmgapi.mods.wireless.dipo;

import com.tcc.iap2.Iap2NotiMsg;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

import java.io.ByteArrayOutputStream;

/**
 * Host test of the G70 lanes (src-patches/08): the phone's LaneGuidanceInformation (0x5204)
 * read from CarPlay-channel bytes, shown on the cluster when RouteGuidanceUpdate says so,
 * cleared when it doesn't, and the lane test's frames. Sizes as in the 11/04 drive log.
 */
public final class G70LanesTest {
    static int fail;
    static Cluster c;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) {
            fail++;
            System.out.println("  calls: " + c.calls);
            System.out.println("  log: " + Dipo.logs);
            System.out.println("  research: " + Dipo.research);
        }
    }

    static byte[] u16(int v) {
        return new byte[] { (byte) (v >> 8), (byte) v };
    }

    static byte[] param(int id, byte[] data) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(u16(data.length + 4), 0, 2);
        o.write(u16(id), 0, 2);
        o.write(data, 0, data.length);
        return o.toByteArray();
    }

    static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    static byte[] packet(int msgId, byte[] params) {
        byte[] msg = cat(new byte[] { 0x40, 0x40 }, u16(params.length + 6), u16(msgId), params);
        return cat(new byte[] { (byte) 0xff, 0x5a }, u16(9 + msg.length + 1), new byte[] { 0x40, 1, 1, 1, 0 }, msg, new byte[] { 0 });
    }

    /** one lane group as the phone sends it: 17 bytes, or 23 with a highlight angle */
    static byte[] lane(int index, int status, Integer highlight, int... angles) {
        byte[] a = new byte[0];
        for (int x : angles) a = cat(a, u16(x & 0xffff));
        byte[] g = cat(param(0, u16(index)), param(1, new byte[] { (byte) status }), param(2, a));
        if (highlight != null) g = cat(g, param(3, u16(highlight & 0xffff)));
        return param(2, g);
    }

    static final int U16 = 5, ENUM = 8, BOOL = 9, STR = 10;

    static Iap2NotiMsg rg(int state, int laneIndex, int showing) {
        Iap2NotiMsg m = new Iap2NotiMsg();
        m.getItemGroup().add(0, U16, new int[] { 5 }).add(1, ENUM, new int[] { state }).add(16, U16, new int[] { laneIndex })
                .add(17, U16, new int[] { 3 }).add(18, BOOL, new int[] { showing }).add(19, STR, new String[] { "TMAP" });
        return m;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        int end = b.length;
        while (end > 1 && b[end - 1] == 0) end--;
        for (int i = 0; i < end; i++) s.append(String.format("%02x", b[i] & 0xff));
        return s.toString();
    }

    static byte[] frame(int[] status, int[][] arrows) {
        byte[] f = new byte[81];
        f[0] = (byte) 0xF1;
        f[1] = (byte) status.length;
        for (int k = 0; k < status.length; k++) {
            f[k + 3] = (byte) status[k];
            for (int j = 0; j < arrows[k].length; j++) f[k * 3 + 19 + j] = (byte) arrows[k][j];
        }
        return f;
    }

    static boolean sent(byte[] f) {
        return c.calls.contains("lanes " + hex(f));
    }

    public static void main(String[] a) throws Exception {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.lanesOn = true;
        Dipo.turns = t;

        // 4 lanes for index 0: left, straight, straight with a right (preferred, highlight straight), right
        byte[] p = packet(0x5204, cat(param(0, u16(5)), param(1, u16(0)), param(3, "1 ".getBytes("UTF-8")),
                lane(0, 1, null, -90), lane(1, 1, null, 0), lane(2, 2, 0, 0, 90), lane(3, 1, null, 90)));
        byte[] want = frame(new int[] { 1, 1, 2, 1 }, new int[][] { { 7 }, { 1 }, { 1, 3 }, { 3 } });

        t.routeGuidance(rg(1, 0, 1));
        check("lanes showing but none from the phone yet: nothing sent", !c.calls.toString().contains("lanes "));
        G70Lanes.feed(p, p.length);
        check("the phone's lanes to the cluster: status, highlight 2, arrows (left 7, straight 1, right 3)", sent(want));
        check("the reading goes to the research log", Dipo.research.size() == 1
                && Dipo.research.get(0).contains("index 0") && Dipo.research.get(0).contains("[status 2 hi 0 angles 0 90]"));
        int n = c.calls.size();
        t.routeGuidance(rg(1, 0, 1));
        check("the same lanes again: not resent", c.calls.size() == n);
        t.routeGuidance(rg(1, 0, 0));
        check("RouteGuidanceUpdate says no lanes: cleared once", c.calls.contains("lanes f1"));
        n = c.calls.size();
        t.routeGuidance(rg(1, 0, 0));
        check("still no lanes: not cleared again", c.calls.size() == n);
        t.routeGuidance(rg(1, 1, 1));
        check("lanes showing for an index the phone hasn't sent: nothing", c.calls.size() == n);

        // a packet with two messages, the lanes second; highlight value from the setting
        c.calls.clear();
        t.laneHi = 3;
        byte[] two = cat(packet(0x5201, param(1, new byte[] { 1 })),
                packet(0x5204, cat(param(0, u16(5)), param(1, u16(1)), lane(0, 1, null, 0), lane(1, 1, 45, 45))));
        G70Lanes.feed(two, two.length);
        check("second message in a read; the highlight status from the setting (3); slight right 2",
                sent(frame(new int[] { 1, 3 }, new int[][] { { 1 }, { 2 } })));

        // the route ends: lanes forgotten
        t.stop();
        c.calls.clear();
        t.routeGuidance(rg(1, 1, 1));
        check("after the route ends: old lanes not shown again", !c.calls.toString().contains("lanes f102"));

        // off: nothing read
        CpTurns off = new CpTurns(new android.content.Context(), c, null);
        Dipo.turns = off;
        c.calls.clear();
        Dipo.research.clear();
        off.routeGuidance(rg(1, 0, 1));
        G70Lanes.feed(p, p.length);
        check("lanes off, research log on (2.3.0): read into the log, not sent", Dipo.research.size() == 1 && !c.calls.toString().contains("lanes "));
        Dipo.research.clear();
        Dipo.researchFlag = false;
        G70Lanes.feed(p, p.length);
        Dipo.researchFlag = true;
        check("lanes and research log off: not read", Dipo.research.isEmpty());

        // the lane test's frames
        check("lane test: 10 steps", CpTurns.testCodes("lanes").length == 10 && CpTurns.LANE_TEST.length == 10);
        check("lane test step 2: lane 3 status 2", hex(CpTurns.laneTestFrame(2)).equals(hex(frame(new int[] { 1, 1, 2, 1 },
                new int[][] { { 7 }, { 1 }, { 1 }, { 3 } }))));
        check("lane test step 10: cleared", hex(CpTurns.laneTestFrame(10)).equals("f1"));
        Cluster c2 = new Cluster();
        c = c2;
        CpTurns lt = new CpTurns(new android.content.Context(), c2, null);
        lt.laneTest = true;
        lt.testCodes = CpTurns.testCodes("lanes");
        lt.testAt = 0;
        lt.stepTest();
        check("lane test step 1 on the cluster: the frame, a right turn at 1 m, the HUD distance 1, LANE 1",
                c2.calls.contains("lanes " + hex(CpTurns.laneTestFrame(1))) && c2.calls.toString().contains("road [LANE 1]")
                && c2.calls.contains("2c 0 0 1 0 0 0 0 0 0"));
        lt.stopIconTest("driving restriction");
        check("lane test stopped: lanes cleared", c2.calls.contains("lanes f1"));

        // the HUD junction test (src-patches/09)
        Cluster c3 = new Cluster();
        c = c3;
        CpTurns jt = new CpTurns(new android.content.Context(), c3, null);
        jt.junctionTest = true;
        jt.testCodes = CpTurns.testCodes("junctions");
        check("junction test: 24 steps", jt.testCodes.length == 24);
        jt.testAt = 2;
        jt.stepTest();
        check("junction test step 3: right turn, side street bit 1, bar 16, the step as distance; JCT 3; HUD distance 3",
                c3.calls.contains("tbt 67 1 0.0 3.0 0 0 0 16 0") && c3.calls.toString().contains("road [JCT 3]")
                && c3.calls.contains("2c 0 0 3 0 0 0 0 0 0"));
        check("lanes off by default", !new CpTurns(new android.content.Context(), c3, null).lanesOn);

        // guidance end (src-patches/10): "no guidance" as the car's navigation sends it, once
        Cluster c4 = new Cluster();
        c = c4;
        CpTurns g = new CpTurns(new android.content.Context(), c4, null);
        Iap2NotiMsg man = new Iap2NotiMsg();
        man.getItemGroup().add(0, U16, new int[] { 5 }).add(1, U16, new int[] { 0 }).add(3, ENUM, new int[] { 2 })
                .add(4, STR, new String[] { "a road" }).add(5, 6, new int[] { 300 }).add(7, ENUM, new int[] { 2 });
        g.maneuver(man);
        Iap2NotiMsg on = new Iap2NotiMsg();
        on.getItemGroup().add(0, U16, new int[] { 5 }).add(1, ENUM, new int[] { 1 }).add(13, U16, new int[] { 0 })
                .add(10, 6, new int[] { 300 }).add(11, STR, new String[] { "300" }).add(12, ENUM, new int[] { 2 })
                .add(19, STR, new String[] { "TMAP" });
        g.routeGuidance(on);
        check("a turn on the cluster", c4.calls.toString().contains("tbt 67 "));
        c4.calls.clear();
        Iap2NotiMsg off0 = new Iap2NotiMsg();
        off0.getItemGroup().add(0, U16, new int[] { 5 }).add(1, ENUM, new int[] { 0 }).add(19, STR, new String[] { "TMAP" });
        g.routeGuidance(off0);
        check("guidance ended: turn type 8 and no-value trip and HUD distance, as the car's navigation",
                c4.calls.contains("tbt 8 0 0.0 0.0 0 0 0 0 -1") && c4.calls.contains("sub 0.0 -3 15 255 255 3 3 0.0 0 255")
                && c4.calls.contains("2c 15 15 65535 15 15 65535 15 15 65535"));
        c4.calls.clear();
        g.routeGuidance(off0);
        check("another state 0: no guidance not sent again", !c4.calls.toString().contains("tbt 8 "));
        // a late "guiding" update right after DiPO's stop (arrival 11/04 08:30:16) is ignored
        g.routeGuidance(on);
        c4.calls.clear();
        g.dipoStopped();
        g.stop();
        c4.calls.clear();
        g.routeGuidance(on);
        check("state 1 just after DiPO's stop: the old turn not sent again", !c4.calls.toString().contains("tbt 67 "));

        System.out.println(fail == 0 ? "G70 lanes: all passed" : "G70 lanes: FAILED");
        System.exit(fail);
    }
}
