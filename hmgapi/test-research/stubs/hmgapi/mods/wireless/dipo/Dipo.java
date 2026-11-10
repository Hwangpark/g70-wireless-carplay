package hmgapi.mods.wireless.dipo;

import java.util.ArrayList;
import java.util.List;

/** host stand-in for the research log test */
final class Dipo {
    static final int RG_UPDATE = 120, RG_MANEUVER = 121, RG_LANES = 122;
    static final List<String> logs = new ArrayList<String>();

    static void log(String m) {
        logs.add(m);
    }

    static void error(String m, Throwable t) {
        logs.add("ERROR " + m + ": " + t);
    }
}
