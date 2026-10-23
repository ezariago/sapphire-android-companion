package dev.sapphire.companion;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

/** Android Sharesheet target. The user explicitly confirms each outgoing URL. */
public final class ShareLinkActivity extends androidx.appcompat.app.AppCompatActivity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        CharSequence shared = Intent.ACTION_SEND.equals(getIntent().getAction())
                ? getIntent().getCharSequenceExtra(Intent.EXTRA_TEXT) : null;
        String url = HandoffPolicy.url(shared == null ? null : shared.toString());
        SapphireUi ui = new SapphireUi(this);
        LinearLayout content = ui.confirmation("Send link to Mac", "Continue browsing on your paired Mac. Review the link before sending.", R.drawable.ic_link);
        LinearLayout preview = ui.card(content, R.color.sapphire_surface_variant, 28);
        ui.heading(preview, url == null ? "Choose a web link" : android.net.Uri.parse(url).getHost(), 20);
        ui.space(preview, 12);
        TextView text = ui.body(preview, url == null ? "Share a single HTTP or HTTPS link without spaces or login credentials (up to 4 KB)." : url);
        text.setTextIsSelectable(true);
        Button send = ui.button(content, "Send to paired Mac", R.drawable.ic_arrow, true, () -> {});
        send.setEnabled(url != null);
        send.setOnClickListener(v -> {
            LinkService link = LinkService.instance;
            if (link != null && link.sendHandoff(url)) {
                Toast.makeText(this, "Link sent. Open it in Sapphire's Phone / Android settings on your Mac.", Toast.LENGTH_LONG).show(); finish();
            } else Toast.makeText(this, "Connect securely to your Mac first.", Toast.LENGTH_LONG).show();
        });
        ui.button(content, "Cancel", 0, false, this::finish);
    }
}
