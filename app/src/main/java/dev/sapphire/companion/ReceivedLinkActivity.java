package dev.sapphire.companion;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;

/** Non-exported: reached only by this app's immutable notification PendingIntent. */
public final class ReceivedLinkActivity extends androidx.appcompat.app.AppCompatActivity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String url = HandoffPolicy.url(getIntent().getDataString());
        if (url == null || !getSharedPreferences("settings", 0).getBoolean("handoff", false)) { finish(); return; }
        SapphireUi ui = new SapphireUi(this);
        LinearLayout content = ui.confirmation("Pick up on your phone", "A link from your paired Mac. Review it before opening in your browser.", R.drawable.ic_link);
        LinearLayout preview = ui.card(content, R.color.sapphire_surface_variant, 28);
        ui.heading(preview, Uri.parse(url).getHost(), 20);
        ui.space(preview, 12);
        TextView text = ui.body(preview, url);
        text.setTextIsSelectable(true);
        Button open = ui.button(content, "Open in browser", R.drawable.ic_arrow, true, () -> {});
        open.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); finish(); }
            catch (RuntimeException unavailable) { Toast.makeText(this, "No browser is available for this link.", Toast.LENGTH_LONG).show(); }
        });
        ui.button(content, "Cancel", 0, false, this::finish);
    }
}
