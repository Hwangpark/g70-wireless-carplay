package com.hkmc.micom;

/** host fake: keeps the listener, counts the read requests */
public class NModeManager {
    public static NModeListener listener;
    public static int requests;

    public NModeManager(android.content.Context c) {}

    public void registerNModeListener(NModeListener l) {
        listener = l;
    }

    public void requestOilCoolantTemperature() {
        requests++;
    }

    public static int addInfo;

    public void requestNAddInfo() {
        addInfo++;
    }

    public DriveMode getIndividualDriveMode() {
        return new DriveMode();
    }
}
