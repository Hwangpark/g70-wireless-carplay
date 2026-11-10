package com.hkmc.micom;

public class NModeListener {
    public void onDriveCustomMode2Received(byte[] b) {}
    public void onGForceDataReceived(byte[] b) {}
    public void onIndividualDriveModeChanged(DriveMode m) {}
    public void onLapTimeDataReceived(byte[] b) {}
    public void onNAddInfoReceived(byte[] b) {}
    public void onNModeZeroSpeedChanged(byte[] b) {}
    public void onNewMyDriveModeReceived(byte[] b) {}
    public void onOilCoolantTemperature(byte[] b) {}
    public void onPerformanceOption(byte[] b) {}
    public void onPowerTorqueTurboBoostReceived(byte[] b) {}
    public void onSpeedTempUnitTempReceived(byte[] b) {}
}
