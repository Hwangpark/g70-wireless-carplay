package com.hkmc.bcm;

/** host fake: keeps the listener, counts the drive mode requests */
public class BCMManager {
    public static BCMDriveListener listener;
    public static int requests;

    public BCMManager(android.content.Context c) {}

    public void addDriveListener(BCMDriveListener l) {
        listener = l;
    }

    public void getDriveMode() {
        requests++;
    }
}
