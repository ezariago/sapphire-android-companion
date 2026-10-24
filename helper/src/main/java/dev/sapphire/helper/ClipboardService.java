package dev.sapphire.helper;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.sapphire.ipc.*;
import org.lsposed.hiddenapibypass.HiddenApiBypass;
import rikka.shizuku.*;

/** Optional, signature-protected read-only clipboard broker. No commands, URIs or clipboard writes. */
public final class ClipboardService extends Service {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean reading = new AtomicBoolean();
    private IClipboardCallback client;
    private IBinder.DeathRecipient clientDeath;
    private Object clipboard, listener;
    private long epoch;
    private boolean dirty;
    private final Runnable changed = () -> { dirty = true; read(); };
    private final Shizuku.OnBinderDeadListener dead = () -> main.post(() -> { status("needs_setup"); clear(); });
    private final IClipboardHelper.Stub binder = new IClipboardHelper.Stub() {
        public void start(IClipboardCallback callback) {
            enforceCallingOrSelfPermission("dev.sapphire.companion.permission.HOTSPOT", "Matching companion required");
            if (callback != null) main.post(() -> attach(callback));
        }
        public void stop(IClipboardCallback callback) {
            enforceCallingOrSelfPermission("dev.sapphire.companion.permission.HOTSPOT", "Matching companion required");
            main.post(() -> { if (client != null && callback != null && client.asBinder().equals(callback.asBinder())) clear(); });
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences = (prefs, key) -> {
        if ("clipboard".equals(key) && !prefs.getBoolean("clipboard", false)) main.post(this::clear);
    };
    @Override public void onCreate() {
        super.onCreate(); Shizuku.addBinderDeadListener(dead);
        getSharedPreferences("helper", 0).registerOnSharedPreferenceChangeListener(preferences);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    private void attach(IClipboardCallback callback) {
        if (client != null && !client.asBinder().equals(callback.asBinder())) {
            try { callback.onStatus("busy"); } catch (RemoteException ignored) {} return;
        }
        clear(); client = callback;
        try {
            if (!getSharedPreferences("helper", 0).getBoolean("clipboard", false)) { status("disabled"); clear(); return; }
            if (!Shizuku.pingBinder() || Shizuku.getUid() != 2000 || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) throw new SecurityException();
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/IClipboard", "Landroid/content/IOnPrimaryClipChangedListener");
            Class<?> api = Class.forName("android.content.IClipboard");
            Class<?> callbacks = Class.forName("android.content.IOnPrimaryClipChangedListener");
            IBinder remote = SystemServiceHelper.getSystemService("clipboard");
            if (remote == null) throw new IllegalStateException();
            clipboard = Class.forName("android.content.IClipboard$Stub").getDeclaredMethod("asInterface", IBinder.class)
                .invoke(null, new ShizukuBinderWrapper(remote));
            final long listenerEpoch = epoch;
            Binder events = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel result, int flags) throws RemoteException {
                    if (code == INTERFACE_TRANSACTION) { if (result != null) result.writeString("android.content.IOnPrimaryClipChangedListener"); return true; }
                    if (code == 1) { data.enforceInterface("android.content.IOnPrimaryClipChangedListener");
                        main.post(() -> { if (epoch == listenerEpoch && client != null) {
                            main.removeCallbacks(changed); main.postDelayed(changed, 200);
                        } }); return true; }
                    return super.onTransact(code, data, result, flags);
                }
            };
            listener = Proxy.newProxyInstance(callbacks.getClassLoader(), new Class[]{callbacks}, (proxy, method, args) -> {
                if (method.getName().equals("asBinder")) return events;
                if (method.getName().equals("toString")) return "SapphireClipboardEvents";
                return null;
            });
            invoke(clipboard, "addPrimaryClipChangedListener", listener);
            final IBinder owner = callback.asBinder();
            clientDeath = () -> main.post(() -> { if (client != null && client.asBinder().equals(owner)) clear(); });
            callback.asBinder().linkToDeath(clientDeath, 0);
            status("ready"); // Do not read or forward the existing clipboard on activation.
        } catch (Exception unavailable) { status("needs_setup"); clear(); }
    }
    @android.annotation.SuppressLint("BlockedPrivateApi")
    private static Object invoke(Object target, String name, Object eventListener) throws Exception {
        for (Method method : Class.forName("android.content.IClipboard").getMethods()) {
            if (!method.getName().equals(name)) continue;
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[types.length];
            int start = 0;
            if (!name.equals("getPrimaryClip")) { args[0] = eventListener; start = 1; }
            int strings = 0, integers = 0;
            // Explicitly supported Android signatures: package, optional attribution, user, optional device.
            for (int i = start; i < types.length; i++) {
                if (types[i] == String.class && integers == 0) args[i] = strings++ == 0 ? "com.android.shell" : null;
                else if (types[i] == int.class) args[i] = integers++ == 0 ? android.os.Process.myUserHandle().hashCode() : 0;
                else throw new NoSuchMethodException("Unsupported clipboard interface");
            }
            if (strings < 1 || strings > 2 || integers < 1 || integers > 2) throw new NoSuchMethodException("Unsupported clipboard interface");
            return method.invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }
    private void read() {
        if (client == null || !getSharedPreferences("helper", 0).getBoolean("clipboard", false)) { clear(); return; }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard == null || keyguard.isDeviceLocked() || keyguard.isKeyguardLocked()) return;
        if (!reading.compareAndSet(false, true)) return;
        dirty = false;
        final long stamp = epoch; final Object source = clipboard;
        new Thread(() -> {
            ClipData clip = null; boolean failed = false;
            try { clip = (ClipData)invoke(source, "getPrimaryClip", null); } catch (Exception unavailable) { failed = true; }
            final ClipData result = clip; final boolean error = failed;
            main.post(() -> {
                reading.set(false);
                if (stamp != epoch || client == null) { if (client != null && dirty) main.post(changed); return; }
                if (dirty) { main.post(changed); return; }
                if (error) { status("needs_setup"); clear(); return; }
                KeyguardManager guard = getSystemService(KeyguardManager.class);
                if (guard != null && !guard.isDeviceLocked() && !guard.isKeyguardLocked()
                    && getSharedPreferences("helper", 0).getBoolean("clipboard", false) && result != null && result.getItemCount() == 1) {
                    PersistableBundle extras = result.getDescription().getExtras();
                    CharSequence text = result.getItemAt(0).getText();
                    boolean sensitive = extras != null && extras.getBoolean("android.content.extra.IS_SENSITIVE", false);
                    boolean remote = extras != null && extras.getBoolean("dev.sapphire.fromMac", false);
                    if (ClipboardTextPolicy.accepts(text == null ? null : text.toString(), result.getItemCount(), sensitive, remote)) {
                        try { client.onClipboard(text.toString(), result.getDescription().getTimestamp()); }
                        catch (RemoteException unavailable) { clear(); }
                    }
                }
                if (dirty) main.post(changed);
            });
        }, "SapphireClipboardRead").start();
    }
    private void status(String state) { try { if (client != null) client.onStatus(state); } catch (RemoteException ignored) {} }
    private void clear() {
        epoch++; main.removeCallbacks(changed); dirty = false;
        if (clipboard != null && listener != null) try { invoke(clipboard, "removePrimaryClipChangedListener", listener); } catch (Exception ignored) {}
        clipboard = null; listener = null;
        if (client != null && clientDeath != null) try { client.asBinder().unlinkToDeath(clientDeath, 0); } catch (java.util.NoSuchElementException ignored) {}
        client = null; clientDeath = null;
    }
    @Override public boolean onUnbind(Intent intent) { clear(); return false; }
    @Override public void onDestroy() { clear(); Shizuku.removeBinderDeadListener(dead);
        getSharedPreferences("helper", 0).unregisterOnSharedPreferenceChangeListener(preferences); super.onDestroy(); }
}
