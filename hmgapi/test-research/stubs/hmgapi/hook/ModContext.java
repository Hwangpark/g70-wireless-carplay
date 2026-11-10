package hmgapi.hook;

import java.util.ArrayList;
import java.util.List;

/** host stand-in: the log only */
public final class ModContext {
    public final List<String> logs = new ArrayList<String>();

    public void log(String m) {
        logs.add(m);
    }

    public void error(String m, Throwable t) {
        logs.add("ERROR " + m + ": " + t);
    }
}
