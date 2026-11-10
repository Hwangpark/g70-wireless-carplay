package hmgapi.mods.wireless.dipo;

import com.hkmc.bcm.BCMManager;
import com.hkmc.micom.NModeManager;
import com.hkmc.mode.ModeManager;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Host test of G70 2.5.0 (src-patches/13): song and call info asked for once the wireless
 * CarPlay channel is up, the startup agreement pressed only when the owner turned it on
 * (and once), the car's engine and drive mode data to the research log without repeats.
 */
public final class G70ExtraTest {
    static int fail;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) {
            fail++;
            System.out.println("  log: " + Dipo.logs);
        }
    }

    /** stands in for DiPO's IIap2Service proxy: the three calls DiPO makes on USB */
    public static final class FakeIap2 {
        final List<String> calls = new ArrayList<String>();
        /** the server's false while the channel still authenticates (11/05 10:17) */
        int falseFirst;

        public boolean Iap2SvcNowPlayingStart() {
            calls.add("now playing");
            return falseFirst-- <= 0;
        }

        public boolean Iap2SvcTelephonyStartCallState() {
            calls.add("call state");
            return true;
        }

        public boolean Iap2SvcTelephonyStartUpdates() {
            calls.add("telephony");
            return true;
        }
    }

    static String file(File f) throws Exception {
        return f.exists() ? new String(Files.readAllBytes(f.toPath()), "UTF-8") : "";
    }

    static int lines(String all, String part) {
        int n = 0;
        for (String l : all.split("\n")) if (l.contains(part)) n++;
        return n;
    }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("g70extra").toFile();
        android.content.Context.cache = dir;
        android.content.Context c = new android.content.Context();
        G70Extra.now = true;

        // song and call info
        G70Extra.media = true;
        // 2.6.4: call state only with g70_call_state (off by default)
        G70Extra.calls = false;
        FakeIap2 songOnly = new FakeIap2();
        G70Extra.cpChannelUp(songOnly);
        check("2.6.4: call state off: now playing only", songOnly.calls.toString().equals("[now playing]"));
        check("2.6.4: says the call state wasn't asked for", Dipo.logs.toString().contains("call state not asked (setting g70_call_state off)"));
        G70Extra.calls = true;
        FakeIap2 s = new FakeIap2();
        G70Extra.cpChannelUp(s);
        check("channel up: now playing, call state, telephony, in DiPO's order",
                s.calls.toString().equals("[now playing, call state, telephony]"));
        check("logged with each call's result", Dipo.logs.toString().contains("Iap2SvcNowPlayingStart true, Iap2SvcTelephonyStartCallState true"));
        G70Extra.media = false;
        FakeIap2 off = new FakeIap2();
        G70Extra.cpChannelUp(off);
        check("setting off: nothing asked", off.calls.isEmpty());
        G70Extra.media = true;
        Dipo.logs.clear();
        G70Extra.cpChannelUp(null);
        check("no iAP2 server: said so, no crash", Dipo.logs.toString().contains("no iAP2 server"));
        // 2.5.2: false at first (still authenticating), asked again until true
        FakeIap2 busy = new FakeIap2();
        busy.falseFirst = 2;
        Dipo.logs.clear();
        G70Extra.cpChannelUp(busy);
        check("2.5.2: false twice, then true: asked 3 times, then the other two once",
                busy.calls.toString().equals("[now playing, now playing, now playing, call state, telephony]")
                        && Dipo.logs.toString().contains("try 3): Iap2SvcNowPlayingStart true"));
        FakeIap2 never = new FakeIap2();
        never.falseFirst = 100;
        G70Extra.cpChannelUp(never);
        check("2.5.2: false every time: gives up after 8 tries, says so",
                lines(never.calls.toString().replace(", ", "\n"), "now playing") == 8 && Dipo.logs.toString().contains("try 8): Iap2SvcNowPlayingStart false"));
        check("a call the server lacks: its name and the failure", G70Extra.call(new Object(), "Iap2SvcNowPlayingStart").startsWith("Iap2SvcNowPlayingStart failed"));

        // the startup agreement (2.6.2): the consent screen dismissed with the car's factory call,
        // in P, while the screen is up and not confirmed, at most 3 times 3 s apart
        final int[] hidden = { 0 };
        final Boolean[] inP = { Boolean.TRUE };
        G70Extra.hideHook = new Runnable() {
            public void run() {
                hidden[0]++;
            }
        };
        G70Extra.gearP = new java.util.concurrent.Callable<Boolean>() {
            public Boolean call() {
                return inP[0];
            }
        };
        android.os.SystemProperties.values.put("sys.boot.show_screen", "true");
        android.os.SystemProperties.values.put("sys.boot.confirm_screen", "false");
        G70Extra.agreeStep(c, false, 0);
        check("agreement off: the screen left alone", hidden[0] == 0);
        boolean done = G70Extra.agreeStep(c, true, 0);
        check("agreement on, screen up, in P: dismissed once", hidden[0] == 1 && !done
                && Dipo.logs.toString().contains("startup consent screen dismissed for the owner"));
        G70Extra.agreeStep(c, true, 2);
        check("not again within 3 s", hidden[0] == 1);
        G70Extra.agreeStep(c, true, 3);
        check("still up 3 s on: once more", hidden[0] == 2);
        android.os.SystemProperties.values.put("sys.boot.confirm_screen", "true");
        done = G70Extra.agreeStep(c, true, 6);
        check("confirmed: nothing more, done", hidden[0] == 2 && done);
        G70Extra.hides = 0;
        G70Extra.lastHide = -100;
        android.os.SystemProperties.values.put("sys.boot.confirm_screen", "false");
        inP[0] = Boolean.FALSE;
        G70Extra.agreeStep(c, true, 10);
        check("not in P: not dismissed", hidden[0] == 2);
        inP[0] = null;
        G70Extra.agreeStep(c, true, 13);
        check("the gear unknown: not dismissed", hidden[0] == 2);
        inP[0] = Boolean.TRUE;
        android.os.SystemProperties.values.put("sys.boot.show_screen", "false");
        done = G70Extra.agreeStep(c, true, 16);
        check("no screen up: nothing to do", hidden[0] == 2 && done);

        // with the research log: the states as they change
        Research.init(c, true);
        G70Extra.lastState = null;
        G70Extra.hides = 0;
        G70Extra.lastHide = -100;
        android.os.SystemProperties.values.put("sys.boot.show_screen", "true");
        check("research on: keeps watching", !G70Extra.agreeStep(c, true, 0));
        android.os.SystemProperties.values.put("sys.boot.confirm_screen", "true");
        G70Extra.agreeStep(c, true, 1);
        String log = file(new File(dir, "g70-research.log"));
        check("research: the screen's properties before and after, and the dismissal",
                lines(log, "screen up true, confirmed false") == 1 && lines(log, "screen up true, confirmed true") == 1
                        && lines(log, "agree: consent screen dismissed for the owner (1, 0 s): done") == 1);

        G70Car.start(c);
        check("car: both listeners registered", NModeManager.listener != null && BCMManager.listener != null);
        check("car: the read requests made at the start", NModeManager.requests == 1 && BCMManager.requests == 1);
        for (int k = 0; k < 8; k++) NModeManager.listener.onOilCoolantTemperature(new byte[] { 1, (byte) 0x5a });
        NModeManager.listener.onOilCoolantTemperature(new byte[] { 1, (byte) 0x5b });
        BCMManager.listener.onDriveModeChanged(3);
        BCMManager.listener.onDriveModeChanged(3);
        G70Car.GAP_MS = 0;
        NModeManager.listener.onOilCoolantTemperature(new byte[] { 1, (byte) 0x5c });
        NModeManager.listener.onPowerTorqueTurboBoostReceived(new byte[] { 0, 10, 20 });
        log = file(new File(dir, "g70-research.log"));
        check("car: a kind's first 5 lines, then only changes", lines(log, "car oil/coolant temperature: [2] 01 5a") == 5);
        check("car: a change within 2 s of the last line waits", lines(log, "01 5b") == 0);
        check("car: a change after the gap is written", lines(log, "car oil/coolant temperature: [2] 01 5c") == 1);
        check("car: drive mode and boost lines", lines(log, "car body control drive mode: 3") == 2 && lines(log, "car power/torque/boost: [3] 00 0a 14") == 1);
        check("car: the individual drive mode asked once", lines(log, "car individual drive mode (asked): DriveMode[sport]") == 1);

        // 2.5.3: mute pauses CarPlay music
        com.daudio.app.dipo.telechips.CarPlayService cp = new com.daudio.app.dipo.telechips.CarPlayService();
        Bridge.sCp = cp;
        Bridge.sCpWireless = true;
        com.hkmc.media.MicomAudioManager.muted = false;
        com.hkmc.media.MicomAudioManager.stream = 14;
        G70Mute.start(c, G70ExtraTest.class.getClassLoader(), true);
        com.hkmc.media.MicomAudioStateChangeListener ml = com.hkmc.media.MicomAudioManager.listener;
        check("mute: the car's audio listener registered", ml != null);
        com.hkmc.media.MicomAudioManager.muted = true;
        ml.onMute(0);
        check("mute on with CarPlay music: Pause pressed and released", cp.keys.toString().equals("[Pause down, Pause up]"));
        ml.onMute(0);
        check("a repeated mute event: nothing more", cp.keys.size() == 2);
        com.hkmc.media.MicomAudioManager.muted = false;
        ml.onMute(1);
        check("mute off: Play pressed and released", cp.keys.toString().equals("[Pause down, Pause up, Play down, Play up]"));
        cp.keys.clear();
        com.hkmc.media.MicomAudioManager.stream = 3;   // the radio
        com.hkmc.media.MicomAudioManager.muted = true;
        ml.onMute(0);
        com.hkmc.media.MicomAudioManager.muted = false;
        ml.onMute(1);
        check("mute on the radio: nothing sent to the iPhone", cp.keys.isEmpty());
        com.hkmc.media.MicomAudioManager.stream = 14;
        com.hkmc.media.MicomAudioManager.muted = true;
        ml.onMute(0);
        com.hkmc.media.MicomAudioManager.stream = 3;   // switched to the radio while muted
        com.hkmc.media.MicomAudioManager.muted = false;
        ml.onMute(1);
        log = file(new File(dir, "g70-research.log"));
        check("muted, switched to the radio, unmuted: paused but not resumed", cp.keys.toString().equals("[Pause down, Pause up]")
                && lines(log, "isn't the car's audio any more") == 1);
        cp.keys.clear();
        com.hkmc.media.MicomAudioManager.stream = 14;
        com.hkmc.media.MicomAudioManager.muted = true;
        G70Mute.streamAfterPause = Integer.MIN_VALUE;
        ml.onMute(0);   // the pause: the stream read right after it is 14 in this fake
        com.hkmc.media.MicomAudioManager.stream = 0;   // say the car shows no stream while paused
        G70Mute.streamAfterPause = 0;
        com.hkmc.media.MicomAudioManager.muted = false;
        ml.onMute(1);
        check("paused, the stream reads 0 as right after the pause: resumed", cp.keys.toString().equals("[Pause down, Pause up, Play down, Play up]"));
        cp.keys.clear();
        G70Mute.on = false;
        com.hkmc.media.MicomAudioManager.stream = 14;
        com.hkmc.media.MicomAudioManager.muted = true;
        ml.onMute(0);
        com.hkmc.media.MicomAudioManager.muted = false;
        ml.onMute(1);
        log = file(new File(dir, "g70-research.log"));
        check("setting off: nothing sent, the events still in the research log", cp.keys.isEmpty() && lines(log, "mute: onMute 0, user mute true, audio stream 14") >= 1);
        G70Mute.on = true;
        Bridge.sCp = null;
        com.hkmc.media.MicomAudioManager.muted = true;
        ml.onMute(0);
        check("no CarPlay session: nothing sent", cp.keys.isEmpty() && !G70Mute.pausedByUs);

        // 2.6.5: the sport display is gone (the owner: no use if the cluster can't show it); its names for the research log are G70Car's
        check("car: mode names and temperatures for the log", G70Car.modeName(3).equals("3 (sport+)") && G70Car.modeName(0x0f).equals("15 (changing)")
                && G70Car.describe(new byte[] { (byte) 0xa4, (byte) 0xbd }).equals(" = oil 75 C, coolant 94 C"));

        // 2.6.0: driving data (research)
        G70Drive.hcan = G70ExtraTest.class.getClassLoader();
        G70Drive.accel = new G70Drive.Accel() {
            public float[] take() {
                return new float[] { 0.12f, -2.5f, 9.6f };
            }
        };
        com.hkmc.telematics.hscan.tm_interface.ITM_HSCANService.speed = 0;
        G70Drive.tick();
        G70Drive.tick();
        log = file(new File(dir, "g70-research.log"));
        check("drive: parked, nothing new: one line", lines(log, "drive: speed 0.00, long accel 0.00, pedal 0.00, brake 0.00, steer 0.00, gear 1, unit accel x 0.12 y -2.50 z 9.60") == 1);
        com.hkmc.telematics.hscan.tm_interface.ITM_HSCANService.speed = 42;
        com.hkmc.telematics.hscan.tm_interface.ITM_HSCANService.pedal = 31.5f;
        G70Drive.tick();
        G70Drive.tick();
        log = file(new File(dir, "g70-research.log"));
        check("drive: moving: a line every second", lines(log, "drive: speed 42.00, long accel 0.00, pedal 31.50") == 2);
        check("car: the N additional info read request made once", com.hkmc.micom.NModeManager.addInfo == 1);
        check("car: drive mode by name, one line", lines(log, "car individual drive mode (asked): DriveMode[sport]") == 1);

        // 2.6.2: CarPlay always dark; 2.6.7: only where DiPO picks day (its rule), once per change, no timer
        com.daudio.app.dipo.telechips.CarPlayService night = new com.daudio.app.dipo.telechips.CarPlayService();
        Bridge.sCp = night;
        Bridge.sCpWireless = true;
        G70Dark.wasUp = false;
        com.hkmc.setup.VideoSetupManager.dim = 0;      // auto
        com.hkmc.setup.VideoSetupManager.bright = true;  // bright outside: DiPO picks day
        G70Dark.start(c, true);
        check("dark: the car's brightness listener registered", com.hkmc.setup.VideoSetupManager.listener != null);
        check("dark: a new session in daylight (auto): night mode once", night.nights == 1);
        G70Dark.look();
        G70Dark.look();
        check("2.6.7: no timer: not sent again while the session goes on", night.nights == 1);
        com.hkmc.setup.VideoSetupManager.bright = false;   // a tunnel: DiPO picks night itself
        com.hkmc.setup.VideoSetupManager.listener.onIlluminateStatusChanged(false);
        check("2.6.7: dark outside: DiPO sends night itself, nothing from us", night.nights == 1);
        com.hkmc.setup.VideoSetupManager.bright = true;    // out of the tunnel: DiPO picks day
        com.hkmc.setup.VideoSetupManager.listener.onIlluminateStatusChanged(true);
        check("dark: bright again: night mode once after DiPO's day", night.nights == 2);
        com.hkmc.setup.VideoSetupManager.dim = 2;          // the car's night brightness mode
        com.hkmc.setup.VideoSetupManager.listener.onDimChanged(2);
        com.hkmc.setup.VideoSetupManager.listener.onIlluminateStatusChanged(true);
        check("2.6.7: the car's night brightness mode: nothing sent, whatever the light", night.nights == 2 && G70Dark.skips >= 2);
        G70Dark.wasUp = false;
        G70Dark.look();
        check("2.6.7: a new session in the night brightness mode: nothing sent", night.nights == 2);
        com.hkmc.setup.VideoSetupManager.dim = 1;          // the day brightness mode
        com.hkmc.setup.VideoSetupManager.listener.onDimChanged(1);
        check("dark: the day brightness mode: night mode once", night.nights == 3);
        check("2.6.7: DiPO's rule", G70Dark.dipoNight(2, true) && !G70Dark.dipoNight(1, false) && G70Dark.dipoNight(0, false)
                && !G70Dark.dipoNight(3, true) && G70Dark.dipoNight(7, true) == null);
        Bridge.sCp = null;
        com.hkmc.setup.VideoSetupManager.listener.onDimChanged(1);
        check("dark: no CarPlay: nothing sent", night.nights == 3);
        G70Dark.on = false;

        System.out.println(fail == 0 ? "G70 extras (2.5-2.6): all passed" : "G70 extras (2.5-2.6): FAILED");
        System.exit(fail);
    }
}
