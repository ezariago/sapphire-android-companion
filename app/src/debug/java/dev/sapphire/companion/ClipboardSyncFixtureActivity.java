package dev.sapphire.companion;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.*;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

/** Fixed preference journal for synthetic relay checks. No clipboard reads or arbitrary payloads. */
public final class ClipboardSyncFixtureActivity extends Activity {
    private String run;
    private void report(String state) {
        try {
            android.util.AtomicFile file = new android.util.AtomicFile(new File(getFilesDir(), "clipboard-sync-fixture.json"));
            FileOutputStream out = file.startWrite();
            try { out.write(new JSONObject().put("run",run).put("state",state).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); file.finishWrite(out); }
            catch (Exception unavailable) { file.failWrite(out); }
        } catch (Exception ignored) {}
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); run = getIntent().getStringExtra("run");
        try { UUID.fromString(run); } catch (Exception invalid) { finish(); return; }
        SharedPreferences journal = getSharedPreferences("clipboard-sync-fixture",0), settings = getSharedPreferences("settings",0);
        String action = getIntent().getStringExtra("fixture");
        LinkService service = LinkService.instance;
        if ("begin".equals(action)) {
            if (journal.getBoolean("active",false) || service == null || !service.secure) { report("blocked"); finish(); return; }
            boolean savedPrefs = journal.edit().putBoolean("active",true).putString("run",run)
                .putBoolean("send",settings.getBoolean("sendClipboard",false))
                .putBoolean("background",settings.getBoolean("backgroundClipboard",false)).commit();
            if (!savedPrefs || !settings.edit().putBoolean("sendClipboard",true).putBoolean("backgroundClipboard",true).commit()) { report("error"); finish(); return; }
            service.backgroundClipboard.retry(); report("starting");
            Handler main = new Handler(Looper.getMainLooper());
            main.post(new Runnable() {
                int attempts;
                public void run() {
                    if (!journal.getBoolean("active",false) || !run.equals(journal.getString("run",""))) return;
                    String state = service.backgroundClipboard.state;
                    if ("ready".equals(state)) report("ready");
                    else if ("needs_setup".equals(state) || ++attempts >= 50) report("unavailable");
                    else { main.postDelayed(this,200); return; }
                    finish();
                }
            });
            return;
        }
        if (!journal.getBoolean("active",false) || !run.equals(journal.getString("run",""))) { report("wrong-run"); finish(); return; }
        if ("status".equals(action)) { report(service == null ? "off" : service.backgroundClipboard.state); finish(); return; }
        if (service != null) service.backgroundClipboard.stop();
        if ("quiesce".equals(action)) {
            report(settings.edit().putBoolean("sendClipboard",false).putBoolean("backgroundClipboard",false).commit() ? "quiesced" : "error");
        } else if ("restore".equals(action)) {
            boolean ok = settings.edit().putBoolean("sendClipboard",journal.getBoolean("send",false))
                .putBoolean("backgroundClipboard",journal.getBoolean("background",false)).commit();
            if (service != null) service.backgroundClipboard.refresh();
            report(ok && journal.edit().clear().commit() ? "restored" : "error");
        } else report("unknown");
        finish();
    }
}
