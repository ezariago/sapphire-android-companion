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
        context.getSystemService(NotificationManager.class).cancel("sapphire-clipboard-link", 2004);
    }
    void copiedText(String text) {
        String link = HandoffPolicy.copiedLink(text);
        if (link == null) return;
        JSONObject payload = new JSONObject();
        try { payload.put("requestId", UUID.randomUUID().toString()).put("url", link); }
        catch (JSONException ignored) { return; }
        receive(payload, true);
    }
    JSONObject receive(JSONObject payload) { return receive(payload, false); }
    private JSONObject receive(JSONObject payload, boolean copied) {
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
                String channelID = copied ? "clipboard-links" : "link-handoff";
                NotificationChannel channel = new NotificationChannel(channelID, copied ? "Copied links from Mac" : "Links from Mac", copied ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_LOW);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                if (copied) { channel.setSound(null, null); channel.enableVibration(false); }
                manager.createNotificationChannel(channel);
                if (manager.getNotificationChannel(channelID).getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    state = "needs_permission"; message = "Allow the Links from Mac notification channel on your phone.";
                } else {
                    if (!received.contains(id)) {
                        Intent open = new Intent(context, ReceivedLinkActivity.class).setData(Uri.parse(url));
                        int notificationID = copied ? 2004 : 2003;
                        String tag = copied ? "sapphire-clipboard-link" : "sapphire-link-handoff";
                        if (copied) open.putExtra("clipboardExpiry", android.os.SystemClock.elapsedRealtime() + 5000);
                        PendingIntent tap = PendingIntent.getActivity(context, notificationID, open,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        Notification.Builder notification = new Notification.Builder(context, channelID)
                                .setSmallIcon(android.R.drawable.ic_menu_share).setContentTitle("Link from your Mac")
                                .setContentText(Uri.parse(url).getHost()).setVisibility(Notification.VISIBILITY_PRIVATE)
                                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(!copied);
                        if (copied) notification.setTimeoutAfter(5000).setContentTitle("Open copied link from Mac?")
                            .addAction(new Notification.Action.Builder(null, "Review link", tap).build());
                        manager.notify(tag, notificationID, notification.build());
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
