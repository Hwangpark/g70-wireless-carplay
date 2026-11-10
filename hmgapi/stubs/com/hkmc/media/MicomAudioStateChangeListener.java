package com.hkmc.media;

/** compile-time stand-in for the car's framework class (automotive.jar); not in hooks.jar */
public abstract class MicomAudioStateChangeListener {
    public MicomAudioStateChangeListener() {}
    public abstract void onStatusChanged(int what, MicomAudioStatus status);
    public void onMute(int mode) {}
}
