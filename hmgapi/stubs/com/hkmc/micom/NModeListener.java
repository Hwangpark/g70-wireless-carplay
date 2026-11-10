package com.hkmc.micom;

/** compile-time stand-in for the car's framework class (automotive.jar, on the boot class path); not in hooks.jar */
public class NModeListener {
    public NModeListener() {}
    public void onDriveCustomMode2Received(byte[] b) {}
    public void onGForceDataReceived(byte[] b) {}
    public void onIndividualDriveModeChanged(DriveMode m) {}
    public void onLapTimeDataReceived(byte[] b) {}
    public void onLapTimeSettingStatus(byte[] b) {}
    public void onNAddInfoReceived(byte[] b) {}
    public void onNModeZeroSpeedChanged(byte[] b) {}
    public void onNewLapTimerReceived(byte[] b) {}
    public void onNewMyDriveModeReceived(byte[] b) {}
    public void onOilCoolantTemperature(byte[] b) {}
    public void onPerformanceOption(byte[] b) {}
    public void onPowerTorqueTurboBoostReceived(byte[] b) {}
    public void onSpeedTempUnitTempReceived(byte[] b) {}
}
