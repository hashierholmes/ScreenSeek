package hh.screenseek.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

/**
 * Foreground service responsible for screen capture and the floating result UI.
 *
 * Capture is kept in a service because the selection overlay and MediaProjection
 * session must remain independent from the settings Activity lifecycle.
 */
public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "ScreenSeekCaptureChannel";
    private WindowManager windowManager;
    private SnipOverlayView overlayView;
    private View resultDialogView;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private int progressStep = 0;
    private boolean isFinished = false;

    // Lightweight feedback shown while the Gemini request is running.
    private final String[] PROGRESS_MESSAGES = {
            "⟡ Optimizing screen snippet...",
            "⟡ Transmitting visual data...",
            "✦ Gemini is analyzing image context...",
            "✦ Synthesizing key insights...",
            "✦ Finalizing concise answer..."
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinished || resultDialogView == null) return;
            TextView tvStatus = (TextView) resultDialogView.findViewById(R.id.tvLoadingStatus);
            if (tvStatus != null) {
                tvStatus.setText(PROGRESS_MESSAGES[progressStep % PROGRESS_MESSAGES.length]);
                progressStep++;
                mainHandler.postDelayed(this, 900);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startServiceForeground();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    private void startServiceForeground() {
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(101, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("resultCode")) {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent data = intent.getParcelableExtra("resultData");

            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (projectionManager != null && data != null) {
                try {
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                    showSnipOverlay();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to start capture: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    stopSelf();
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void showSnipOverlay() {
        try {
            // Capture dimensions must match the physical display used by MediaProjection.
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;

            int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );

            overlayView = new SnipOverlayView(this, new SnipOverlayView.OnSnipListener() {
                @Override
                public void onConfirmed(final RectF rect) {
                    removeOverlayView();
                    captureAndSearch(rect);
                }

                @Override
                public void onCancelled() {
                    stopSelf();
                }
            });

            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            Toast.makeText(this, "Overlay Error: Grant overlay permission first!", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void captureAndSearch(final RectF cropRect) {
        try {
            // ImageReader receives frames from the mirrored virtual display.
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenSeekDisplay",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    null
            );

            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    imageReader.setOnImageAvailableListener(null, null);
                    Image image = null;
                    Bitmap fullBitmap = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            Image.Plane[] planes = image.getPlanes();
                            ByteBuffer buffer = planes[0].getBuffer();
                            int pixelStride = planes[0].getPixelStride();
                            int rowStride = planes[0].getRowStride();
                            // Row stride may contain padding, so the temporary bitmap can be wider than the display.
                            int rowPadding = rowStride - pixelStride * screenWidth;

                            fullBitmap = Bitmap.createBitmap(
                                    screenWidth + rowPadding / pixelStride,
                                    screenHeight,
                                    Bitmap.Config.ARGB_8888
                            );
                            fullBitmap.copyPixelsFromBuffer(buffer);

                            int x = Math.max(0, (int) cropRect.left);
                            int y = Math.max(0, (int) cropRect.top);
                            int width = Math.min((int) cropRect.width(), fullBitmap.getWidth() - x);
                            int height = Math.min((int) cropRect.height(), fullBitmap.getHeight() - y);

                            if (width > 0 && height > 0) {
                                // Crop only after copying the frame out of ImageReader's buffer.
                                Bitmap croppedBitmap = Bitmap.createBitmap(fullBitmap, x, y, width, height);
                                cleanupProjection();
                                processSnippetWithGemini(croppedBitmap);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) image.close();
                        if (fullBitmap != null) fullBitmap.recycle();
                    }
                }
            }, mainHandler);
        } catch (Exception e) {
            Toast.makeText(this, "Capture failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    private void processSnippetWithGemini(Bitmap croppedBitmap) {
        showResultDialog();
        startProgressFeedback();

        // Read the latest settings so changes take effect without restarting the service.
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String apiKey = prefs.getString(MainActivity.KEY_API_KEY, "");
        String modelName = prefs.getString(MainActivity.KEY_MODEL_NAME, MainActivity.DEFAULT_MODEL);

        GeminiApiHelper.askGemini(apiKey, modelName, croppedBitmap, new GeminiApiHelper.ApiCallback() {
            @Override
            public void onSuccess(final String rawResponseText) {
                final String formattedText = TextFormatter.cleanMarkdown(rawResponseText);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        stopProgressFeedback();
                        if (resultDialogView != null) {
                            TextView tvStatus = (TextView) resultDialogView.findViewById(R.id.tvLoadingStatus);
                            TextView tv = (TextView) resultDialogView.findViewById(R.id.tvResultText);
                            tvStatus.setText("✓ Completed");
                            tvStatus.setTextColor(0xFF10B981); // Green
                            tv.setText(formattedText);
                        }
                    }
                });
            }

            @Override
            public void onError(final String errorMessage) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        stopProgressFeedback();
                        if (resultDialogView != null) {
                            TextView tvStatus = (TextView) resultDialogView.findViewById(R.id.tvLoadingStatus);
                            TextView tv = (TextView) resultDialogView.findViewById(R.id.tvResultText);
                            tvStatus.setText("✕ Request Failed");
                            tvStatus.setTextColor(0xFFEF4444); // Red
                            tv.setText(errorMessage);
                        }
                    }
                });
            }
        });
    }

    private void startProgressFeedback() {
        isFinished = false;
        progressStep = 0;
        mainHandler.post(progressRunnable);
    }

    private void stopProgressFeedback() {
        isFinished = true;
        mainHandler.removeCallbacks(progressRunnable);
    }

    private void showResultDialog() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        resultDialogView = inflater.inflate(R.layout.layout_result_dialog, null);

        final TextView tv = (TextView) resultDialogView.findViewById(R.id.tvResultText);

        Button btnClose = (Button) resultDialogView.findViewById(R.id.btnCloseResult);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeResultDialog();
                stopSelf();
            }
        });

        Button btnCopy = (Button) resultDialogView.findViewById(R.id.btnCopyResult);
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String content = tv.getText().toString();
                if (!content.isEmpty()) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Gemini Result", content);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(ScreenCaptureService.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        });

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM;
        params.y = 0;

        windowManager.addView(resultDialogView, params);
    }

    /**
     * Releases all resources associated with the current MediaProjection session.
     * Centralizing cleanup keeps cancellation and normal completion consistent.
     */
    private void cleanupProjection() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void removeOverlayView() {
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    private void removeResultDialog() {
        if (resultDialogView != null && resultDialogView.isAttachedToWindow()) {
            windowManager.removeView(resultDialogView);
            resultDialogView = null;
        }
    }

    @Override
    public void onDestroy() {
        stopProgressFeedback();
        removeOverlayView();
        removeResultDialog();
        cleanupProjection();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "ScreenSeek Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle("ScreenSeek")
                .setContentText("Snipping assistant is active")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
    }
}