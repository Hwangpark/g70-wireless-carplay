package com.hkmc.bcm;

import android.content.Context;

/** compile-time stand-in for the car's framework class (automotive.jar); not in hooks.jar */
public class BCMManager {
    public BCMManager(Context c) {}
    public void addDriveListener(BCMDriveListener l) {}
    public void removeDriveListener(BCMDriveListener l) {}
    public void getDriveMode() {}
}
