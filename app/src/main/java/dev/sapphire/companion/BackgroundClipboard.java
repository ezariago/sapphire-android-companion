package dev.sapphire.companion;
import android.content.*;
import android.os.*;
import dev.sapphire.ipc.*;

/** Binds only the fixed signature-protected helper while connected and explicitly enabled. */
final class BackgroundClipboard {
    private final LinkService owner;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ServiceConnection connection;
    private IClipboardHelper helper;
    private IClipboardCallback callback;
    private long generation;
    private long retryAfter;
    String state = "off";
    BackgroundClipboard(LinkService owner) { this.owner = owner; }
    private boolean enabled() { return owner.secure && owner.getSharedPreferences("settings", 0).getBoolean("sendClipboard", false)
        && owner.getSharedPreferences("settings", 0).getBoolean("backgroundClipboard", false); }
    void refresh() {
        if (!enabled()) { stop(); return; }
        long now = SystemClock.elapsedRealtime();
        if (connection != null) {
            if ("ready".equals(state) || now < retryAfter) return;
            stop(); // A stale binding/status must not prevent recovery after helper authorization returns.
        }
        if (now < retryAfter) return;
        retryAfter = now + 30000;
        final long stamp = ++generation;
        state = "connecting";
        callback = new IClipboardCallback.Stub() {
            public void onClipboard(String text, long timestamp) {
                main.post(() -> { if (stamp == generation && enabled()) owner.sendClipboardText(text, timestamp, true); });
            }
            public void onStatus(String value) {
                main.post(() -> {
                    if (stamp != generation) return;
                    state = "ready".equals(value) ? "ready" : "needs_setup";
                    retryAfter = "ready".equals(state) ? 0 : SystemClock.elapsedRealtime() + 30000;
                });
            }
        };
        connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder remote) {
                if (stamp != generation || !enabled()) return;
                helper = IClipboardHelper.Stub.asInterface(remote);
                try { helper.start(callback); } catch (RemoteException | RuntimeException unavailable) { state = "needs_setup"; retryAfter = SystemClock.elapsedRealtime() + 30000; }
            }
            public void onServiceDisconnected(ComponentName name) { if (stamp == generation) { stop(); state = "needs_setup"; retryAfter = SystemClock.elapsedRealtime() + 30000; } }
            public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
            public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
        };
        try {
            Intent intent = new Intent().setComponent(new ComponentName("dev.sapphire.companion.hotspothelper", "dev.sapphire.helper.ClipboardService"));
            if (!owner.bindService(intent, connection, Context.BIND_AUTO_CREATE)) { connection = null; state = "needs_setup"; retryAfter = SystemClock.elapsedRealtime() + 30000; }
        } catch (RuntimeException unavailable) { connection = null; state = "needs_setup"; retryAfter = SystemClock.elapsedRealtime() + 30000; }
    }
    void retry() { stop(); retryAfter = 0; refresh(); }
    void stop() {
        generation++;
        if (helper != null && callback != null) try { helper.stop(callback); } catch (RemoteException | RuntimeException ignored) {}
        helper = null; callback = null;
        if (connection != null) try { owner.unbindService(connection); } catch (IllegalArgumentException ignored) {}
        connection = null; state = "off"; retryAfter = 0;
    }
}
