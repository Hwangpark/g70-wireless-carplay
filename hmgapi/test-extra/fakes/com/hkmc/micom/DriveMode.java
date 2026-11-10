package com.hkmc.micom;

/** host fake: the car's class has public bytes like this one and a toString over several lines */
public class DriveMode {
    public byte mCurrentDrviceMode = 1;   // the car spells it this way (its toString says mCurrentDriveMode)

    @Override
    public String toString() {
        return "DriveMode[sport]";
    }
}
