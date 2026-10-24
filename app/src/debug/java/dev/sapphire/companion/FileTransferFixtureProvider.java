package dev.sapphire.companion;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.List;

/** Private debug destination: one synthetic output in this app's cache, never a user path. */
public final class FileTransferFixtureProvider extends ContentProvider {
    static final String AUTHORITY = "dev.sapphire.companion.filefixture";
    static File output(Context context) { return new File(context.getCacheDir(), "file-provider-fixture.bin"); }
    static Uri destination(String run, boolean refuse) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(run)
            .appendPath(refuse ? "refused" : "output").build();
    }
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        List<String> path = uri.getPathSegments();
        if (context == null || !"w".equals(mode) || !"content".equals(uri.getScheme())
            || !AUTHORITY.equals(uri.getAuthority()) || uri.getQuery() != null || uri.getFragment() != null
            || path.size() != 2 || !IncomingFileTransfer.validID(path.get(0))) throw new FileNotFoundException("Invalid fixture destination");
        SharedPreferences journal = context.getSharedPreferences("file-transfer-fixture", Context.MODE_PRIVATE);
        if (!journal.getBoolean("active", false) || !path.get(0).equals(journal.getString("run", ""))
            || !"output".equals(path.get(1))) throw new FileNotFoundException("Fixture destination unavailable");
        return ParcelFileDescriptor.open(output(context), ParcelFileDescriptor.MODE_WRITE_ONLY
            | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
