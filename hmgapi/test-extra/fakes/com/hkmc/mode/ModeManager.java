package com.hkmc.mode;

/** host fake of the car's mode manager: the startup agreement */
public class ModeManager {
    public static boolean clicked;
    public static int agrees;

    public ModeManager(android.content.Context c) {}

    public boolean isUserClickedAgree() {
        return clicked;
    }

    public void agreeUserLicense() {
        agrees++;
        clicked = true;
    }
}
