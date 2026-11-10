package com.hkmc.media;

public abstract class MicomAudioStateChangeListener {
    public abstract void onStatusChanged(int what, MicomAudioStatus status);
    public void onMute(int mode) {}
}
