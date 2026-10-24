package dev.sapphire.companion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Debug-only, fixed-byte staging checks. Never opens a browser/document picker or user file. */
public final class FileTransferFixtureActivity extends Activity {
    private static final String NAME = "sapphire-file-transfer-fixture.bin";
    private SharedPreferences journal, settings;
    private String run;
    private PhoneFileTransfers files;
    private boolean saving;
    private static byte[] fixture() {
        byte[] data = new byte[4097]; for (int i = 0; i < data.length; i++) data[i] = (byte)(i % 251); return data;
    }
    private static String hash(byte[] data) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    private boolean owned(IncomingFileTransfer.Received value) throws Exception {
        return value != null && value.id.equals(journal.getString("expected", "")) && NAME.equals(value.name)
            && value.size == 4097 && hash(fixture()).equals(value.sha256);
    }
    private void report(String state) {
        try {
            JSONObject result = new JSONObject().put("run", run == null ? "" : run).put("state", state).put("action", getIntent().getStringExtra("fixture"));
            android.util.AtomicFile report = new android.util.AtomicFile(new File(getFilesDir(), "file-transfer-fixture-state.json"));
            FileOutputStream output = report.startWrite();
            try {
                output.write(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                report.finishWrite(output);
            } catch (Exception unavailable) { report.failWrite(output); }
        } catch (Exception ignored) { }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        run = getIntent().getStringExtra("run");
        if (!IncomingFileTransfer.validID(run)) { finish(); return; }
        journal = getSharedPreferences("file-transfer-fixture", MODE_PRIVATE);
        settings = getSharedPreferences("settings", MODE_PRIVATE);
        files = PhoneFileTransfers.get(this);
        String action = getIntent().getStringExtra("fixture");
        try {
            if ("begin".equals(action)) {
                if (journal.getBoolean("active", false) || files.isReceiving() || files.hasStoredFile()) { report("blocked"); return; }
                File output = FileTransferFixtureProvider.output(this);
                if (output.exists() && !output.delete()) { report("error"); return; }
                boolean original = settings.getBoolean("files", false);
                if (!journal.edit().putBoolean("active", true).putBoolean("original", original).putString("run", run).putString("expected", "").commit()) { report("error"); return; }
                if (!settings.edit().putBoolean("files", false).commit()) { report("error"); return; }
                report("disabled"); return;
            }
            if (!journal.getBoolean("active", false) || !run.equals(journal.getString("run", ""))) { report("wrong-run"); return; }
            if ("enable".equals(action)) {
                report(settings.edit().putBoolean("files", true).commit() ? "enabled" : "error");
            } else if ("expect".equals(action)) {
                String expected = getIntent().getStringExtra("transfer");
                report(IncomingFileTransfer.validID(expected) && journal.edit().putString("expected", expected).commit() ? "expected" : "error");
            } else if ("verify".equals(action)) {
                IncomingFileTransfer.Received value = files.received();
                if (!owned(value)) { report("not-fixture"); return; }
                IncomingFileTransfer reader = new IncomingFileTransfer(new File(getCacheDir(), "received-files-v1"));
                try (InputStream input = reader.openReceived(value.id); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    IncomingFileTransfer.copyVerified(input, output, value);
                    report(Arrays.equals(fixture(), output.toByteArray()) ? "verified" : "mismatch");
                }
            } else if ("save".equals(action) || "save-refused".equals(action)) {
                IncomingFileTransfer.Received value = files.received();
                if (!owned(value)) { report("not-fixture"); return; }
                File output = FileTransferFixtureProvider.output(this);
                if (output.exists() && !output.delete()) { report("error"); return; }
                boolean refused = "save-refused".equals(action);
                saving = true;
                report("saving");
                files.save(value.id, FileTransferFixtureProvider.destination(run, refused), success -> {
                    try {
                        boolean retained = owned(files.received());
                        if (refused) report(!success && !output.exists() && retained ? "save-refused" : "mismatch");
                        else {
                            byte[] actual;
                            try (InputStream input = new FileInputStream(output); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                                byte[] buffer = new byte[1024]; int count;
                                while ((count = input.read(buffer)) != -1) {
                                    if (bytes.size() + count > 4097) throw new IOException("Oversized fixture output");
                                    bytes.write(buffer, 0, count);
                                }
                                actual = bytes.toByteArray();
                            }
                            report(success && retained && Arrays.equals(fixture(), actual) && value.sha256.equals(hash(actual)) ? "saved" : "mismatch");
                        }
                    } catch (Exception unavailable) { report("error"); }
                    finally { saving = false; finish(); }
                });
            } else if ("discard".equals(action)) {
                IncomingFileTransfer.Received value = files.received();
                report(owned(value) && files.discard(value.id) && !files.hasStoredFile() ? "discarded" : "not-fixture");
            } else if ("restore".equals(action)) {
                IncomingFileTransfer.Received value = files.received();
                if (owned(value)) files.discard(value.id);
                File output = FileTransferFixtureProvider.output(this);
                if (output.exists() && !output.delete()) { report("error"); return; }
                boolean original = journal.getBoolean("original", false);
                if (settings.edit().putBoolean("files", original).commit() && journal.edit().clear().commit()) report("restored");
                else report("error");
            } else report("unknown");
        } catch (Exception unavailable) { report("error"); }
        finally { if (!saving) finish(); }
    }
}
