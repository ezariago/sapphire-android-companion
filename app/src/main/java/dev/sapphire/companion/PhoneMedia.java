package dev.sapphire.companion;

import android.app.NotificationManager;
import android.content.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.SystemClock;
import org.json.*;
import java.util.*;

/** Main-looper only. No audio capture, artwork transfer, or persisted track history. */
final class PhoneMedia {
    private final Context context;
    private MediaController selected;
    private String sessionID = "";

    PhoneMedia(Context context) { this.context = context; }

    static boolean enabled(Context context) {
        return context.getSharedPreferences("settings", 0).getBoolean("media", false)
                && context.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(
                        new ComponentName(context, NotificationMirror.class));
    }

    void reset() { selected = null; sessionID = ""; }

    private void select() {
        if (!enabled(context)) { reset(); return; }
        try {
            List<MediaController> controllers = context.getSystemService(MediaSessionManager.class)
                    .getActiveSessions(new ComponentName(context, NotificationMirror.class));
            MediaController next = null;
            // Android supplies sessions in priority order. Prefer actual playback,
            // then retain a paused selection while it remains available.
            for (MediaController candidate : controllers) {
                PlaybackState state = candidate.getPlaybackState();
                if (state != null && MediaPolicy.playing(state.getState())) { next = candidate; break; }
            }
            if (next == null && selected != null) for (MediaController candidate : controllers)
                if (candidate.getSessionToken().equals(selected.getSessionToken())) { next = candidate; break; }
            if (next == null && !controllers.isEmpty()) next = controllers.get(0);
            if (next == null) { reset(); return; }
            if (selected == null || !next.getSessionToken().equals(selected.getSessionToken()))
                sessionID = UUID.randomUUID().toString();
            selected = next;
        } catch (RuntimeException unavailable) { reset(); }
    }

    JSONObject read() {
        select();
        JSONObject result = new JSONObject();
        try {
            result.put("sessionId", sessionID).put("appId", "").put("appName", "")
                    .put("title", "").put("isPlaying", false).put("positionMs", 0).put("durationMs", 0)
                    .put("canNext", false).put("canPrev", false).put("canSeek", false).put("canToggle", false);
            if (selected == null) return result;
            PlaybackState state = selected.getPlaybackState();
            MediaMetadata metadata = selected.getMetadata();
            String appID = selected.getPackageName(), appName = appID;
            try { appName = context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(appID, 0)).toString(); }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
            long duration = metadata == null ? 0 : MediaPolicy.time(metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
            long actions = state == null ? 0 : state.getActions();
            boolean playing = state != null && MediaPolicy.playing(state.getState());
            result.put("appId", NotificationPolicy.bounded(appID, 256)).put("appName", NotificationPolicy.bounded(appName, 256))
                    .put("title", text(metadata, MediaMetadata.METADATA_KEY_TITLE, 512))
                    .put("artist", text(metadata, MediaMetadata.METADATA_KEY_ARTIST, 512))
                    .put("album", text(metadata, MediaMetadata.METADATA_KEY_ALBUM, 512))
                    .put("durationMs", duration).put("isPlaying", playing)
                    .put("positionMs", state == null ? 0 : MediaPolicy.position(state.getPosition(), state.getState(),
                            state.getPlaybackSpeed(), state.getLastPositionUpdateTime(), SystemClock.elapsedRealtime(), duration))
                    .put("canNext", MediaPolicy.allows("next", playing, actions))
                    .put("canPrev", MediaPolicy.allows("previous", playing, actions))
                    .put("canSeek", MediaPolicy.allows("seek", playing, actions))
                    .put("canToggle", MediaPolicy.allows("toggle", playing, actions));
        } catch (RuntimeException | JSONException malformed) {
            reset();
            return new JSONObject();
        }
        return result;
    }

    private String text(MediaMetadata metadata, String key, int bytes) {
        return metadata == null ? "" : NotificationPolicy.bounded(metadata.getText(key), bytes);
    }

    boolean command(JSONObject payload) {
        // Re-select before dispatch: never redirect a stale command to a new player.
        select();
        if (selected == null || !sessionID.equals(payload.optString("sessionId"))) return false;
        try {
            PlaybackState state = selected.getPlaybackState();
            if (state == null) return false;
            String command = payload.optString("action");
            boolean playing = MediaPolicy.playing(state.getState());
            if (!MediaPolicy.allows(command, playing, state.getActions())) return false;
            MediaController.TransportControls controls = selected.getTransportControls();
            switch (command) {
                case "play": controls.play(); break;
                case "pause": controls.pause(); break;
                case "toggle": if (playing) controls.pause(); else controls.play(); break;
                case "next": controls.skipToNext(); break;
                case "previous": controls.skipToPrevious(); break;
                case "stop": controls.stop(); break;
                case "seek":
                    Object raw = payload.opt("seekMs");
                    if (!(raw instanceof Number)) return false;
                    double value = ((Number) raw).doubleValue();
                    if (!Double.isFinite(value) || value < 0 || value > MediaPolicy.MAX_TIME_MS || value != Math.floor(value)) return false;
                    MediaMetadata metadata = selected.getMetadata();
                    long duration = metadata == null ? 0 : MediaPolicy.time(metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
                    controls.seekTo(duration > 0 ? Math.min((long) value, duration) : (long) value);
                    break;
                default: return false;
            }
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }
}
