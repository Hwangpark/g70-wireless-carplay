package hmgapi.mods.wireless.dipo;

import java.util.ArrayList;
import java.util.List;

/** host stand-in for the G70 turn-by-turn tests: CpTurns logs and posts through it; G70Lanes reaches turns through it */
final class Dipo {
    static final List<String> logs = new ArrayList<String>();
    static final List<String> research = new ArrayList<String>();
    static android.os.Handler turnsHandler = new android.os.Handler();
    static CpTurns turns;

    static void log(String m) {
        logs.add(m);
    }

    static void error(String m, Throwable t) {
        logs.add("ERROR " + m + ": " + t);
    }

    static void research(String m) {
        research.add(m);
    }

    /** the research log's switch (the setting g70_debug) */
    static boolean researchFlag = true;

    static boolean researchOn() {
        return researchFlag;
    }

    /** the turn-by-turn thread: at once, here */
    static void post(Runnable r) {
        r.run();
    }
}
