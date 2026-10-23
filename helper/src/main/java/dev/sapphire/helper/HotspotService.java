package dev.sapphire.helper;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.content.pm.PackageManager;

import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.sapphire.ipc.*;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import rikka.shizuku.*;

/**
 * A narrow, signature-protected broker. No shell commands or arbitrary IPC.
 */
public final class HotspotService extends Service {
    private static final int TETHER_ERROR_DUPLICATE_REQUEST = 18;
    private static final int WIFI_AP_STATE_ENABLED = 13;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean();
    private final IHotspotHelper.Stub binder = new IHotspotHelper.Stub() {
        public void setEnabled(boolean enabled, IHotspotCallback callback) {
            if (callback == null) return;
            if (checkCallingOrSelfPermission("dev.sapphire.companion.permission.HOTSPOT") != PackageManager.PERMISSION_GRANTED) {
                reply(callback, "denied", "The companion signature does not match.");
                return;
            }
            if (!busy.compareAndSet(false, true)) {
                reply(callback, "error", "A hotspot operation is already running.");
                return;
            }
            AtomicBoolean completed = new AtomicBoolean();
            Runnable timeout = () -> {
                if (completed.compareAndSet(false, true)) {
                    busy.set(false);
                    reply(callback, "error", "Android did not finish the hotspot request. Check hotspot settings on the phone.");
                }
            };
            main.postDelayed(timeout, 30000);
            new Thread(() -> {
                try {
                    if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                        throw new SecurityException("setup");
                    HiddenApiBypass.addHiddenApiExemptions("Landroid/net/");
                    // Connecting must also work when the user already enabled tethering.
                    if (enabled && isHotspotActive()) {
                        if (completed.compareAndSet(false, true)) {
                            main.removeCallbacks(timeout);
                            busy.set(false);
                            credentials(callback);
                        }
                        return;
                    }
                    Object connector = service("tethering", "android.net.ITetheringConnector$Stub");
                    Class<?> listenerType = Class.forName("android.net.IIntResultListener");
                    Binder listenerBinder = new Binder() {
                        @Override
                        protected boolean onTransact(int code, Parcel data, Parcel result, int flags) throws RemoteException {
                            if (code == INTERFACE_TRANSACTION) {
                                if (result != null)
                                    result.writeString("android.net.IIntResultListener");
                                return true;
                            }
                            if (code == 1) {
                                data.enforceInterface("android.net.IIntResultListener");
                                int status = data.readInt();
                                if (enabled && status == TETHER_ERROR_DUPLICATE_REQUEST) {
                                    // Another request may still be bringing up the AP. Do not
                                    // treat stored credentials alone as proof it is running.
                                    new Thread(() -> reuseHotspot(callback, completed, timeout), "SapphireHotspotReuse").start();
                                    return true;
                                }
                                if (completed.compareAndSet(false, true)) {
                                    main.removeCallbacks(timeout);
                                    busy.set(false);
                                    if (status == 0) {
                                        if (enabled) credentials(callback);
                                        else reply(callback, "stopped", "Hotspot is off.");
                                    } else
                                        reply(callback, status == 14 ? "denied" : "error", "Android could not change the hotspot (code " + status + "). Check Shizuku access and your carrier’s hotspot settings.");
                                }
                                return true;
                            }
                            return super.onTransact(code, data, result, flags);
                        }
                    };
                    Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class[]{listenerType}, (proxy, method, args) -> {
                        if (method.getName().equals("asBinder")) return listenerBinder;
                        if (method.getName().equals("toString")) return "SapphireHotspotResult";
                        return null;
                    });
                    if (enabled) {
                        Class<?> builderType = Class.forName("android.net.TetheringManager$TetheringRequest$Builder");
                        Object builder = builderType.getConstructor(int.class).newInstance(0);
                        // Keep carrier entitlement checks enabled; this is internet tethering.
                        builderType.getDeclaredMethod("setExemptFromEntitlementCheck", boolean.class).invoke(builder, false);
                        builderType.getDeclaredMethod("setShouldShowEntitlementUi", boolean.class).invoke(builder, true);
                        Object request = builderType.getMethod("build").invoke(builder);
                        Method getParcel = request.getClass().getDeclaredMethod("getParcel");
                        getParcel.setAccessible(true);
                        invoke(connector, "startTethering", getParcel.invoke(request), listener);
                    } else invoke(connector, "stopTethering", 0, listener);
                } catch (Exception e) {
                    if (completed.compareAndSet(false, true)) {
                        main.removeCallbacks(timeout);
                        busy.set(false);
                        reply(callback, "needs_setup", "Open Sapphire Hotspot Helper and authorize it in Shizuku. If your phone blocks hotspot control, use Android hotspot settings.");
                    }
                }
            }, "SapphireHotspot").start();
        }
    };

    private static boolean isHotspotActive() {
        try {
            Object wifi = service("wifi", "android.net.wifi.IWifiManager$Stub");
            return (Integer) Class.forName("android.net.wifi.IWifiManager")
                    .getDeclaredMethod("getWifiApEnabledState").invoke(wifi) == WIFI_AP_STATE_ENABLED;
        } catch (Exception e) {
            // If this OEM cannot expose state, let the normal start request decide.
            return false;
        }
    }

    private void reuseHotspot(IHotspotCallback callback, AtomicBoolean completed, Runnable timeout) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        boolean active;
        do {
            if (completed.get()) return;
            active = isHotspotActive();
            if (active) break;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (SystemClock.elapsedRealtime() < deadline);
        if (completed.compareAndSet(false, true)) {
            main.removeCallbacks(timeout);
            busy.set(false);
            if (active) credentials(callback);
            else reply(callback, "error", "Android already has a hotspot request pending, but its hotspot is not active yet. Wait for it to turn on in Android hotspot settings, then try again.");
        }
    }

    private static Object service(String name, String stub) throws Exception {
        IBinder remote = SystemServiceHelper.getSystemService(name);
        if (remote == null) throw new IllegalStateException("Service unavailable");
        return Class.forName(stub).getDeclaredMethod("asInterface", IBinder.class).invoke(null, new ShizukuBinderWrapper(remote));
    }

    private static void invoke(Object connector, String name, Object request, Object listener) throws Exception {
        for (Method method : Class.forName("android.net.ITetheringConnector").getMethods()) {
            if (!method.getName().equals(name)) continue;
            if (method.getParameterCount() == 4) {
                method.invoke(connector, request, "com.android.shell", null, listener);
                return;
            }
            if (method.getParameterCount() == 3) {
                method.invoke(connector, request, "com.android.shell", listener);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    // This is an intentionally hidden Binder interface, reached only after the
    // existing Shizuku permission and targeted android.net exemptions above.
    @android.annotation.SuppressLint("BlockedPrivateApi")
    private static void credentials(IHotspotCallback callback) {
        try {
            Object wifi = service("wifi", "android.net.wifi.IWifiManager$Stub");
            Object config = Class.forName("android.net.wifi.IWifiManager").getDeclaredMethod("getSoftApConfiguration").invoke(wifi);
            if (config == null) throw new IllegalStateException("No hotspot configuration");
            Class<?> type = Class.forName("android.net.wifi.SoftApConfiguration");
            String ssid = (String) type.getMethod("getSsid").invoke(config), password = (String) type.getMethod("getPassphrase").invoke(config);
            int security = (Integer) type.getMethod("getSecurityType").invoke(config);
            if (ssid == null || password == null || security != 1) {
                reply(callback, "configure", "Hotspot is on. Choose WPA2 security in Android hotspot settings to let your Mac join automatically.");
                return;
            }
            Bundle b = new Bundle();
            b.putString("state", "active");
            b.putString("ssid", ssid);
            b.putString("passphrase", password);
            b.putString("security", "wpa2");
            b.putString("band", "auto");
            b.putBoolean("internet", true);
            callback.onResult(b);
        } catch (Exception e) {
            reply(callback, "configure", "Hotspot is on. Android did not allow its network details to be read; join it using the phone’s hotspot settings.");
        }
    }

    private static void reply(IHotspotCallback callback, String state, String message) {
        try {
            Bundle b = new Bundle();
            b.putString("state", state);
            b.putString("message", message);
            callback.onResult(b);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
