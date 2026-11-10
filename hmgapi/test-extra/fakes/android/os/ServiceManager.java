package android.os;

/** host fake: the one service G70Drive asks for */
public class ServiceManager {
    public static Object getService(String name) {
        return "binder:" + name;
    }
}
