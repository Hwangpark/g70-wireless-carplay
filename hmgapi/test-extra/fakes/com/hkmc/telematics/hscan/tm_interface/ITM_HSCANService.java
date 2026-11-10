package com.hkmc.telematics.hscan.tm_interface;

/** host fake of the telematics CAN service: the driving values G70Drive reads */
public class ITM_HSCANService {
    public static float speed, longAccel, pedal, brake, steer;
    public static int gear = 1;

    public float getCarSpeed(byte unit) { return speed; }
    public float getLongAccelVal() { return longAccel; }
    public float getAccelPdlVal() { return pedal; }
    public float getCylPrsrVal() { return brake; }
    public float getSasAngleVal() { return steer; }
    public int getGearPosition() { return gear; }

    public static class Stub {
        public static ITM_HSCANService asInterface(Object binder) {
            return "binder:tm_hscan_service".equals(binder) ? new ITM_HSCANService() : null;
        }
    }
}
