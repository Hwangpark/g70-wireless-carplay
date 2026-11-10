package android.os;

/** host stand-in: the iAP2 switches as a test sets them */
public class SystemProperties {
    public static final java.util.Map<String, String> values = new java.util.HashMap<String, String>();

    public static String get(String k) {
        String v = values.get(k);
        return v == null ? "" : v;
    }
}
