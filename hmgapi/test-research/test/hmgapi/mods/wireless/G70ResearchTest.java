package hmgapi.mods.wireless;

import hmgapi.hook.ModContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Host test of the G70 research log outside DiPO (src-patches/07): car navigation calls, iAP2 server features. */
public final class G70ResearchTest {
    static int fails;

    static void check(String what, boolean ok, String detail) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what + (ok ? "" : "\n      " + detail));
        if (!ok) fails++;
    }

    /** NavigationCluster.TurnList as the car has it (fields only) */
    static final class TurnList {
        int no = 1, type = 67, unit = 2;
        float distance = 0.4f;
        String name = "가나로";
    }

    /** Iap2InitInformationMsg as the car has it (a few of its fields) */
    static final class InitInfo {
        private static final int TRUE = 1;
        private static int iAP2UsedRouteGuidance = 1;
        private static int iAP2DevNotiUsedMask = 0x30;
        private static String iAP2Serial = "SERIAL123";
        private static final String PROPERTY_ROUTE = "persist.sys.iap2.route";
        private static final String PROPERTY_CP_ADDRESS = "persist.iap2.cp.address";
    }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("g70navi").toFile();
        android.content.Context.cache = dir;
        ModContext mod = new ModContext();
        check("navigation log on", G70Research.navi(mod, new android.content.Context(), true), mod.logs.toString());
        Object[] tbt = { 67, 0, 90.0f, 0.4f, 0, 0, 2, 0, 0 };
        G70Research.naviCall("update_tbt_info", tbt, false);
        G70Research.naviCall("update_tbt_info", new Object[] { 67, 0, 90.0f, 0.3f, 0, 0, 2, 0, 0 }, false);   // distance only
        G70Research.naviCall("update_tp_turn_list", new Object[] { new Object[] { new TurnList() } }, true);
        G70Research.naviCall("update_lane_info", new Object[] { new byte[] { (byte) 0xf1, 3, 0x10 } }, false);
        for (int k = 0; k < 70; k++) G70Research.naviCall("update_remain_dis", new Object[] { 1.5f - k * 0.01f, 0 }, false);
        String f = new String(Files.readAllBytes(new File(dir, "g70-navi.log").toPath()), StandardCharsets.UTF_8);
        check("a call with its arguments", f.contains("navi update_tbt_info #1: [67, 0, 90.0, 0.4, 0, 0, 2, 0, 0]"), f);
        check("a distance-only change isn't a new line", !f.contains("update_tbt_info #2"), f);
        check("objects by their fields; skipped while the phone guides", f.contains("update_tp_turn_list #1 (skipped, phone guides): [[TurnList{no=1, type=67, unit=2, distance=0.4, name=\"가나로\""), f);
        check("lane frames as hex", f.contains("update_lane_info #1: [hex(3):f10310]"), f);
        int n = f.split("navi update_remain_dis").length - 1;
        check("countdown calls: the first 3, then one in 60", n == 4, "lines: " + n);
        check("first call of a kind to HMG-API's log", mod.logs.toString().contains("car navigation calls update_lane_info (first)"), mod.logs.toString());

        hmgapi.hook.Points.initInfo = InitInfo.class;
        android.os.SystemProperties.values.put("persist.sys.iap2.route", "1");
        android.os.SystemProperties.values.put("persist.iap2.cp.address", "AA-BB-CC-DD-EE-FF");
        G70Research.initInfo(mod, null);
        String l = mod.logs.toString();
        check("iAP2 server numbers", l.contains("iAP2UsedRouteGuidance=1") && l.contains("iAP2DevNotiUsedMask=48") && !l.contains("TRUE="), l);
        check("iAP2 switches, not the address", l.contains("persist.sys.iap2.route=1") && !l.contains("AA-BB"), l);
        check("no names or serials", !l.contains("SERIAL123"), l);
        int before = mod.logs.size();
        G70Research.initInfo(mod, null);
        check("unchanged: not logged again", mod.logs.size() == before, "logged again");
        G70Research.action(mod, "com.ev6.cpwl.BT_ATTACHED");
        G70Research.action(mod, "com.ev6.cpwl.BT_ATTACHED");
        check("broadcasts: the first of each", mod.logs.toString().split("got broadcast").length == 2, mod.logs.toString());

        G70Research.on = false;
        G70Research.navi(mod, new android.content.Context(), false);
        check("off deletes the log", !new File(dir, "g70-navi.log").exists(), "still there");
        System.out.println(fails == 0 ? "research log (navigation, iAP2 server): all passed" : fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
