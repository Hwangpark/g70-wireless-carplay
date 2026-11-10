package com.hkmc.media;

import android.content.Context;

/** compile-time stand-in for the car's framework class (automotive.jar); not in hooks.jar */
public class MicomAudioManager {
    public MicomAudioManager(Context c) {}
    public void registerListener(MicomAudioStateChangeListener l) {}
    public void unregisterListener(MicomAudioStateChangeListener l) {}
    public boolean getUserMuteState() { return false; }
    public int getCurrentStreamType() { return 0; }
}
