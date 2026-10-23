package dev.sapphire.companion;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Shared Material 3 Expressive presentation for the companion's activities. */
final class SapphireUi {
    final Activity activity;
    SapphireUi(Activity activity) { this.activity = activity; }
    int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    int color(int resource) { return activity.getColor(resource); }
    LinearLayout column() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    LinearLayout row() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    LinearLayout.LayoutParams width() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
    void space(LinearLayout parent, int height) {
        View spacer = new View(activity);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
        spacer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        view.setFontFeatureSettings("kern");
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLineSpacing(dp(3), 1);
        parent.addView(view, width());
        return view;
    }
    TextView heading(LinearLayout parent, String value, int size) {
        TextView view = text(parent, value, size, R.color.sapphire_text, true);
        ViewCompat.setAccessibilityHeading(view, true);
        return view;
    }
    TextView body(LinearLayout parent, String value) {
        return text(parent, value, 14, R.color.sapphire_muted, false);
    }
    void section(LinearLayout parent, String title, String description) {
        space(parent, 24);
        heading(parent, title, 22);
        if (description != null) { space(parent, 4); body(parent, description); }
        space(parent, 14);
    }
    LinearLayout card(LinearLayout parent, int background, int radius) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setRadius(dp(radius));
        card.setCardBackgroundColor(color(background));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        LinearLayout.LayoutParams params = width();
        params.bottomMargin = dp(10);
        parent.addView(card, params);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.addView(content, new android.widget.FrameLayout.LayoutParams(-1, -2));
        return content;
    }
    ImageView icon(LinearLayout parent, int drawable, int tint, int size) {
        ImageView icon = new ImageView(activity);
        icon.setImageResource(drawable);
        icon.setImageTintList(ColorStateList.valueOf(color(tint)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(icon, new LinearLayout.LayoutParams(dp(size), dp(size)));
        return icon;
    }
    void cardHeading(LinearLayout parent, int icon, String title, String subtitle) {
        LinearLayout row = row();
        icon(row, icon, R.color.sapphire_primary, 26);
        LinearLayout labels = column();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(14);
        row.addView(labels, params);
        heading(labels, title, 19);
        if (subtitle != null) body(labels, subtitle);
        parent.addView(row, width());
    }
    MaterialButton button(LinearLayout parent, String title, int icon, boolean primary, Runnable action) {
        int style = primary ? com.google.android.material.R.attr.materialButtonStyle
                : com.google.android.material.R.attr.materialButtonTonalStyle;
        MaterialButton button = new MaterialButton(activity, null, style);
        button.setText(title);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMaxLines(3);
        button.setTextSize(15);
        button.setMinimumHeight(dp(56));
        button.setInsetTop(dp(4));
        button.setInsetBottom(dp(4));
        if (icon != 0) {
            button.setIconResource(icon);
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setIconPadding(dp(10));
        }
        LinearLayout.LayoutParams params = width();
        params.topMargin = dp(8);
        parent.addView(button, params);
        button.setOnClickListener(v -> action.run());
        return button;
    }
    MaterialSwitch toggle(LinearLayout parent, String title, String key, boolean defaultValue, Runnable changed) {
        MaterialSwitch toggle = new MaterialSwitch(activity);
        toggle.setText(title);
        toggle.setTextSize(16);
        toggle.setTextColor(color(R.color.sapphire_text));
        toggle.setMinHeight(dp(56));
        toggle.setSwitchPadding(dp(16));
        toggle.setChecked(activity.getSharedPreferences("settings", 0).getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((v, checked) -> {
            activity.getSharedPreferences("settings", 0).edit().putBoolean(key, checked).apply();
            if (changed != null) changed.run();
        });
        parent.addView(toggle, width());
        return toggle;
    }
    ScrollView scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }
    void install(LinearLayout root) {
        root.setBackgroundColor(color(R.color.sapphire_background));
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.setContentView(root);
        boolean light = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightNavigationBars(light);
        if (Build.VERSION.SDK_INT >= 29) activity.getWindow().setNavigationBarContrastEnforced(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        root.post(() -> {
            WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightStatusBars(light);
            WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightNavigationBars(light);
        });
    }
    LinearLayout confirmation(String title, String description, int icon) {
        LinearLayout root = column();
        LinearLayout content = column();
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        root.addView(scroll(content), new LinearLayout.LayoutParams(-1, -1));
        text(content, "SAPPHIRE", 13, R.color.sapphire_primary, true);
        space(content, 40);
        icon(content, icon, R.color.sapphire_primary, 48);
        space(content, 24);
        heading(content, title, 34);
        space(content, 12);
        body(content, description);
        space(content, 28);
        install(root);
        return content;
    }
}
