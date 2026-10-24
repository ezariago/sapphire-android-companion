package dev.sapphire.companion;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.json.*;

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
            File fileRoot = new File(getTargetContext().getCacheDir(), "file-fixture-" + UUID.randomUUID());
            try {
                ByteArrayOutputStream fixtureData = new ByteArrayOutputStream();
                try (InputStream asset = getTargetContext().getAssets().open("mac-file-transfer.json")) {
                    byte[] buffer = new byte[8192]; int read;
                    while ((read = asset.read(buffer)) != -1) { fixtureData.write(buffer, 0, read); require(fixtureData.size() <= 32768, "Bounded fixture asset"); }
                }
                JSONArray packets = new JSONArray(new String(fixtureData.toByteArray(), StandardCharsets.UTF_8));
                IncomingFileTransfer receiver = new IncomingFileTransfer(fileRoot);
                long now = 0;
                for (int i = 0; i < packets.length(); i++) {
                    JSONObject packet = packets.getJSONObject(i), payload = packet.getJSONObject("payload");
                    String type = packet.getString("type");
                    JSONObject ack = receiver.handle(type, payload, true, now++);
                    String expected = "file.offer".equals(type) ? "ready" : "file.finish".equals(type) ? "complete" : "chunk";
                    require(expected.equals(ack.getString("state")), "Mac wire fixture accepted by Android receiver");
                }
                IncomingFileTransfer.Received received = receiver.received();
                require(received != null && received.size == 4097, "Verified atomic file publication");
                try (InputStream file = receiver.openReceived(received.id)) {
                    ByteArrayOutputStream saved = new ByteArrayOutputStream();
                    IncomingFileTransfer.copyVerified(file, saved, received);
                    byte[] bytes = saved.toByteArray();
                    require(bytes.length == 4097, "Mac fixture exact length");
                    for (int i = 0; i < bytes.length; i++) require((bytes[i] & 255) == i % 251, "Mac fixture byte identity");
                }
                receiver.abort(); require(receiver.received() != null, "Disconnect preserves completed staged file");
                require(receiver.discard(received.id) && receiver.received() == null, "Explicit fixture-only discard");
                result.putBoolean("fileReceiverPassed", true);
            } finally {
                if (fileRoot.exists()) try (java.util.stream.Stream<Path> paths = Files.walk(fileRoot.toPath())) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
                }
            }
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
