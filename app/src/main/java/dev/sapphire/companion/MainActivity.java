package dev.sapphire.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.util.*;

public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    private TextView status, paired, code, hotspotStatus, notificationStatus;
    private Button confirm, connectionButton;
    private TextView connectionTitle, connectionBadge, pairingHint;
    private LinearLayout verification, pairingDetails;
    private SapphireUi ui;
    private final ScrollView[] pages = new ScrollView[3];
    private int selectedPage;
    private com.google.android.material.bottomnavigation.BottomNavigationView navigation;
    private String savedMacName = "";
    private EditText qr;
    private String permissionAction;
    private final Runnable observer = this::refresh;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refreshLoop = new Runnable() {
        public void run() {
            refresh();
            main.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ui = new SapphireUi(this);
        try {
            org.json.JSONObject record = new PairingStore(this).load();
            if (record != null) savedMacName = record.optString("macName", "");
        } catch (Exception ignored) { }
        LinearLayout root = ui.column();
        LinearLayout brand = ui.row();
        brand.setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(12));
        ui.icon(brand, R.drawable.ic_devices, R.color.sapphire_primary, 24);
        TextView brandName = new TextView(this);
        brandName.setText("sapphire");
        brandName.setTextSize(22);
        brandName.setTextColor(ui.color(R.color.sapphire_text));
        brandName.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-2, -2);
        brandParams.leftMargin = ui.dp(10);
        brand.addView(brandName, brandParams);
        root.addView(brand, ui.width());
        FrameLayout viewport = new FrameLayout(this);
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < pages.length; i++) {
            LinearLayout content = ui.column();
            content.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(28));
            // Center content on tablets without stretching cards across the entire display.
            FrameLayout centered = new FrameLayout(this);
            FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                    Math.min(getResources().getDisplayMetrics().widthPixels, ui.dp(640)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            centered.addView(content, contentParams);
            pages[i] = new ScrollView(this);
            pages[i].setFillViewport(true);
            pages[i].setVerticalScrollBarEnabled(false);
            pages[i].addView(centered, new ScrollView.LayoutParams(-1, -2));
            viewport.addView(pages[i], new FrameLayout.LayoutParams(-1, -1));
            if (i == 0) connectionPage(content);
            else if (i == 1) featuresPage(content);
            else settingsPage(content);
        }
        navigation =
                new com.google.android.material.bottomnavigation.BottomNavigationView(this);
        navigation.setBackgroundColor(ui.color(R.color.sapphire_surface_variant));
        navigation.setElevation(0);
        navigation.setLabelVisibilityMode(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        navigation.getMenu().add(0, R.id.nav_connection, 0, "Connection").setIcon(R.drawable.ic_devices);
        navigation.getMenu().add(0, R.id.nav_features, 1, "Features").setIcon(R.drawable.ic_features);
        navigation.getMenu().add(0, R.id.nav_settings, 2, "Settings").setIcon(R.drawable.ic_settings);
        navigation.setOnItemSelectedListener(item -> {
            showPage(item.getItemId() == R.id.nav_connection ? 0 : item.getItemId() == R.id.nav_features ? 1 : 2);
            return true;
        });
        root.addView(navigation, ui.width());
        selectedPage = saved == null ? 0 : saved.getInt("page", 0);
        navigation.setSelectedItemId(selectedPage == 0 ? R.id.nav_connection : selectedPage == 1 ? R.id.nav_features : R.id.nav_settings);
        showPage(selectedPage);
        ui.install(root);
        if (saved != null) {
            pairingDetails.setVisibility(saved.getBoolean("pairingExpanded") ? View.VISIBLE : View.GONE);
            qr.setText(saved.getString("pairingText", ""));
            for (int i = 0; i < pages.length; i++) {
                final ScrollView page = pages[i];
                final int scroll = saved.getInt("scroll" + i, 0);
                page.post(() -> page.scrollTo(0, scroll));
            }
        }
        if (getIntent().hasExtra("qr")) {
            String challenge = getIntent().getStringExtra("qr");
            getIntent().removeExtra("qr");
            pairingDetails.setVisibility(View.VISIBLE);
            qr.setText(challenge);
        }
        refresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        android.util.Log.i("SapphireLink", "Launcher QR extra=" + intent.hasExtra("qr"));
        setIntent(intent);
        if (intent.hasExtra("qr")) {
            navigation.setSelectedItemId(R.id.nav_connection);
            pairingDetails.setVisibility(View.VISIBLE);
            qr.setText(intent.getStringExtra("qr"));
            intent.removeExtra("qr");
        }
    }

    private void showPage(int index) {
        selectedPage = index;
        for (int i = 0; i < pages.length; i++) pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
    }

    private void pageTitle(LinearLayout content, String title, String subtitle) {
        ui.heading(content, title, 36);
        ui.space(content, 6);
        ui.body(content, subtitle);
        ui.space(content, 24);
    }

    private void connectionPage(LinearLayout content) {
        pageTitle(content, "Better together.", "Your phone and Mac, in sync.");
        LinearLayout hero = ui.card(content, R.color.sapphire_primary_container, 32);
        LinearLayout top = ui.row();
        ui.icon(top, R.drawable.ic_devices, R.color.sapphire_on_primary_container, 48);
        LinearLayout badge = ui.column();
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(0, -2, 1);
        badgeParams.leftMargin = ui.dp(20);
        top.addView(badge, badgeParams);
        connectionBadge = ui.text(badge, "READY TO CONNECT", 11, R.color.sapphire_on_primary_container, true);
        connectionBadge.setLetterSpacing(0.08f);
        hero.addView(top, ui.width());
        ui.space(hero, 24);
        connectionTitle = ui.text(hero, "Your Mac, one tap away", 28, R.color.sapphire_on_primary_container, true);
        paired = ui.text(hero, "No Mac paired yet", 16, R.color.sapphire_on_primary_container, true);
        ui.space(hero, 8);
        status = ui.text(hero, "Start a connection to find your Mac.", 14, R.color.sapphire_on_primary_container, false);
        ui.space(hero, 12);
        connectionButton = ui.button(hero, "Start connection", R.drawable.ic_arrow, true, () -> ensureConnection(null));

        verification = ui.card(content, R.color.sapphire_secondary_container, 28);
        ui.cardHeading(verification, R.drawable.ic_shield, "Check your pairing code", "The same six digits must appear on your Mac.");
        ui.space(verification, 12);
        code = ui.text(verification, "", 40, R.color.sapphire_on_secondary_container, true);
        code.setTypeface(android.graphics.Typeface.MONOSPACE);
        code.setLetterSpacing(0.12f);
        code.setGravity(Gravity.CENTER);
        code.setMaxLines(1);
        code.setAutoSizeTextTypeUniformWithConfiguration(20, 40, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        pairingHint = ui.body(verification, "Confirm on both devices to finish pairing.");
        confirm = ui.button(verification, "Codes match · Confirm", R.drawable.ic_check, true, () ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Do the codes match?")
                .setMessage("Confirm only if these exact six digits also appear on your Mac. If the Mac shows no code, cancel and reconnect.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm matching codes", (d, w) -> send("confirm", null)).show());

        ui.section(content, "Make the connection", null);
        LinearLayout pairing = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(pairing, R.drawable.ic_qr, "Pair a Mac", "A quick scan. A secure connection.");
        ui.space(pairing, 16);
        ui.body(pairing, "On your Mac, open Sapphire → Settings → Continuity → Pair a phone.");
        ui.button(pairing, "Scan Mac QR code", R.drawable.ic_qr, true, () -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 12);
            else startActivityForResult(new Intent(this, ScannerActivity.class), 20);
        });
        ui.button(pairing, "Other pairing options", 0, false, () -> {
            pairingDetails.setVisibility(pairingDetails.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        pairingDetails = ui.column();
        pairingDetails.setVisibility(View.GONE);
        pairing.addView(pairingDetails, ui.width());
        ui.space(pairingDetails, 16);
        ui.body(pairingDetails, "Paste pairing text from your Mac, or make this phone discoverable to pair over Bluetooth.");
        com.google.android.material.textfield.TextInputLayout input = new com.google.android.material.textfield.TextInputLayout(
                this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        input.setHint("Mac pairing text");
        LinearLayout.LayoutParams inputParams = ui.width();
        inputParams.topMargin = ui.dp(16);
        pairingDetails.addView(input, inputParams);
        qr = new com.google.android.material.textfield.TextInputEditText(input.getContext());
        qr.setTextSize(14);
        qr.setMinLines(2);
        qr.setMaxLines(4);
        qr.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        qr.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.addView(qr, new LinearLayout.LayoutParams(-1, -2));
        ui.button(pairingDetails, "Use pairing text", R.drawable.ic_arrow, true, () -> {
            String text = qr.getText().toString().trim();
            if (text.isEmpty()) { input.setError("Paste the pairing text from your Mac."); return; }
            input.setError(null);
            ensureConnection(null);
            main.postDelayed(() -> send("qr", text), 300);
            qr.setText("");
            qr.clearFocus();
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(qr.getWindowToken(), 0);
        });
        ui.button(pairingDetails, "Pair over Bluetooth", R.drawable.ic_devices, false, () -> ensureConnection("pair"));
        LinearLayout privacy = ui.card(content, R.color.sapphire_surface_variant, 24);
        ui.cardHeading(privacy, R.drawable.ic_shield, "Private by design", null);
        ui.space(privacy, 8);
        ui.body(privacy, "Your clipboard, notifications and controls travel over an encrypted connection to your paired Mac.");
    }

    private void featuresPage(LinearLayout content) {
        pageTitle(content, "Your flow.", "Choose what moves between your devices.");
        LinearLayout clipboard = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(clipboard, R.drawable.ic_clipboard, "Clipboard", "Copy here. Continue there.");
        ui.toggle(clipboard, "Receive Mac clipboard", "clipboard", true, null);
        ui.body(clipboard, "Mac text arrives automatically while connected. Android limits clipboard access in the background.");
        ui.button(clipboard, "Send phone clipboard to Mac", R.drawable.ic_arrow, false, () -> {
            if (LinkService.instance != null && LinkService.instance.secure) LinkService.instance.sendClipboardFromActivity();
            else Toast.makeText(this, "Connect to your Mac first.", Toast.LENGTH_SHORT).show();
        });
        LinearLayout notifications = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(notifications, R.drawable.ic_bell, "Notifications", "Stay in the loop on your Mac.");
        ui.toggle(notifications, "Share notifications", "notifications", false, this::refresh);
        ui.body(notifications, "Mirror notifications and supported actions. Ongoing, secret, work-profile and Sapphire notifications are excluded. Android may hide sensitive content. Unlock your phone to use replies and actions.");
        ui.space(notifications, 12);
        notificationStatus = ui.body(notifications, "");
        ui.button(notifications, "Manage notification access", R.drawable.ic_bell, false,
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        LinearLayout media = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(media, R.drawable.ic_music, "Now playing", "Your phone's music, within reach.");
        ui.toggle(media, "Share playback & controls", "media", false, null);
        ui.body(media, "Share track, artist, album and position; play, pause, skip and seek from your Mac. Requires notification access. Audio is never recorded or streamed.");
        LinearLayout handoff = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(handoff, R.drawable.ic_link, "Link handoff", "Pick up where you left off.");
        ui.toggle(handoff, "Receive links from Mac", "handoff", false, () -> {
            if (!getSharedPreferences("settings", 0).getBoolean("handoff", false))
                getSystemService(NotificationManager.class).cancel("sapphire-link-handoff", 2003);
        });
        ui.body(handoff, "Allow Sapphire notifications to receive links. Tap a notification to review before opening. To send a phone link, use your browser's Share menu → Send link to Mac.");
        LinearLayout phoneActions = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(phoneActions, R.drawable.ic_devices, "Phone drafts", "Review before you act.");
        ui.toggle(phoneActions, "Receive SMS and dialer drafts", "phoneActions", false, () -> {
            if (!getSharedPreferences("settings", 0).getBoolean("phoneActions", false) && LinkService.instance != null && LinkService.instance.phoneActions != null)
                LinkService.instance.phoneActions.reset();
        });
        ui.body(phoneActions, "Mac drafts arrive as private notifications and expire after two minutes. Review on your unlocked phone, then open its SMS composer or dialer. Sapphire never sends an SMS or places a call. No contacts, SMS inbox or call history is read.");
        LinearLayout hotspot = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(hotspot, R.drawable.ic_hotspot, "Mobile hotspot", "Internet for your Mac, on the go.");
        ui.toggle(hotspot, "Allow Mac hotspot control", "hotspot", true, null);
        ui.body(hotspot, "Uses Sapphire Hotspot Helper with Shizuku authorization. Requires mobile data and carrier support.");
        ui.space(hotspot, 8);
        hotspotStatus = ui.body(hotspot, "");
        ui.button(hotspot, "Set up hotspot helper", R.drawable.ic_hotspot, false, this::openHelper);
        ui.button(hotspot, "Android hotspot settings", 0, false, () -> {
            try { startActivity(new Intent("android.settings.TETHER_SETTINGS")); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
        });
    }

    private void settingsPage(LinearLayout content) {
        pageTitle(content, "Make it yours.", "Connection, access and a little peace of mind.");
        LinearLayout access = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(access, R.drawable.ic_shield, "Android access", "You're always in control.");
        ui.space(access, 12);
        ui.body(access, "Nearby devices keeps your Mac connected. Camera access is used only to scan a pairing QR code.");
        ui.button(access, "App permissions", R.drawable.ic_settings, false, () -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))));
        ui.button(access, "Notification access", R.drawable.ic_bell, false,
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        ui.section(content, "Connection controls", null);
        LinearLayout controls = ui.card(content, R.color.sapphire_surface, 28);
        ui.cardHeading(controls, R.drawable.ic_devices, "Your paired Mac", null);
        ui.space(controls, 8);
        ui.body(controls, "Stopping disconnects this phone. Forgetting removes the saved pairing from Sapphire on this phone.");
        ui.button(controls, "Stop connection", 0, false, () -> send("stop", null));
        com.google.android.material.button.MaterialButton forget = ui.button(controls, "Forget this Mac", 0, false, () ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Forget this Mac?")
                    .setMessage("Remove the saved Mac pairing from this phone? You'll need to pair again to reconnect.")
                    .setNegativeButton("Keep Mac", null)
                    .setPositiveButton("Forget Mac", (d, w) -> { savedMacName = ""; send("forget", null); }).show());
        forget.setTextColor(ui.color(R.color.sapphire_error));
        ui.section(content, "About Sapphire", null);
        LinearLayout about = ui.card(content, R.color.sapphire_surface_variant, 28);
        ui.heading(about, "A little closer. A lot more connected.", 23);
        ui.space(about, 12);
        ui.body(about, "Bluetooth pairing, battery status, text clipboard, notifications, playback controls, link handoff and hotspot access. File transfer and screen sharing aren't available yet.");
        ui.space(about, 16);
        String version = "";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException ignored) { }
        ui.text(about, "Sapphire Companion · " + version, 12, R.color.sapphire_muted, true);
    }

    private void openHelper() {
        try {
            startActivity(new Intent().setComponent(new ComponentName("dev.sapphire.companion.hotspothelper", "dev.sapphire.helper.HelperActivity")));
        } catch (Exception e) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Set up your hotspot")
                    .setMessage("Install SapphireHotspotHelper.apk from the same build, then return here.")
                    .setPositiveButton("Got it", null).show();
        }
    }

    private void refresh() {
        LinkService service = LinkService.instance;
        boolean connected = service != null && service.secure;
        String name = service != null && !service.macName.isEmpty() ? service.macName : savedMacName;
        if (service != null) savedMacName = service.macName;
        status.setText(service == null ? "Start a connection to find your Mac." : service.status);
        paired.setText(name.isEmpty() ? "No Mac paired yet" : name);
        connectionTitle.setText(connected ? "You're connected." : (service == null ? "Your Mac, one tap away" : "Let's get connected."));
        connectionBadge.setText(connected ? "●  ENCRYPTED CONNECTION" : (service == null ? "READY TO CONNECT" : "CONNECTION STATUS"));
        connectionButton.setText(connected ? "Connected securely" : (service == null ? "Start connection" : "Restart connection"));
        connectionButton.setEnabled(!connected);
        boolean hasCode = service != null && !service.code.isEmpty() && !service.secure;
        verification.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        code.setText(hasCode ? service.code : "");
        confirm.setVisibility(hasCode && !service.localConfirmed ? View.VISIBLE : View.GONE);
        pairingHint.setText(service != null && service.localConfirmed ? "Confirmed on phone. Confirm on your Mac to finish." : "Confirm on both devices to finish pairing.");
        hotspotStatus.setText(service == null ? "" : service.hotspotStatus);
        hotspotStatus.setVisibility(hotspotStatus.length() == 0 ? View.GONE : View.VISIBLE);
        notificationStatus.setText(NotificationMirror.enabled(this)
                ? (NotificationMirror.instance != null ? "Notification access is ready." : "Waiting for Android notification access…")
                : "Enable sharing and allow Android notification access to begin.");
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", selectedPage);
        state.putBoolean("pairingExpanded", pairingDetails.getVisibility() == View.VISIBLE);
        state.putString("pairingText", qr.getText().toString());
        for (int i = 0; i < pages.length; i++) state.putInt("scroll" + i, pages[i].getScrollY());
        super.onSaveInstanceState(state);
    }

    private void ensureConnection(String action) {
        if (Build.VERSION.SDK_INT >= 31 && (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) {
            permissionAction = action;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE}, 10);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        send(action, null);
    }

    private void send(String action, String challenge) {
        Intent i = new Intent(this, LinkService.class);
        if (action != null) i.setAction(action);
        if (challenge != null) i.putExtra("qr", challenge);
        try {
            startForegroundService(i);
        } catch (Exception e) {
            Toast.makeText(this, "Allow Nearby devices and turn on Bluetooth first.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 10) {
            if (results.length > 0 && Arrays.stream(results).allMatch(r -> r == PackageManager.PERMISSION_GRANTED)) {
                String action = permissionAction;
                permissionAction = null;
                ensureConnection(action);
            }
        } else if (request == 12 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            startActivityForResult(new Intent(this, ScannerActivity.class), 20);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 20 && result == RESULT_OK && data != null) {
            qr.setText(data.getStringExtra("qr"));
            ensureConnection(null);
            main.postDelayed(() -> {
                send("qr", qr.getText().toString());
                qr.setText("");
            }, 300);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LinkService.observers.add(observer);
        main.post(refreshLoop);
    }

    @Override
    protected void onPause() {
        LinkService.observers.remove(observer);
        main.removeCallbacks(refreshLoop);
        super.onPause();
    }
}
