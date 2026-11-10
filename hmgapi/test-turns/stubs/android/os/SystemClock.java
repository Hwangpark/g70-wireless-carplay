package android.os;

/** host stand-in: the test sets the clock */
public final class SystemClock {
    public static long now = 1000000L;

    public static long elapsedRealtime() {
        return now;
    }
}
