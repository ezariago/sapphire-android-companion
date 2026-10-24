package dev.sapphire.companion;
import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

/** Fixed notification-only probe; never reads/writes clipboard or opens a link. */
public final class ClipboardLinkFixtureReceiver extends BroadcastReceiver {
    private static boolean active;
    private static final String TAG = "sapphire-clipboard-link";
    @Override public void onReceive(Context context, Intent intent) {
        String run = intent.getStringExtra("run");
        try { UUID.fromString(run); } catch (Exception invalid) { return; }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (active || find(manager) != null) { report(context, run, "blocked"); return; }
        if (!manager.areNotificationsEnabled()) { report(context, run, "needs_permission"); return; }
        active = true;
        PendingResult pending = goAsync();
        SharedPreferences settings = context.getSharedPreferences("settings", 0);
        boolean original = settings.getBoolean("handoff", false);
        settings.edit().putBoolean("handoff", true).commit();
        PhoneHandoff handoff = new PhoneHandoff(context);
        handoff.copiedText("Read this https://example.com/sapphire-copied-link-test please");
        Notification notification = find(manager);
        boolean correct = notification != null && notification.getTimeoutAfter() == 5000
            && notification.visibility == Notification.VISIBILITY_PRIVATE && notification.contentIntent != null
            && "example.com".contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT, ""));
        report(context, run, correct ? "posted" : "failed");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean expired = find(manager) == null;
            manager.cancel(TAG, 2004);
            boolean restored = settings.edit().putBoolean("handoff", original).commit();
            report(context, run, correct && expired && restored ? "verified-expiry" : "failed");
            active = false; pending.finish();
        }, 6000);
    }
    private static Notification find(NotificationManager manager) {
        for (StatusBarNotification entry : manager.getActiveNotifications()) if (TAG.equals(entry.getTag()) && entry.getId() == 2004) return entry.getNotification();
        return null;
    }
    private static void report(Context context, String run, String state) {
        try {
            android.util.AtomicFile file = new android.util.AtomicFile(new File(context.getFilesDir(), "clipboard-link-fixture.json"));
            FileOutputStream output = file.startWrite();
            try { output.write(new JSONObject().put("run", run).put("state", state).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); file.finishWrite(output); }
            catch (Exception unavailable) { file.failWrite(output); }
        } catch (Exception ignored) {}
    }
}
