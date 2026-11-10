package com.hkmc.setup;

/** compile-time stand-in for the car's framework class (automotive.jar); not in hooks.jar */
public class VideoSetupListener {
    public VideoSetupListener() {}
    public void on1wayManualUpdateReceived(int v) {}
    public void on2wayUserGainLuxReceived(int a, int b, int c) {}
    public void onCurrentBrightness(int v) {}
    public void onDimChanged(int mode) {}
    public void onIlluminateStatusChanged(boolean on) {}
}
