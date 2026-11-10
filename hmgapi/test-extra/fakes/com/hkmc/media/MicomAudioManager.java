package com.hkmc.media;

/** host fake of the car's audio manager: the owner's mute and the current stream */
public class MicomAudioManager {
    public static boolean muted;
    public static int stream = 14;
    public static MicomAudioStateChangeListener listener;

    public MicomAudioManager(android.content.Context c) {}

    public void registerListener(MicomAudioStateChangeListener l) {
        listener = l;
    }

    public boolean getUserMuteState() {
        return muted;
    }

    public int getCurrentStreamType() {
        return stream;
    }
}
