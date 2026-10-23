package dev.sapphire.companion;
import android.media.session.PlaybackState;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaPolicyTest {
    @Test public void timeBoundsAndExtrapolation() {
        assertEquals(0, MediaPolicy.time(-1));
        assertEquals(MediaPolicy.MAX_TIME_MS, MediaPolicy.time(Long.MAX_VALUE));
        assertEquals(3000, MediaPolicy.position(1000, PlaybackState.STATE_PLAYING, 1f, 100, 2100, 5000));
        assertEquals(5000, MediaPolicy.position(4000, PlaybackState.STATE_PLAYING, 2f, 100, 2100, 5000));
        assertEquals(1000, MediaPolicy.position(1000, PlaybackState.STATE_PAUSED, 1f, 100, 2100, 5000));
        assertEquals(1000, MediaPolicy.position(1000, PlaybackState.STATE_PLAYING, Float.NaN, 100, 2100, 5000));
        assertEquals(1000, MediaPolicy.position(1000, PlaybackState.STATE_PLAYING, Float.POSITIVE_INFINITY, 100, 2100, 5000));
        assertEquals(1000, MediaPolicy.position(1000, PlaybackState.STATE_PLAYING, 1f, 3000, 2100, 5000));
        assertEquals(0, MediaPolicy.position(1000, PlaybackState.STATE_PLAYING, -2f, 100, 2100, 5000));
    }
    @Test public void onlyAdvertisedActionsAreAllowed() {
        assertTrue(MediaPolicy.allows("toggle", true, PlaybackState.ACTION_PAUSE));
        assertFalse(MediaPolicy.allows("toggle", false, PlaybackState.ACTION_PAUSE));
        assertTrue(MediaPolicy.allows("toggle", false, PlaybackState.ACTION_PLAY_PAUSE));
        assertTrue(MediaPolicy.allows("toggle", true, PlaybackState.ACTION_PLAY_PAUSE));
        assertTrue(MediaPolicy.allows("seek", true, PlaybackState.ACTION_SEEK_TO));
        assertFalse(MediaPolicy.allows("next", true, PlaybackState.ACTION_PLAY));
        assertFalse(MediaPolicy.allows("arbitrary", true, Long.MAX_VALUE));
        assertFalse(MediaPolicy.allows("play", false, 0));
    }
}
