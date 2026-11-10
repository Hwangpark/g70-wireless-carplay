package hmgapi.hook;

/** host stand-in: the iAP2 server's init information class is the test's own */
public final class Points {
    public static Class<?> initInfo;

    public static Class<?> cls(ClassLoader cl, String id) {
        return initInfo;
    }
}
