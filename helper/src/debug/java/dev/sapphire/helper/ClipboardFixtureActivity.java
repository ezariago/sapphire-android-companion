package dev.sapphire.helper;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;
import dev.sapphire.ipc.*;

/** Fixed synthetic clipboard probe. Original clip stays opaque/in-memory, never logged or persisted. */
public final class ClipboardFixtureActivity extends Activity {
    private static State active;
    private static final String TEXT = "Sapphire clipboard fixture https://example.com/sapphire-clipboard-test";
    private String run, action;
    private boolean handled;
    private static final class State {
        Activity activity; String run; ClipData original; boolean preference; ServiceConnection connection; IClipboardHelper helper; IClipboardCallback callback;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        run = getIntent().getStringExtra("run"); action = getIntent().getStringExtra("fixture");
        try { UUID.fromString(run); } catch (Exception invalid) { finish(); return; }
        if ("restore".equals(action) && active == null) { report("wrong-run"); finishAffinity(); return; }
        KeyguardManager lock = getSystemService(KeyguardManager.class);
        if ("begin".equals(action) && (lock == null || lock.isDeviceLocked() || lock.isKeyguardLocked())) {
            report("locked"); finish(); return;
        }
    }
    private void report(String state) {
        try {
            android.util.AtomicFile file = new android.util.AtomicFile(new File(getFilesDir(), "clipboard-fixture.json"));
            FileOutputStream out = file.startWrite();
            try { out.write(new JSONObject().put("run", run).put("state", state).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); file.finishWrite(out); }
            catch (Exception unavailable) { file.failWrite(out); }
        } catch (Exception ignored) {}
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused || handled || isFinishing()) return;
        handled = true;
        if ("restore".equals(action)) { restore(); return; }
        if (!"begin".equals(action) || active != null) { report("blocked"); finish(); return; }
        State state = new State(); state.run = run; state.activity = this;
        state.original = getSystemService(ClipboardManager.class).getPrimaryClip();
        state.preference = getSharedPreferences("helper", 0).getBoolean("clipboard", false);
        active = state;
        getSharedPreferences("helper", 0).edit().putBoolean("clipboard", true).commit();
        state.callback = new IClipboardCallback.Stub() {
            public void onStatus(String value) { runOnUiThread(() -> {
                if (active != state) return;
                if (!"ready".equals(value)) { report("unavailable"); return; }
                report("ready"); moveTaskToBack(true);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (active != state) return;
                    if (hasWindowFocus()) { report("still-focused"); return; }
                    getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("sapphire-fixture-" + run, TEXT));
                    report("copied-in-background");
                }, 1000);
            }); }
            public void onClipboard(String text, long timestamp) { runOnUiThread(() -> {
                if (active == state) report(TEXT.equals(text) && !hasWindowFocus() ? "verified-background" : "mismatch");
            }); }
        };
        state.connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder remote) {
                state.helper = IClipboardHelper.Stub.asInterface(remote);
                try { state.helper.start(state.callback); } catch (RemoteException | SecurityException unavailable) { report("unavailable"); }
            }
            public void onServiceDisconnected(ComponentName name) { report("disconnected"); }
        };
        report("starting");
        try { if (!getApplicationContext().bindService(new Intent(this, ClipboardService.class), state.connection, BIND_AUTO_CREATE)) report("unavailable"); }
        catch (RuntimeException unavailable) { report("unavailable"); }
    }
    private void restore() {
        State state = active;
        if (state == null || !run.equals(state.run)) { report("wrong-run"); finish(); return; }
        try { if (state.helper != null) state.helper.stop(state.callback); } catch (RemoteException ignored) {}
        try { getApplicationContext().unbindService(state.connection); } catch (IllegalArgumentException ignored) {}
        getSharedPreferences("helper", 0).edit().putBoolean("clipboard", state.preference).commit();
        ClipboardManager board = getSystemService(ClipboardManager.class);
        ClipData current = board.getPrimaryClip();
        if (current != null && ("sapphire-fixture-" + run).contentEquals(current.getDescription().getLabel()) && current.getItemCount() == 1 && TEXT.equals(String.valueOf(current.getItemAt(0).getText()))) {
            if (state.original == null) board.clearPrimaryClip(); else board.setPrimaryClip(state.original);
        }
        state.original = null; active = null; state.activity.finish(); report("restored"); finish();
    }
}
