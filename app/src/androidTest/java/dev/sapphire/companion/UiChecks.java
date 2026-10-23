package dev.sapphire.companion;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.io.File;
import java.io.FileOutputStream;

/** Presentation smoke checks. No connection actions, pairing changes or outgoing links. */
public final class UiChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private <T extends View> T find(View view, Class<T> type, String text) {
        if (type.isInstance(view) && (text == null || view instanceof TextView && text.contentEquals(((TextView) view).getText())))
            return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            T result = find(((ViewGroup) view).getChildAt(i), type, text);
            if (result != null) return result;
        }
        return null;
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        // Idle messages can precede the next rendered frame or navigation transition.
        android.os.SystemClock.sleep(350);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        require(bitmap != null, "Screenshot available");
        File output = new File(getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream stream = new FileOutputStream(output)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        bitmap.recycle();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        MainActivity activity = null;
        ShareLinkActivity share = null;
        try {
            runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            MainActivity main = activity;
            runOnMainSync(() -> {
                BottomNavigationView nav = find(main.getWindow().getDecorView(), BottomNavigationView.class, null);
                require(nav != null && nav.getMenu().size() == 3, "Three Material navigation destinations");
                nav.setSelectedItemId(R.id.nav_features);
                MaterialSwitch clipboard = find(main.getWindow().getDecorView(), MaterialSwitch.class, "Receive Mac clipboard");
                require(clipboard != null && clipboard.isShown(), "Feature page visible");
                require(clipboard.isChecked() == getTargetContext().getSharedPreferences("settings", 0).getBoolean("clipboard", true), "Existing preference retained");
                nav.setSelectedItemId(R.id.nav_settings);
                require(find(main.getWindow().getDecorView(), Button.class, "App permissions").isShown(), "Settings page visible");
                nav.setSelectedItemId(R.id.nav_connection);
                Button options = find(main.getWindow().getDecorView(), Button.class, "Other pairing options");
                options.performClick();
                require(find(main.getWindow().getDecorView(), EditText.class, null).isShown(), "Manual pairing expands");
                options.performClick();
                require(!find(main.getWindow().getDecorView(), EditText.class, null).isShown(), "Manual pairing collapses");
            });
            screenshot("ui-connection-dark");
            runOnMainSync(() -> find(main.getWindow().getDecorView(), BottomNavigationView.class, null).setSelectedItemId(R.id.nav_features));
            screenshot("ui-features-dark");
            runOnMainSync(() -> find(main.getWindow().getDecorView(), BottomNavigationView.class, null).setSelectedItemId(R.id.nav_settings));
            screenshot("ui-settings-dark");
            // A configuration recreation must retain the selected destination.
            ActivityMonitor monitor = new ActivityMonitor(MainActivity.class.getName(), null, false);
            addMonitor(monitor);
            runOnMainSync(() -> main.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO));
            MainActivity light = (MainActivity) waitForMonitorWithTimeout(monitor, 5000);
            removeMonitor(monitor);
            require(light != null, "Light theme recreation");
            activity = light;
            waitForIdleSync();
            runOnMainSync(() -> {
                BottomNavigationView nav = find(light.getWindow().getDecorView(), BottomNavigationView.class, null);
                require(nav.getSelectedItemId() == R.id.nav_settings, "Destination survives theme recreation");
                nav.setSelectedItemId(R.id.nav_connection);
            });
            screenshot("ui-connection-light");
            share = (ShareLinkActivity) startActivitySync(new Intent(getTargetContext(), ShareLinkActivity.class)
                    .setAction(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "https://example.com/sapphire")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            ShareLinkActivity valid = share;
            runOnMainSync(() -> require(find(valid.getWindow().getDecorView(), Button.class, "Send to paired Mac").isEnabled(), "Valid link can be confirmed"));
            screenshot("ui-share-link");
            runOnMainSync(valid::finish);
            share = (ShareLinkActivity) startActivitySync(new Intent(getTargetContext(), ShareLinkActivity.class)
                    .setAction(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "not a web link")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            ShareLinkActivity invalid = share;
            runOnMainSync(() -> require(!find(invalid.getWindow().getDecorView(), Button.class, "Send to paired Mac").isEnabled(), "Invalid link cannot be sent"));
            result.putString("result", "passed");
        } catch (Throwable failure) {
            result.putString("result", "failed");
            result.putString("error", failure.toString());
        } finally {
            ShareLinkActivity currentShare = share;
            MainActivity currentMain = activity;
            runOnMainSync(() -> {
                if (currentShare != null) currentShare.finish();
                if (currentMain != null) currentMain.finish();
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
        finish("passed".equals(result.getString("result")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
