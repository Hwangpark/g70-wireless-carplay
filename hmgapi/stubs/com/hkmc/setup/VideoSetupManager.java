package com.hkmc.setup;

import android.content.Context;

/** compile-time stand-in for the car's framework class (automotive.jar); not in hooks.jar */
public class VideoSetupManager {
    public VideoSetupManager(Context c) {}
    public void addListener(VideoSetupListener l) {}
    public void removeListener(VideoSetupListener l) {}
    public int getDim() { return 0; }
    public boolean getIlluminateStatus() { return false; }
}
