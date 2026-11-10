package android.content;

/** host stand-in: only the cache folder */
public class Context {
    public static java.io.File cache;

    public java.io.File getCacheDir() {
        return cache;
    }
}
