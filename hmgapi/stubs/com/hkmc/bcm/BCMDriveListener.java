package com.hkmc.bcm;

/** compile-time stand-in for the car's framework class (automotive.jar); not in hooks.jar */
public class BCMDriveListener {
    public BCMDriveListener() {}
    public void onDriveModeChanged(int mode) {}
    public void onEcsStatusChanged(int a, int b, int c, int d, int e, int f, int g, int h, int i) {}
    public void onEngineStatusChanged(boolean on) {}
    public void onRctaAlarmChanged(int a, int b) {}
}
