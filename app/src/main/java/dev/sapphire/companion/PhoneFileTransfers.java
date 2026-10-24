package dev.sapphire.companion;

import android.content.Context;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Disk work is serial, bounded and separate from the BLE/main looper. */
final class PhoneFileTransfers {
    private static PhoneFileTransfers instance;
    static synchronized PhoneFileTransfers get(Context context) {
        if (instance == null) instance = new PhoneFileTransfers(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final IncomingFileTransfer receiver;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicLong generation = new AtomicLong();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32));
    private final ThreadPoolExecutor saver = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2));
    private PhoneFileTransfers(Context context) {
        this.context = context;
        receiver = new IncomingFileTransfer(new File(context.getCacheDir(), "received-files-v1"));
    }
    void reset() {
        generation.incrementAndGet();
        worker.getQueue().clear();
        worker.execute(receiver::abort); // preserves completed files awaiting Save/Discard
    }
    void receive(String type, JSONObject payload, Consumer<JSONObject> reply) {
        long ticket = generation.get();
        try {
            worker.execute(() -> {
                if (ticket != generation.get()) return;
                JSONObject result = receiver.handle(type, payload, context.getSharedPreferences("settings", 0).getBoolean("files", false), SystemClock.elapsedRealtime());
                main.post(() -> { if (ticket == generation.get()) reply.accept(result); });
            });
        } catch (RejectedExecutionException overloaded) { reset(); }
    }
    void tick() {
        long ticket = generation.get();
        try { worker.execute(() -> { if (ticket == generation.get()) receiver.expire(SystemClock.elapsedRealtime()); }); }
        catch (RejectedExecutionException overloaded) { reset(); }
    }
    IncomingFileTransfer.Received received() { return receiver.received(); }
    boolean discard(String id) { return receiver.discard(id); }
    boolean hasStoredFile() { return receiver.hasStoredFile(); }
    boolean discardUnavailable() { return receiver.discardUnavailable(); }
    void save(String id, android.net.Uri destination, Consumer<Boolean> completion) {
        try {
            saver.execute(() -> {
                boolean saved = false;
                try {
                    IncomingFileTransfer.Received expected = receiver.received();
                    if (expected == null || !expected.id.equals(id)) throw new IOException("Staged file unavailable");
                    try (InputStream input = receiver.openReceived(id); OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
                        if (output == null) throw new IOException("Destination unavailable");
                        IncomingFileTransfer.copyVerified(input, output, expected);
                    }
                    saved = true; // only after both streams close successfully
                } catch (Exception unavailable) { saved = false; }
                boolean result = saved;
                // Keep the staged copy until explicitly discarded, even after Save.
                main.post(() -> completion.accept(result));
            });
        } catch (RejectedExecutionException overloaded) { completion.accept(false); }
    }
}
