package dev.sapphire.companion;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.*;
import org.json.*;
import java.util.*;

/** Android holds PendingIntents; the Mac receives only short-lived opaque action IDs. */
public final class NotificationMirror extends NotificationListenerService {
    static NotificationMirror instance;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> dirty = new HashSet<>();
    private boolean subscribed, includeGeneral, includeSystem;
    private SharedPreferences.OnSharedPreferenceChangeListener preferences;

    private static final class Entry {
        final StatusBarNotification notification;
        final JSONObject payload;
        final Map<String, Notification.Action> actions;
        Entry(StatusBarNotification notification, JSONObject payload, Map<String, Notification.Action> actions) {
            this.notification = notification; this.payload = payload; this.actions = actions;
        }
    }

    static boolean enabled(Context context) {
        boolean debugFixture = (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && context.getSharedPreferences("settings", 0).getBoolean("fixtureNotifications", false);
        return (context.getSharedPreferences("settings", 0).getBoolean("notifications", false) || debugFixture)
                && context.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(
                    new ComponentName(context, NotificationMirror.class));
    }

    @Override public void onListenerConnected() {
        close();
        instance = this;
        preferences = (prefs, key) -> {
            if ("notifications".equals(key) || "fixtureNotifications".equals(key)) main.post(() -> {
                LinkService link = LinkService.instance;
                configure(link != null && link.notificationSubscribed, includeGeneral, includeSystem);
            });
        };
        getSharedPreferences("settings", 0).registerOnSharedPreferenceChangeListener(preferences);
        LinkService link = LinkService.instance;
        if (link != null) configure(link.notificationSubscribed, link.notificationGeneral, link.notificationSystem);
    }

    @Override public void onListenerDisconnected() {
        close();
        LinkService link = LinkService.instance;
        if (link != null) link.notificationEvent("notif.reset", new JSONObject());
    }

    private void close() {
        if (preferences != null) getSharedPreferences("settings", 0).unregisterOnSharedPreferenceChangeListener(preferences);
        preferences = null;
        subscribed = false;
        entries.clear(); dirty.clear();
        main.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
    }

    @Override public void onDestroy() {
        close();
        LinkService link = LinkService.instance;
        if (link != null) link.notificationEvent("notif.reset", new JSONObject());
        super.onDestroy();
    }

    void configure(boolean active, boolean general, boolean system) {
        subscribed = active;
        includeGeneral = general; includeSystem = system;
        entries.clear(); dirty.clear();
        main.removeCallbacksAndMessages(null);
        LinkService link = LinkService.instance;
        if (link != null) link.notificationEvent("notif.reset", new JSONObject());
        if (!canShare()) return;
        try {
            StatusBarNotification[] current = getActiveNotifications();
            if (current == null) return;
            Arrays.sort(current, Comparator.comparingLong(StatusBarNotification::getPostTime).reversed());
            for (StatusBarNotification item : current) {
                if (entries.size() >= 30) break;
                publish(item, true);
            }
        } catch (SecurityException ignored) { /* Access can be revoked while taking the snapshot. */ }
    }

    private boolean canShare() {
        LinkService link = LinkService.instance;
        return subscribed && enabled(this) && link != null && link.secure && link.notificationSubscribed;
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (!canShare()) return;
        // Coalesce chat progress updates and repeated posts before serializing anything.
        boolean scheduled = !dirty.isEmpty();
        dirty.add(notification.getKey());
        if (!scheduled) main.postDelayed(flush, 300);
    }

    private final Runnable flush = () -> {
        if (!canShare()) { dirty.clear(); return; }
        try {
            StatusBarNotification[] current = getActiveNotifications(dirty.toArray(new String[0]));
            dirty.clear();
            if (current != null) for (StatusBarNotification item : current) publish(item, false);
        } catch (SecurityException ignored) { dirty.clear(); }
    };

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        dirty.remove(notification.getKey());
        Entry removed = entries.remove(notification.getKey());
        if (removed != null && canShare()) emitRemoval(removed);
    }

    private void emitRemoval(Entry entry) {
        try {
            LinkService.instance.notificationEvent("notif.removed", new JSONObject().put("key", entry.notification.getKey())
                    .put("revision", entry.payload.getString("revision")));
        } catch (JSONException ignored) { }
    }

    private void publish(StatusBarNotification item, boolean snapshot) {
        try {
            Notification n = item.getNotification();
            boolean system = item.getPackageName().equals("android") || item.getPackageName().equals("com.android.systemui");
            Ranking ranking = new Ranking();
            boolean ranked = getCurrentRanking().getRanking(item.getKey(), ranking);
            boolean general = ranked && ranking.getImportance() < NotificationManager.IMPORTANCE_DEFAULT;
            boolean fixture = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    && item.getPackageName().equals(getPackageName()) && "sapphire-notification-fixture".equals(item.getTag());
            // Synthetic device tests may opt in to this one fixed debug fixture
            // while the user's normal notification sharing preference stays off.
            if (!getSharedPreferences("settings", 0).getBoolean("notifications", false) && !fixture) return;
            boolean eligible = NotificationPolicy.eligible(item.getPackageName(), fixture ? "" : getPackageName(),
                    item.getUser().equals(android.os.Process.myUserHandle()), n.visibility == Notification.VISIBILITY_SECRET,
                    item.isOngoing(), (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0,
                    general, system, includeGeneral, includeSystem);
            if (!eligible || item.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024) {
                Entry removed = entries.remove(item.getKey());
                if (removed != null) emitRemoval(removed);
                return;
            }
            String appName = item.getPackageName();
            try { appName = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(appName, 0)).toString(); }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
            String title = NotificationPolicy.bounded(n.extras.getCharSequence(Notification.EXTRA_TITLE), 512);
            CharSequence body = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (body == null || body.length() == 0) body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            String text = NotificationPolicy.bounded(body, 4096);
            if (title.isEmpty() && text.isEmpty()) return;
            Entry previous = entries.get(item.getKey());
            String revision = UUID.randomUUID().toString();
            Map<String, Notification.Action> actions = new LinkedHashMap<>();
            JSONArray encoded = new JSONArray();
            if (n.actions != null) for (Notification.Action action : n.actions) {
                if (actions.size() == 4) break;
                if (action.actionIntent == null || action.title == null
                        || (Build.VERSION.SDK_INT >= 31 && action.isAuthenticationRequired())) continue;
                RemoteInput[] inputs = action.getRemoteInputs();
                boolean reply = false;
                if (inputs != null) for (RemoteInput input : inputs) reply |= input.getAllowFreeFormInput();
                if (inputs != null && inputs.length > 0 && !reply) continue;
                String id = UUID.randomUUID().toString();
                actions.put(id, action);
                encoded.put(new JSONObject().put("id", id).put("title", NotificationPolicy.bounded(action.title, 128)).put("allowsReply", reply));
            }
            JSONObject payload = new JSONObject().put("key", item.getKey()).put("revision", revision)
                    .put("appId", item.getPackageName()).put("appName", NotificationPolicy.bounded(appName, 256))
                    .put("title", title).put("text", text).put("postTimeMs", item.getPostTime())
                    .put("clearable", item.isClearable()).put("isGeneral", general).put("isSystem", system)
                    .put("snapshot", snapshot).put("actions", encoded);
            if (!NotificationPolicy.fitPayload(payload)) {
                Entry removed = entries.remove(item.getKey());
                if (removed != null) emitRemoval(removed);
                return;
            }
            text = payload.optString("text");
            // Do not alert twice for exact duplicate Android callbacks. Retain the old action IDs.
            if (previous != null && previous.notification.getPostTime() == item.getPostTime()
                    && previous.payload.optString("title").equals(title) && previous.payload.optString("text").equals(text)
                    && Arrays.equals(previous.notification.getNotification().actions, n.actions)) return;
            if (entries.size() >= 30 && !entries.containsKey(item.getKey())) {
                String oldest = Collections.min(entries.entrySet(), Comparator.comparingLong(e -> e.getValue().notification.getPostTime())).getKey();
                emitRemoval(entries.remove(oldest));
            }
            entries.remove(item.getKey());
            entries.put(item.getKey(), new Entry(item, payload, actions));
            LinkService.instance.notificationEvent("notif.postedV2", payload);
        } catch (Exception ignored) { /* Malformed app extras must not kill the notification listener. */ }
    }

    void command(String type, JSONObject payload) {
        if (!canShare()) return;
        String key = payload.optString("key"), revision = payload.optString("revision");
        Entry entry = entries.get(key);
        boolean ok = false;
        String message = "This notification changed or is no longer available.";
        try {
            if (entry != null && !dirty.contains(key) && revision.equals(entry.payload.optString("revision"))) {
                StatusBarNotification[] active = getActiveNotifications(new String[]{key});
                if (active != null && active.length == 1 && active[0].getPostTime() == entry.notification.getPostTime()) {
                    if ("notif.dismiss".equals(type)) {
                        if (entry.notification.isClearable()) {
                            cancelNotification(key); ok = true; message = "Dismissed on phone.";
                        } else message = "This notification cannot be dismissed.";
                    } else if (getSystemService(KeyguardManager.class).isDeviceLocked()) {
                        message = "Unlock your phone to use this action.";
                    } else {
                        Notification.Action action = entry.actions.get(payload.optString("actionId"));
                        if (action != null) {
                            Intent intent = new Intent();
                            RemoteInput[] inputs = action.getRemoteInputs();
                            if (inputs != null && inputs.length > 0) {
                                String reply = payload.optString("replyText");
                                if (reply.trim().isEmpty() || reply.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4000)
                                    throw new IllegalArgumentException();
                                Bundle results = new Bundle();
                                for (RemoteInput input : inputs) if (input.getAllowFreeFormInput()) results.putCharSequence(input.getResultKey(), reply);
                                RemoteInput.addResultsToIntent(inputs, intent, results);
                                RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT);
                            }
                            action.actionIntent.send(this, 0, intent);
                            ok = true; message = "Action sent to phone.";
                        }
                    }
                }
            }
        } catch (Exception ignored) { message = "The phone could not complete this action. Open the app on your phone."; }
        try { LinkService.instance.notificationEvent("notif.actionResult", new JSONObject().put("key", key)
                .put("revision", revision).put("requestId", payload.optString("requestId")).put("ok", ok).put("message", message)); }
        catch (JSONException ignored) { }
    }
}
