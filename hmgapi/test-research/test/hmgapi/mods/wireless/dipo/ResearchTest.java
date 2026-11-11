package hmgapi.mods.wireless.dipo;

import com.tcc.iap2.Iap2NotiMsg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Host test of the G70 research log in DiPO (src-patches/07): iAP2 packets, notifications, masking. */
public final class ResearchTest {
    static int fails;

    static void check(String what, boolean ok, String detail) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what + (ok ? "" : "\n      " + detail));
        if (!ok) fails++;
    }

    static int lines(String all, String part) {
        int n = 0;
        for (int i = all.indexOf(part); i >= 0; i = all.indexOf(part, i + part.length())) n++;
        return n;
    }

    static String file() throws Exception {
        return new String(Files.readAllBytes(new File(android.content.Context.cache, "g70-research.log").toPath()), StandardCharsets.UTF_8);
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

    /** a link packet (session 1) holding one control message */
    static byte[] packet(int msgId, byte[] params) {
        byte[] msg = cat(new byte[] { 0x40, 0x40 }, u16(params.length + 6), u16(msgId), params);
        byte[] head = cat(new byte[] { (byte) 0xff, 0x5a }, u16(9 + msg.length + 1), new byte[] { 0x40, 1, 1, 1, 0 });
        return cat(head, msg, new byte[] { 0 });
    }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("g70research").toFile();
        android.content.Context.cache = dir;
        Research.init(new android.content.Context(), true);
        check("log on", Research.on && Dipo.logs.toString().contains("research: log on"), Dipo.logs.toString());

        // a RouteGuidanceUpdate from the phone over the CarPlay channel
        byte[] rg = packet(0x5201, cat(param(0, u16(0)), param(1, new byte[] { 1 }), param(4, "Seoul".getBytes(StandardCharsets.UTF_8))));
        Research.iap("cp<", rg, rg.length);
        String f = file();
        check("message id, name, parameter ids and sizes", f.contains("iap cp< 0x5201 RouteGuidanceUpdate len") && f.contains("params 0:2 1:1 4:5"), f);
        check("no parameter values outside identification", !f.contains("Seoul") && !f.contains("0:2="), f);
        check("first sighting to HMG-API's log", Dipo.logs.toString().contains("iAP2 cp< 0x5201 RouteGuidanceUpdate: first"), Dipo.logs.toString());

        // the head unit's identification: message lists named, a component group opened, long values hidden
        byte[] comp = cat(param(0, u16(7)), param(1, "Genesis G70".getBytes(StandardCharsets.UTF_8)), param(2, u16(40)));
        byte[] ident = packet(0x1d01, cat(param(0, "HeadUnit".getBytes(StandardCharsets.UTF_8)),
                param(6, cat(u16(0x5200), u16(0x5203))), param(7, cat(u16(0x5201), u16(0x5202))),
                param(9, u16(1000)), param(30, comp), param(17, cat(param(0, u16(3)), param(1, new byte[] { 1, 2, 3, 4, 5, 6 })))));
        Research.iap("cp>", ident, ident.length);
        f = file();
        check("identification: messages it sends", f.contains("6:4={0x5200/StartRouteGuidanceUpdates 0x5203/StopRouteGuidanceUpdates}"), f);
        check("identification: messages it takes", f.contains("7:4={0x5201/RouteGuidanceUpdate 0x5202/RouteGuidanceManeuverUpdate}"), f);
        check("identification: small numbers", f.contains("9:2=1000"), f);
        check("identification: a component's parameters", f.contains("30:27=( 0:2=7 1:11 2:2=40 )") && f.contains("17:16=( 0:2=3 1:6 )"), f);
        check("identification: no names or addresses", !f.contains("Genesis") && !f.contains("HeadUnit") && !f.contains("1:6="), f);

        // two packets in one read, an ACK, the detect sequence, something else
        byte[] two = cat(packet(0x5001, param(1, new byte[] { 2 })), packet(0x5202, param(1, u16(3))));
        Research.iap("bt<", two, two.length);
        byte[] ack = cat(new byte[] { (byte) 0xff, 0x5a }, u16(9), new byte[] { 0x40, 2, 2, 0, 0 });
        Research.iap("bt<", ack, ack.length);
        Research.iap("bt<", new byte[] { (byte) 0xff, 0x55, 2, 0, (byte) 0xee, 0x10 }, 6);
        Research.iap("bt<", new byte[] { 0x12, 0x34, 0x56 }, 3);
        f = file();
        check("two packets in one read", f.contains("bt< 0x5001 NowPlayingUpdate") && f.contains("bt< 0x5202 RouteGuidanceManeuverUpdate"), f);
        check("something that isn't a packet", f.contains("iap bt< other: 3 bytes starting 123456"), f);
        Research.MINUTE_SUMMARY.run();
        f = file();
        check("counts per minute", f.contains("counts 60s:") && f.contains("bt< detect=1") && f.contains("bt< link=1") && f.contains("cp< 0x5201=1, cp> 0x1d01=1"), f.replace("\n", "\n      "));
        // a message cut short in the read: the id still counts
        byte[] cut = packet(0x4155, param(1, new byte[20]));
        Research.iap("cp<", cut, 17);
        Research.totals("test");
        check("a cut message still counts; totals to HMG-API's log", Dipo.logs.toString().contains("cp< 0x4155=1"), Dipo.logs.toString());

        // notifications: route guidance in full, texts kept, numbers and addresses masked, groups opened
        Iap2NotiMsg m = new Iap2NotiMsg();
        Iap2NotiMsg.Iap2ObjGroup g = m.getItemGroup();
        Iap2NotiMsg inner = new Iap2NotiMsg();
        inner.getItemGroup().add(0, 10, new String[] { "555-123-4567" }).add(1, 8, new int[] { 3 });
        g.add(1, 8, new int[] { 1 }).add(4, 10, new String[] { "서울역" }).add(19, 10, new String[] { "TMAP" })
                .add(7, 6, new int[] { 1500 }).add(5, 7, new long[] { 820000000L })
                .add(9, 10, new String[] { "phone AA-BB-CC-DD-EE-FF" }).add(12, 13, new Iap2NotiMsg.Iap2ObjGroup[] { inner.getItemGroup() })
                .add(13, 11, new byte[] { 1, 2, (byte) 0xab });
        Research.noti("rg", 120, 4, 0, m);
        f = file();
        check("route guidance: every item with its value", f.contains("noti rg 120 a1 4 a2 0: 1/t8=[1] 4/t10=\"서울역\" 19/t10=\"TMAP\" 7/t6=[1500] 5/t7=[820000000]"), f);
        check("phone number masked", f.contains("\"###-###-##67\""), f);
        check("address masked", f.contains("phone xx:xx:xx:xx:xx:xx") && !f.contains("AA-BB"), f);
        check("group and blob", f.contains("12/t13={ 0/t10=") && f.contains("1/t8=[3] }") && f.contains("13/t11=hex:0102ab"), f);
        for (int k = 0; k < 300; k++) Research.noti("dipo", 77, 0, 0, m);
        f = file();
        int n = f.split("noti dipo 77 a1").length - 1;
        check("other notifications: the first 200, then one in 20", n == 205, "lines: " + n);
        check("first notification of a kind to HMG-API's log", Dipo.logs.toString().contains("dipo listener: notification 77 first (a1 0, a2 0, 8 items)"), Dipo.logs.toString());
        check("mask keeps plain text", Research.mask("강남대로 123").equals("강남대로 123") && Research.mask("A1B2C3D4E5F60718").equals("<id>"), Research.mask("강남대로 123") + " " + Research.mask("A1B2C3D4E5F60718"));

        // 2.7.0: route guidance asked for and the phone sends none (a map app that doesn't give iOS its turns)
        Dipo.logs.clear();
        Research.rgAnsweredAt = 0;   // the packets above answered on the real clock
        Research.routeGuidance("cp>", 0x5200, 1000);
        Research.routeGuidance("cp>", 0xfffb, 15000);
        check("no route guidance: not said before 20 s", !Dipo.logs.toString().contains("sends no route guidance"), Dipo.logs.toString());
        Research.routeGuidance("cp>", 0xfffb, 22000);
        Research.routeGuidance("cp>", 0xfffb, 40000);
        check("no route guidance 20 s after the request: said once, in HMG-API's log", lines(Dipo.logs.toString(), "sends no route guidance") == 1, Dipo.logs.toString());
        Dipo.logs.clear();
        Research.routeGuidance("cp>", 0x5200, 50000);
        Research.routeGuidance("cp<", 0x5201, 50100);
        Research.routeGuidance("cp>", 0xfffb, 90000);
        check("the phone answered: nothing said", !Dipo.logs.toString().contains("sends no route guidance"), Dipo.logs.toString());

        // 2.7.1 (src-patches/25): an unanswered request is asked once more (Stop, then Start), once per navigation
        final int[] asked = { 0 };
        Research.rgRetry = new Runnable() {
            @Override
            public void run() {
                synchronized (asked) {
                    asked[0]++;
                    asked.notifyAll();
                }
            }
        };
        Dipo.logs.clear();
        Research.rgRetried = false;
        Research.routeGuidance("cp>", 0x5200, 100000);
        Research.routeGuidance("cp>", 0xfffb, 121000);
        synchronized (asked) {
            if (asked[0] == 0) asked.wait(2000);
        }
        check("unanswered: asked once more, and said so", asked[0] == 1 && lines(Dipo.logs.toString(), "asking once more") == 1
                && Dipo.logs.toString().contains("CarPlay HUD setting"), Dipo.logs.toString());
        Research.routeGuidance("cp>", 0x5203, 121300);   // our own Stop and Start go out
        Research.routeGuidance("cp>", 0x5200, 121500);
        Research.routeGuidance("cp>", 0xfffb, 142000);
        Thread.sleep(300);
        check("still unanswered after asking again: said, not asked a third time",
                asked[0] == 1 && lines(Dipo.logs.toString(), "asked twice") == 1 && lines(Dipo.logs.toString(), "asking once more") == 1, Dipo.logs.toString());
        Research.rgDipoStopped();   // DiPO ends that navigation
        Research.routeGuidance("cp>", 0x5200, 200000);
        Research.routeGuidance("cp>", 0xfffb, 221000);
        synchronized (asked) {
            if (asked[0] == 1) asked.wait(2000);
        }
        check("the next navigation (after DiPO's stop) is asked once more again", asked[0] == 2, "asked " + asked[0]);
        Dipo.logs.clear();
        Research.routeGuidance("cp>", 0x5200, 300000);
        Research.routeGuidance("cp<", 0x5201, 300400);
        Research.routeGuidance("cp>", 0xfffb, 330000);
        Thread.sleep(300);
        check("answered: not asked again", asked[0] == 2 && !Dipo.logs.toString().contains("sends no route guidance"), Dipo.logs.toString());
        Research.rgRetry = null;

        // off: the files go
        Research.on = false;
        Research.init(new android.content.Context(), false);
        check("off deletes the log", !new File(dir, "g70-research.log").exists(), "still there");
        System.out.println(fails == 0 ? "research log (DiPO): all passed" : fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
