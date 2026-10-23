package dev.sapphire.companion;
import android.app.*;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

/** Fixed public test URL only. Does not open a browser or fetch any network content. */
public final class HandoffFixtureActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String command = getIntent().getStringExtra("fixture");
        if ("enable".equals(command) || "disable".equals(command)) {
            getSharedPreferences("settings", 0).edit().putBoolean("handoff", "enable".equals(command)).apply();
        } else if ("send".equals(command)) {
            if (LinkService.instance != null) LinkService.instance.sendHandoff("https://example.com/sapphire-handoff-test");
        } else if ("review".equals(command)) {
            for (StatusBarNotification item : getSystemService(NotificationManager.class).getActiveNotifications())
                if ("sapphire-link-handoff".equals(item.getTag()) && item.getNotification().contentIntent != null)
                    try { item.getNotification().contentIntent.send(); } catch (PendingIntent.CanceledException ignored) { }
        } else if ("remove".equals(command)) {
            getSystemService(NotificationManager.class).cancel("sapphire-link-handoff", 2003);
        }
        finish();
    }
}
