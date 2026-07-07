package com.blinkvoice.visual.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import com.blinkvoice.visual.debug.BlinkDebugLogger;
import com.blinkvoice.visual.detector.BlinkDetector;
import com.blinkvoice.visual.events.BlinkClassifierDebugSnapshot;
import com.blinkvoice.visual.events.BlinkEventClassifier;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlinkCaptureActivity extends ComponentActivity implements BlinkDetector.BlinkListener {
    public static final String EXTRA_EAR_CLOSE_THRESHOLD = "com.blinkvoice.visual.EAR_CLOSE_THRESHOLD";
    public static final String EXTRA_EAR_OPEN_THRESHOLD = "com.blinkvoice.visual.EAR_OPEN_THRESHOLD";
    public static final String EXTRA_DOUBLE_BLINK_WINDOW_MS = "com.blinkvoice.visual.DOUBLE_BLINK_WINDOW_MS";
    public static final String EXTRA_LONG_CLOSE_MIN_MS = "com.blinkvoice.visual.LONG_CLOSE_MIN_MS";
    public static final String EXTRA_MIN_SHORT_BLINK_MS = "com.blinkvoice.visual.MIN_SHORT_BLINK_MS";
    public static final String EXTRA_MAX_SHORT_BLINK_MS = "com.blinkvoice.visual.MAX_SHORT_BLINK_MS";
    public static final String EXTRA_NO_FACE_RESET_MS = "com.blinkvoice.visual.NO_FACE_RESET_MS";
    public static final String EXTRA_AUTO_FINISH_ON_EVENT = "com.blinkvoice.visual.AUTO_FINISH_ON_EVENT";
    public static final String EXTRA_EVENT_TYPES = "com.blinkvoice.visual.EVENT_TYPES";
    public static final String EXTRA_DEBUG_LOGGING_ENABLED = "com.blinkvoice.visual.DEBUG_LOGGING_ENABLED";
    public static final String EXTRA_DEBUG_OVERLAY_ENABLED = "com.blinkvoice.visual.DEBUG_OVERLAY_ENABLED";

    public static final String RESULT_EVENT_TYPE = "com.blinkvoice.visual.RESULT_EVENT_TYPE";
    public static final String RESULT_START_TIME_MS = "com.blinkvoice.visual.RESULT_START_TIME_MS";
    public static final String RESULT_END_TIME_MS = "com.blinkvoice.visual.RESULT_END_TIME_MS";
    public static final String RESULT_DURATION_MS = "com.blinkvoice.visual.RESULT_DURATION_MS";
    public static final String RESULT_CONFIDENCE = "com.blinkvoice.visual.RESULT_CONFIDENCE";
    public static final String RESULT_CANCEL_REASON = "com.blinkvoice.visual.RESULT_CANCEL_REASON";

    private static final String TAG = "BlinkVoiceCapture";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int MAX_CAMERA_BIND_ATTEMPTS = 3;
    private static final long CAMERA_BIND_RETRY_DELAY_MS = 250L;

    private PreviewView previewView;
    private TextView statusText;
    private TextView debugOverlayText;
    private Handler mainHandler;
    private ExecutorService cameraExecutor;
    private BlinkDetector detector;
    private BlinkEventClassifier classifier;
    private BlinkCaptureOptions options;
    private Set<BlinkEventType> targetEvents;
    private boolean finishedWithEvent = false;
    private ProcessCameraProvider cameraProvider;
    private int cameraBindAttempts = 0;
    private int analyzerFrameCount = 0;
    private long lastDebugOverlayUpdateMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        cameraExecutor = Executors.newSingleThreadExecutor();
        options = readOptions();
        targetEvents = options.getEventTypes();
        classifier = new BlinkEventClassifier(options);
        logDebug("capture_start closeTh=" + options.getEarCloseThreshold()
                + " openTh=" + options.getEarOpenThreshold()
                + " doubleWindow=" + options.getDoubleBlinkWindowMs()
                + " longClose=" + options.getLongCloseMinMs()
                + " short=[" + options.getMinShortBlinkMs() + "," + options.getMaxShortBlinkMs() + "]"
                + " noFaceReset=" + options.getNoFaceResetMs()
                + " autoFinish=" + options.isAutoFinishOnEvent()
                + " overlay=" + options.isDebugOverlayEnabled()
                + " target=" + targetEvents);
        setContentView(createContentView());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startDetection();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private FrameLayout createContentView() {
        previewView = new PreviewView(this);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        statusText = new TextView(this);
        statusText.setText("BlinkVoice starting...");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(18f);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackgroundColor(Color.argb(150, 0, 0, 0));
        statusText.setPadding(16, 16, 16, 16);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        statusParams.setMargins(24, 24, 24, 48);
        statusText.setLayoutParams(statusParams);

        if (options.isDebugOverlayEnabled()) {
            debugOverlayText = new TextView(this);
            debugOverlayText.setTextColor(Color.WHITE);
            debugOverlayText.setTextSize(12f);
            debugOverlayText.setGravity(Gravity.START);
            debugOverlayText.setBackgroundColor(Color.argb(175, 0, 0, 0));
            debugOverlayText.setPadding(16, 12, 16, 12);
            debugOverlayText.setText("BV Debug\nwaiting camera...");
            FrameLayout.LayoutParams debugParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP
            );
            debugParams.setMargins(16, 48, 16, 16);
            debugOverlayText.setLayoutParams(debugParams);
        }

        FrameLayout root = new FrameLayout(this);
        root.addView(previewView);
        if (debugOverlayText != null) {
            root.addView(debugOverlayText);
        }
        root.addView(statusText);
        return root;
    }

    private BlinkCaptureOptions readOptions() {
        Intent intent = getIntent();
        BlinkCaptureOptions.Builder builder = new BlinkCaptureOptions.Builder()
                .setEarCloseThreshold(intent.getFloatExtra(EXTRA_EAR_CLOSE_THRESHOLD, 0.22f))
                .setEarOpenThreshold(intent.getFloatExtra(EXTRA_EAR_OPEN_THRESHOLD, 0.25f))
                .setDoubleBlinkWindowMs(intent.getLongExtra(EXTRA_DOUBLE_BLINK_WINDOW_MS, 650L))
                .setLongCloseMinMs(intent.getLongExtra(EXTRA_LONG_CLOSE_MIN_MS, 500L))
                .setMinShortBlinkMs(intent.getLongExtra(EXTRA_MIN_SHORT_BLINK_MS, 30L))
                .setMaxShortBlinkMs(intent.getLongExtra(EXTRA_MAX_SHORT_BLINK_MS, 260L))
                .setNoFaceResetMs(intent.getLongExtra(EXTRA_NO_FACE_RESET_MS, 500L))
                .setAutoFinishOnEvent(intent.getBooleanExtra(EXTRA_AUTO_FINISH_ON_EVENT, true))
                .setDebugLoggingEnabled(intent.getBooleanExtra(EXTRA_DEBUG_LOGGING_ENABLED, false))
                .setDebugOverlayEnabled(intent.getBooleanExtra(EXTRA_DEBUG_OVERLAY_ENABLED, true));

        ArrayList<String> names = intent.getStringArrayListExtra(EXTRA_EVENT_TYPES);
        if (names != null && !names.isEmpty()) {
            EnumSet<BlinkEventType> events = EnumSet.noneOf(BlinkEventType.class);
            for (String name : names) {
                try {
                    events.add(BlinkEventType.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    // Ignore unknown event names from older/newer callers.
                }
            }
            if (!events.isEmpty()) {
                builder.setEventTypes(events);
            }
        }
        return builder.build();
    }

    private void startDetection() {
        detector = new BlinkDetector(this, this);
        detector.setDebugLoggingEnabled(options.isDebugLoggingEnabled());
        try {
            detector.setup();
            detector.setEarThreshold(options.getEarCloseThreshold());
            logDebug("detector_ready loadMs=" + detector.getLoadTimeMs());
        } catch (Exception e) {
            warnDebug("cancel reason=Model load failed error=" + e.getClass().getSimpleName());
            finishCanceled("Model load failed");
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraWithRetry();
            } catch (Exception e) {
                Log.e(TAG, "Camera provider unavailable", e);
                warnDebug("cancel reason=Camera provider unavailable error=" + e.getClass().getSimpleName());
                finishCanceled("Camera unavailable");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraWithRetry() {
        cameraBindAttempts++;
        try {
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build();
            analysis.setAnalyzer(cameraExecutor, this::processImage);

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
            );
            statusText.setText("Blink once, blink twice, or close eyes");
            Log.d(TAG, "Camera bound on attempt " + cameraBindAttempts);
            logDebug("camera_bound attempt=" + cameraBindAttempts);
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed on attempt " + cameraBindAttempts, e);
            warnDebug("camera_bind_failed attempt=" + cameraBindAttempts
                    + " error=" + e.getClass().getSimpleName());
            if (cameraBindAttempts < MAX_CAMERA_BIND_ATTEMPTS && !isFinishing()) {
                statusText.setText("Camera starting...");
                mainHandler.postDelayed(this::bindCameraWithRetry, CAMERA_BIND_RETRY_DELAY_MS);
            } else {
                finishCanceled("Camera unavailable: " + e.getClass().getSimpleName());
            }
        }
    }

    private void processImage(ImageProxy imageProxy) {
        try {
            MPImage mpImage = new BitmapImageBuilder(imageProxy.toBitmap()).build();
            long frameTimeMs = imageProxy.getImageInfo().getTimestamp() / 1_000_000L;
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            analyzerFrameCount++;
            if (options.isDebugLoggingEnabled() && analyzerFrameCount % 10 == 0) {
                // 帧入口日志用于确认 CameraX analyzer 是否持续把画面送进 SDK。
                logDebug("frame_in ts=" + frameTimeMs
                        + " rotation=" + rotation
                        + " size=" + imageProxy.getWidth() + "x" + imageProxy.getHeight());
            }
            detector.detectAsync(mpImage, frameTimeMs, rotation);
        } finally {
            imageProxy.close();
        }
    }

    @Override
    public void onResult(
            @NonNull FaceLandmarkerResult result,
            float leftEar,
            float rightEar,
            boolean leftClosed,
            boolean rightClosed,
            int blinkCount,
            long inferenceMs,
            int imageWidth,
            int imageHeight,
            int rotationDegrees
    ) {
        boolean hasFace = !result.faceLandmarks().isEmpty();
        BlinkCaptureResult event = classifier.accept(
                SystemClock.elapsedRealtime(),
                hasFace,
                leftEar,
                rightEar
        );
        BlinkClassifierDebugSnapshot snapshot = classifier.getDebugSnapshot();
        updateDebugOverlay(
                hasFace,
                leftEar,
                rightEar,
                leftClosed,
                rightClosed,
                blinkCount,
                inferenceMs,
                snapshot,
                event,
                null
        );
        if (event == null) {
            logDebug("activity classifier_event=null target=" + targetEvents);
            return;
        }

        if (!targetEvents.contains(event.getEventType())) {
            // 返回链路日志用于区分“分类器没识别到”和“识别到了但被事件过滤掉”。
            updateDebugOverlay(
                    hasFace,
                    leftEar,
                    rightEar,
                    leftClosed,
                    rightClosed,
                    blinkCount,
                    inferenceMs,
                    snapshot,
                    event,
                    "EVENT_FILTERED target=" + targetEvents
            );
            logDebug("activity event_filtered type=" + event.getEventType().name() + " target=" + targetEvents);
            return;
        }

        runOnUiThread(() -> {
            statusText.setText("Event: " + event.getEventType().name());
            if (options.isAutoFinishOnEvent()) {
                logDebug("activity finish_event type=" + event.getEventType().name()
                        + " duration=" + event.getDurationMs()
                        + " autoFinish=true");
                finishWithEvent(event);
            } else {
                logDebug("activity event_detected type=" + event.getEventType().name()
                        + " duration=" + event.getDurationMs()
                        + " autoFinish=false");
            }
        });
    }

    private void updateDebugOverlay(
            boolean hasFace,
            float leftEar,
            float rightEar,
            boolean leftClosed,
            boolean rightClosed,
            int blinkCount,
            long inferenceMs,
            BlinkClassifierDebugSnapshot snapshot,
            BlinkCaptureResult event,
            String activityReason
    ) {
        if (!options.isDebugOverlayEnabled() || debugOverlayText == null) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        boolean shouldShowImmediately = event != null || activityReason != null;
        if (!shouldShowImmediately && now - lastDebugOverlayUpdateMs < 200L) {
            return;
        }
        lastDebugOverlayUpdateMs = now;

        float avgEar = (leftEar + rightEar) / 2f;
        String eyeState;
        if (!hasFace) {
            eyeState = "NO_FACE";
        } else if (snapshot.isClosed()) {
            eyeState = "CLOSED";
        } else {
            eyeState = "OPEN";
        }
        String reason = activityReason != null ? activityReason : snapshot.getLastReason();
        String eventText = event != null ? event.getEventType().name() : snapshot.getLastEvent();
        String overlay = "BV Debug\n"
                + "face: " + (hasFace ? "YES" : "NO") + "  eye: " + eyeState + "\n"
                + "EAR L/R/AVG: " + formatFloat(leftEar) + " / " + formatFloat(rightEar)
                + " / " + formatFloat(avgEar) + "\n"
                + "threshold: close<" + formatFloat(options.getEarCloseThreshold())
                + " open>" + formatFloat(options.getEarOpenThreshold()) + "\n"
                + "seenOpen: " + (snapshot.hasSeenOpenEyes() ? "YES" : "NO")
                + "  phase: " + snapshot.getPhase() + "\n"
                + "closedMs: " + snapshot.getClosedDurationMs()
                + "  wait2Ms: " + snapshot.getPendingBlinkElapsedMs() + "\n"
                + "event: " + eventText + "  blinks: " + blinkCount + "\n"
                + "infer: " + inferenceMs + "ms\n"
                + "last: " + reason;

        runOnUiThread(() -> debugOverlayText.setText(overlay));
    }

    @Override
    public void onError(@NonNull String error) {
        runOnUiThread(() -> finishCanceled(error));
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDetection();
        } else {
            finishCanceled("Camera permission denied");
        }
    }

    private void finishWithEvent(BlinkCaptureResult event) {
        if (finishedWithEvent) {
            return;
        }
        finishedWithEvent = true;
        logDebug("finish_event type=" + event.getEventType().name()
                + " start=" + event.getStartTimeMs()
                + " end=" + event.getEndTimeMs()
                + " duration=" + event.getDurationMs());
        Intent data = new Intent()
                .putExtra(RESULT_EVENT_TYPE, event.getEventType().name())
                .putExtra(RESULT_START_TIME_MS, event.getStartTimeMs())
                .putExtra(RESULT_END_TIME_MS, event.getEndTimeMs())
                .putExtra(RESULT_DURATION_MS, event.getDurationMs())
                .putExtra(RESULT_CONFIDENCE, event.getConfidence());
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    private void finishCanceled(String message) {
        Log.w(TAG, "Capture canceled: " + message);
        warnDebug("cancel reason=" + message);
        if (statusText != null) {
            statusText.setText(message);
        }
        Intent data = new Intent().putExtra(RESULT_CANCEL_REASON, message);
        setResult(Activity.RESULT_CANCELED, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (detector != null) {
            detector.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }

    private void logDebug(String message) {
        BlinkDebugLogger.log(options != null && options.isDebugLoggingEnabled(), message);
    }

    private void warnDebug(String message) {
        BlinkDebugLogger.warn(options != null && options.isDebugLoggingEnabled(), message);
    }

    private String formatFloat(float value) {
        return String.format(Locale.US, "%.3f", value);
    }
}
