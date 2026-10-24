package dev.sapphire.companion;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;

/** Synthetic fixture only, absent from release APKs. No messages leave this test app. */
public final class NotificationFixtureActivity extends Activity {
    static final String TAG = "sapphire-notification-fixture";
    static final int ID = 2001;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String command = getIntent().getStringExtra("fixture");
        if (command != null) {
            if ("test-enable".equals(command) || "test-disable".equals(command)) {
                getSharedPreferences("settings", 0).edit().putBoolean("fixtureNotifications", "test-enable".equals(command)).apply();
            } else if ("remove".equals(command)) getSystemService(NotificationManager.class).cancel(TAG, ID);
            else post(this, "update".equals(command) ? "Updated synthetic notification." : "Hello from the Android notification fixture.");
            finish();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(28, 36, 28, 28);
        TextView text = new TextView(this);
        text.setText("Synthetic Sapphire notification test. Replies and actions stay in this fixture app; no real messages are sent.");
        layout.addView(text);
        Button post = new Button(this); post.setText("Post test notification");
        post.setOnClickListener(v -> post(this, "Hello from the Android notification fixture.")); layout.addView(post);
        Button remove = new Button(this); remove.setText("Remove test notification");
        remove.setOnClickListener(v -> getSystemService(NotificationManager.class).cancel(TAG, ID)); layout.addView(remove);
        setContentView(layout);
        post(this, "Hello from the Android notification fixture.");
    }
    static void post(Context context, String text) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("notification-fixture-silent-v2", "Silent notification test", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
        PendingIntent reply = PendingIntent.getBroadcast(context, 21, new Intent(context, ActionReceiver.class).setAction("fixture.reply"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        PendingIntent action = PendingIntent.getBroadcast(context, 22, new Intent(context, ActionReceiver.class).setAction("fixture.action"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(TAG, ID, new Notification.Builder(context, "notification-fixture-silent-v2")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Sapphire mirroring test")
                .setContentText(text).setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "Reply", reply)
                        .addRemoteInput(new RemoteInput.Builder("fixture-reply").setLabel("Reply").build()).build())
                .addAction(new Notification.Action.Builder(null, "Test action", action).build()).build());
    }
    public static final class ActionReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            Bundle reply = RemoteInput.getResultsFromIntent(intent);
            boolean replied = "fixture.reply".equals(intent.getAction()) && reply != null
                    && "Sapphire reply fixture".contentEquals(reply.getCharSequence("fixture-reply", ""));
            boolean acted = "fixture.action".equals(intent.getAction());
            context.getSharedPreferences("notification-fixture", 0).edit()
                    .putBoolean("replyVerified", replied).putBoolean("actionVerified", acted).apply();
            post(context, replied ? "Synthetic reply verified." : acted ? "Synthetic action verified." : "Synthetic reply received.");
        }
    }
}
