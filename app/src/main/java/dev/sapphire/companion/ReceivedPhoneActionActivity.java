package dev.sapphire.companion;

import android.app.KeyguardManager;
import android.os.Bundle;
import android.widget.*;

/** Non-exported review, followed by the system composer/dialer. Never sends/calls. */
public final class ReceivedPhoneActionActivity extends androidx.appcompat.app.AppCompatActivity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setShowWhenLocked(false); setTurnScreenOn(false);
        String id = getIntent().getStringExtra("requestId"), token = getIntent().getStringExtra("token");
        LinkService service = LinkService.instance;
        PhoneActions actions = service != null && service.secure ? service.phoneActions : null;
        PhoneActionPolicy.Draft draft = actions == null ? null : actions.peek(id, token);
        if (getSystemService(KeyguardManager.class).isDeviceLocked() || draft == null) { finish(); return; }
        SapphireUi ui = new SapphireUi(this);
        LinearLayout content = ui.confirmation("Review phone draft", "Prepared by your paired Mac. Sending or calling still requires the system app on this phone.", R.drawable.ic_devices);
        LinearLayout preview = ui.card(content, R.color.sapphire_surface_variant, 28);
        ui.heading(preview, draft.number, 20);
        if ("sms".equals(draft.kind)) {
            TextView text = ui.body(preview, draft.body.isEmpty() ? "Empty message draft" : draft.body);
            text.setTextIsSelectable(true);
        }
        ui.button(content, "sms".equals(draft.kind) ? "Open SMS composer" : "Open dialer", R.drawable.ic_arrow, true, () -> {
            if (LinkService.instance != service || !service.secure || getSystemService(KeyguardManager.class).isDeviceLocked()
                    || !actions.consume(id, token)) { finish(); return; }
            try { startActivity(PhoneActionIntents.review(draft)); finish(); }
            catch (RuntimeException unavailable) { Toast.makeText(this, "No compatible phone app is available. Nothing was sent or called.", Toast.LENGTH_LONG).show(); finish(); }
        });
        ui.button(content, "Discard", 0, false, () -> { if (LinkService.instance == service && service.secure) actions.consume(id, token); finish(); });
    }
}
