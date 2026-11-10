package com.hkmc.setup;

/** host fake: keeps the listener so the test can play the car's brightness changes; the test sets the state */
public class VideoSetupManager {
    public static VideoSetupListener listener;
    /** the brightness mode (0, 3 auto, 1 day, 2 night) and the illumination status (true: bright outside) */
    public static int dim;
    public static boolean bright;

    public VideoSetupManager(android.content.Context c) {}

    public void addListener(VideoSetupListener l) {
        listener = l;
    }

    public int getDim() {
        return dim;
    }

    public boolean getIlluminateStatus() {
        return bright;
    }
}
