package com.hkmc.micom;

import android.content.Context;

/** compile-time stand-in for the car's framework class (automotive.jar); not in hooks.jar */
public class NModeManager {
    public NModeManager(Context c) {}
    public void registerNModeListener(NModeListener l) {}
    public void unregisterNModeListener(NModeListener l) {}
    public void requestOilCoolantTemperature() {}
    public void requestNAddInfo() {}
    public DriveMode getIndividualDriveMode() { return null; }
}
