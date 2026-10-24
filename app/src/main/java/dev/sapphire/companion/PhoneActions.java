package dev.sapphire.companion;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.UUID;

/** One in-memory draft, bound to this authenticated connection, expires in 2 min. */
final class PhoneActions {
    static final String TAG = "sapphire-phone-action";
    static final int NOTIFICATION_ID = 2004;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PhoneActionPolicy.Store store = new PhoneActionPolicy.Store();
    private Runnable expiration;
    private PendingIntent pendingIntent;
    PhoneActions(Context context) { this.context = context; }
    void reset() {
        if (expiration != null) main.removeCallbacks(expiration);
        expiration = null;
        if (pendingIntent != null) pendingIntent.cancel();
        pendingIntent = null;
        store.reset();
        context.getSystemService(NotificationManager.class).cancel(TAG, NOTIFICATION_ID);
    }
    PhoneActionPolicy.Draft peek(String id, String token) {
        if (!context.getSharedPreferences("settings", 0).getBoolean("phoneActions", false)) return null;
        return store.peek(id, token, SystemClock.elapsedRealtime());
    }
    boolean consume(String id, String token) {
        if (peek(id, token) == null || !store.consume(id, token, SystemClock.elapsedRealtime())) return false;
        context.getSystemService(NotificationManager.class).cancel(TAG, NOTIFICATION_ID);
        if (pendingIntent != null) pendingIntent.cancel();
        pendingIntent = null;
        return true;
    }
    JSONObject receive(JSONObject payload) {
        String id = payload.opt("requestId") instanceof String ? payload.optString("requestId") : "";
        PhoneActionPolicy.Draft draft = PhoneActionPolicy.draft(
                payload.opt("kind") instanceof String ? payload.optString("kind") : null,
                payload.opt("number") instanceof String ? payload.optString("number") : null,
                payload.opt("body") instanceof String ? payload.optString("body") : null);
        String state = "invalid";
        if (PhoneActionPolicy.requestID(id) && draft != null) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (!context.getSharedPreferences("settings", 0).getBoolean("phoneActions", false)) state = "disabled";
            else if (!manager.areNotificationsEnabled()) state = "needs_permission";
            else {
                try {
                    manager.createNotificationChannel(new NotificationChannel("phone-actions", "Phone drafts from Mac", NotificationManager.IMPORTANCE_LOW));
                    if (manager.getNotificationChannel("phone-actions").getImportance() == NotificationManager.IMPORTANCE_NONE) state = "needs_permission";
                    else {
                        String token = UUID.randomUUID().toString();
                        PhoneActionPolicy.Offer offered = store.offer(id, token, draft, SystemClock.elapsedRealtime());
                        if (offered == PhoneActionPolicy.Offer.DUPLICATE) state = "ready";
                        else if (offered == PhoneActionPolicy.Offer.EXPIRED) state = "expired";
                        else if (offered == PhoneActionPolicy.Offer.READY) {
                            Intent review = new Intent(context, ReceivedPhoneActionActivity.class)
                                    .setData(Uri.fromParts("sapphire-phone-action", token, null))
                                    .putExtra("requestId", id).putExtra("token", token);
                            if (pendingIntent != null) pendingIntent.cancel();
                            PendingIntent tap = PendingIntent.getActivity(context, NOTIFICATION_ID, review, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
                            pendingIntent = tap;
                            manager.notify(TAG, NOTIFICATION_ID, new Notification.Builder(context, "phone-actions")
                                    .setSmallIcon(android.R.drawable.ic_menu_send).setContentTitle("Phone draft from your Mac")
                                    .setContentText("Tap to review on your unlocked phone. Expires in two minutes.")
                                    .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(tap)
                                    .setTimeoutAfter(PhoneActionPolicy.LIFETIME_MS).setOnlyAlertOnce(true).setAutoCancel(false).build());
                            if (expiration != null) main.removeCallbacks(expiration);
                            expiration = () -> { store.clearPending(); manager.cancel(TAG, NOTIFICATION_ID); if (pendingIntent != null) pendingIntent.cancel(); pendingIntent = null; expiration = null; };
                            main.postDelayed(expiration, PhoneActionPolicy.LIFETIME_MS);
                            state = "ready";
                        }
                    }
                } catch (RuntimeException failure) { reset(); state = "error"; }
            }
        }
        JSONObject result = new JSONObject();
        try { result.put("requestId", PhoneActionPolicy.requestID(id) ? id : "").put("state", state); }
        catch (JSONException ignored) { }
        return result;
    }
}
