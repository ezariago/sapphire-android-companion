package dev.sapphire.companion;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.*;

/** One pending link replaces the prior one. Opening always needs a phone tap. */
final class PhoneHandoff {
    private final Context context;
    private final ArrayDeque<String> received = new ArrayDeque<>();
    PhoneHandoff(Context context) { this.context = context; }
    void reset() {
        received.clear();
        context.getSystemService(NotificationManager.class).cancel("sapphire-link-handoff", 2003);
    }
    JSONObject receive(JSONObject payload) {
        String id = payload.optString("requestId");
        String url = HandoffPolicy.url(payload.optString("url"));
        String state = "error", message = "Use a valid HTTP or HTTPS link under 4 KB, without spaces or login credentials.";
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (id.isEmpty() || id.length() > 128) id = "";
        else if (!context.getSharedPreferences("settings", 0).getBoolean("handoff", false)) {
            state = "disabled"; message = "Enable link handoff in Sapphire Companion on your phone.";
        } else if (!manager.areNotificationsEnabled()) {
            state = "needs_permission"; message = "Allow Sapphire notifications on your phone to receive links.";
        } else if (url != null) {
            try {
                NotificationChannel channel = new NotificationChannel("link-handoff", "Links from Mac", NotificationManager.IMPORTANCE_LOW);
                manager.createNotificationChannel(channel);
                if (manager.getNotificationChannel("link-handoff").getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    state = "needs_permission"; message = "Allow the Links from Mac notification channel on your phone.";
                } else {
                    if (!received.contains(id)) {
                        Intent open = new Intent(context, ReceivedLinkActivity.class).setData(Uri.parse(url));
                        PendingIntent tap = PendingIntent.getActivity(context, 2003, open,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        manager.notify("sapphire-link-handoff", 2003, new Notification.Builder(context, "link-handoff")
                                .setSmallIcon(android.R.drawable.ic_menu_share).setContentTitle("Link from your Mac")
                                .setContentText(Uri.parse(url).getHost()).setVisibility(Notification.VISIBILITY_PRIVATE)
                                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true).build());
                        received.addLast(id);
                        if (received.size() > 32) received.removeFirst();
                    }
                    state = "ready"; message = "Link sent. Tap the Sapphire notification on your phone to open it.";
                }
            } catch (RuntimeException unavailable) { message = "Your phone could not display this link. Check notification permission."; }
        }
        JSONObject result = new JSONObject();
        try { result.put("requestId", id).put("state", state).put("message", message); }
        catch (JSONException ignored) { }
        return result;
    }
}
