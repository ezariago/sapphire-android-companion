package dev.sapphire.companion;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Runs in its own fixture package. No activities, permissions, data or contacts. */
public final class IsolatedDeviceChecks extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            PhoneActionPolicy.Draft sms = PhoneActionPolicy.draft("sms", "+15550100000", "Synthetic fixture only");
            Intent message = PhoneActionIntents.review(sms);
            require(Intent.ACTION_SENDTO.equals(message.getAction()) && "smsto".equals(message.getData().getScheme()), "SMS draft action/scheme");
            require("+15550100000".equals(message.getData().getSchemeSpecificPart()) && sms.body.equals(message.getStringExtra("sms_body")), "SMS draft fields");
            Intent dial = PhoneActionIntents.review(PhoneActionPolicy.draft("dial", "+15550100000", ""));
            require(Intent.ACTION_DIAL.equals(dial.getAction()) && "tel".equals(dial.getData().getScheme()), "Review-only dialer intent");
            require(!Intent.ACTION_CALL.equals(dial.getAction()), "No immediate call");
            require(PhoneActionPolicy.draft("dial", "*#06#", "") == null && PhoneActionPolicy.draft("dial", "911", "") == null, "Service/short code rejection");
            PhoneActionPolicy.Store store = new PhoneActionPolicy.Store();
            require(store.offer("fixture", "token", sms, 0) == PhoneActionPolicy.Offer.READY, "Store ready");
            require(store.peek("fixture", "token", 120000) == null, "Expired draft rejection");
            java.security.KeyPair a = LinkCrypto.keyPair(), b = LinkCrypto.keyPair();
            byte[] shared = LinkCrypto.shared(a.getPrivate(), LinkCrypto.publicPoint(b.getPublic()));
            require(Arrays.equals(shared, LinkCrypto.shared(b.getPrivate(), LinkCrypto.publicPoint(a.getPublic()))), "Android ECDH");
            byte[] nonce = new byte[32]; Arrays.fill(nonce, (byte) 7);
            LinkCrypto.Session mac = new LinkCrypto.Session(shared, new byte[32], nonce, false), phone = new LinkCrypto.Session(shared, new byte[32], nonce, true);
            byte[] data = LinkCrypto.utf8("Isolated Android fixture"), frame = mac.seal(data);
            require(Arrays.equals(data, phone.open(frame)), "Android AEAD");
            boolean replay = false; try { phone.open(frame); } catch (Exception expected) { replay = true; }
            require(replay, "Replay rejection");
            GattFrames parser = new GattFrames(); byte[] complete = null;
            for (byte[] chunk : GattFrames.split(data, 1, 20)) { byte[] next = parser.accept(chunk, 0); if (next != null) complete = next; }
            require(Arrays.equals(data, complete), "Fragment reconstruction");
            result.putString("result", "passed");
            result.putBoolean("draftIntentsPassed", true); result.putBoolean("cryptoPassed", true);
            result.putBoolean("launchedExternalActivities", false);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "failed"); result.putString("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
