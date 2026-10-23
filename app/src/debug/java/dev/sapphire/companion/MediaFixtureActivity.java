package dev.sapphire.companion;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;

/** Silent synthetic session. Never starts real audio, network requests, or another player. */
public final class MediaFixtureActivity extends Activity {
    private static MediaSession session;
    private static boolean playing;
    private static int track = 1;
    private static long position;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String command = getIntent().getStringExtra("fixture");
        if ("remove".equals(command)) {
            if (session != null) { session.setActive(false); session.release(); session = null; }
        } else if ("enable".equals(command) || "disable".equals(command)) {
            getSharedPreferences("settings", 0).edit().putBoolean("media", "enable".equals(command)).apply();
        } else {
            startForegroundService(new Intent(this, LinkService.class));
            if (session != null) session.release();
            playing = false; position = 0; track = 1;
            session = new MediaSession(this, "Sapphire silent media fixture");
            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { playing = true; update(); }
                @Override public void onPause() { playing = false; update(); }
                @Override public void onSkipToNext() { track = 2; position = 0; update(); }
                @Override public void onSkipToPrevious() { track = 1; position = 0; update(); }
                @Override public void onSeekTo(long value) { position = value; update(); }
                @Override public void onStop() { playing = false; position = 0; update(); }
            }, new Handler(Looper.getMainLooper()));
            update();
            session.setActive(true);
        }
        finish();
    }
    private static void update() {
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Sapphire media fixture " + track)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Silent synthetic artist")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Sapphire test album")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 180000).build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        position, 0f, SystemClock.elapsedRealtime()).build());
    }
}
