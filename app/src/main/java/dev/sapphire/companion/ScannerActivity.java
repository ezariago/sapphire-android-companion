package dev.sapphire.companion;

import android.app.*;
import android.content.Intent;
import android.hardware.Camera;
import android.os.*;
import android.view.*;
import android.widget.*;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;

import java.util.*;
import java.util.concurrent.*;

/**
 * Offline QR scanner. Camera frames are never stored or sent over the network.
 */
@SuppressWarnings("deprecation")
public final class ScannerActivity extends androidx.appcompat.app.AppCompatActivity implements SurfaceHolder.Callback {
    private Camera camera;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean decoding = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean finished;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        SapphireUi ui = new SapphireUi(this);
        LinearLayout root = ui.column();
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(24));
        ui.button(content, "Close scanner", R.drawable.ic_close, false, this::finish);
        ui.space(content, 24);
        ui.heading(content, "Pair with a scan.", 34);
        ui.space(content, 8);
        ui.body(content, "On your Mac, open Sapphire → Settings → Continuity → Pair a phone.");
        root.addView(content, ui.width());
        FrameLayout cameraFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(-1, 0, 1);
        frameParams.setMargins(ui.dp(24), 0, ui.dp(24), 0);
        root.addView(cameraFrame, frameParams);
        SurfaceView preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        cameraFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        View guide = new View(this) {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                float size = Math.min(getWidth(), getHeight()) * .72f;
                float left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
                paint.setColor(0xAA000000);
                canvas.drawRect(0, 0, getWidth(), top, paint);
                canvas.drawRect(0, top + size, getWidth(), getHeight(), paint);
                canvas.drawRect(0, top, left, top + size, paint);
                canvas.drawRect(left + size, top, getWidth(), top + size, paint);
                paint.setColor(0xFFDEE4FF);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(ui.dp(3));
                canvas.drawRoundRect(left, top, left + size, top + size, ui.dp(24), ui.dp(24), paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
            }
        };
        guide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cameraFrame.addView(guide, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout footer = ui.column();
        footer.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24));
        ui.heading(footer, "Point at the Mac's QR code", 18);
        ui.space(footer, 8);
        ui.body(footer, "Scanning happens on your phone. Camera frames are never saved or uploaded.");
        root.addView(footer, ui.width());
        ui.install(root);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            Camera.Parameters p = camera.getParameters();
            if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(p);
            camera.setPreviewCallback((data, c) -> {
                if (finished || !decoding.compareAndSet(false, true)) return;
                Camera.Size size = c.getParameters().getPreviewSize();
                byte[] copy = Arrays.copyOf(data, size.width * size.height);
                worker.execute(() -> {
                    try {
                        MultiFormatReader reader = new MultiFormatReader();
                        reader.setHints(Map.of(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER, true));
                        Result result = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(copy, size.width, size.height, 0, 0, size.width, size.height, false))));
                        String text = result.getText();
                        if (text.length() <= 8192 && !finished) {
                            finished = true;
                            runOnUiThread(() -> {
                                setResult(RESULT_OK, new Intent().putExtra("qr", text));
                                finish();
                            });
                        }
                    } catch (Exception ignored) {
                    } finally {
                        decoding.set(false);
                    }
                });
            });
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "Camera could not start. You can paste the pairing text instead.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        close();
    }

    private void close() {
        finished = true;
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onDestroy() {
        close();
        worker.shutdownNow();
        super.onDestroy();
    }
}
