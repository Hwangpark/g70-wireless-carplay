package android.os;

/** host stand-in: post runs at once, postDelayed never (the per-minute summary is called by hand) */
public class Handler {
    public Handler(Looper l) {}

    public boolean post(Runnable r) {
        r.run();
        return true;
    }

    public boolean postDelayed(Runnable r, long ms) {
        return true;
    }
}
