package com.hkmc.regulation;

/** host fake of the car's regulation manager: the occupation consent and popup states */
public class RegulationActivityManager {
    public static int consent = 0, popup = 1, sets;

    public static RegulationActivityManager getInstance(android.content.Context c) {
        return new RegulationActivityManager();
    }

    public int getOccupationConsentState() {
        return consent;
    }

    public int getOccupationPopupState() {
        return popup;
    }

    /** what the car navigation does when its consent button is pressed: given, the popup goes */
    public void setOccupationConsentState(int v) {
        sets++;
        consent = v;
        popup = 0;
    }
}
