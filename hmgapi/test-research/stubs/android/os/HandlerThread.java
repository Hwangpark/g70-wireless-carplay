package android.os;

/** host stand-in: never runs; its Handler runs posts at once */
public class HandlerThread extends Thread {
    public HandlerThread(String name) {
        super(name);
    }

    @Override
    public synchronized void start() {}

    public Looper getLooper() {
        return new Looper();
    }
}
