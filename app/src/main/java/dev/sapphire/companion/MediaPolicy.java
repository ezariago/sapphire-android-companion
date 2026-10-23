package dev.sapphire.companion;

import android.media.session.PlaybackState;

/** Pure bounds/action policy shared by the live controller and JVM tests. */
final class MediaPolicy {
    // Keep remote time values within a year and safely representable on both peers.
    static final long MAX_TIME_MS = 366L * 24 * 60 * 60 * 1000;
    static long time(long value) { return Math.max(0, Math.min(MAX_TIME_MS, value)); }
    static boolean playing(int state) {
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING;
    }
    static long position(long position, int state, float speed, long updated, long now, long duration) {
        double value = time(position);
        if (state == PlaybackState.STATE_PLAYING && Float.isFinite(speed) && updated > 0 && now >= updated)
            value += Math.min(MAX_TIME_MS, now - updated) * (double) speed;
        long bounded = time((long) value);
        return duration > 0 ? Math.min(bounded, time(duration)) : bounded;
    }
    static boolean allows(String command, boolean playing, long actions) {
        long required;
        switch (command) {
            case "play": required = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE; break;
            case "pause": required = PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE; break;
            case "toggle": return allows(playing ? "pause" : "play", playing, actions);
            case "next": required = PlaybackState.ACTION_SKIP_TO_NEXT; break;
            case "previous": required = PlaybackState.ACTION_SKIP_TO_PREVIOUS; break;
            case "seek": required = PlaybackState.ACTION_SEEK_TO; break;
            case "stop": required = PlaybackState.ACTION_STOP; break;
            default: return false;
        }
        return (actions & required) != 0;
    }
}
