package com.daudio.app.dipo.telechips;

import java.util.ArrayList;
import java.util.List;

/** host fake of DiPO's CarPlay service: records the media key presses */
public class CarPlayService {
    public final List<String> keys = new ArrayList<String>();

    public int nights;

    /** DiPO's night mode call: counted when it asks for night */
    public String setNightMode(boolean night) {
        if (night) nights++;
        return "OK";
    }

    public String onConsumer(CarPlayConsumer c, boolean pressed) {
        keys.add(c + (pressed ? " down" : " up"));
        return "OK";
    }
}
