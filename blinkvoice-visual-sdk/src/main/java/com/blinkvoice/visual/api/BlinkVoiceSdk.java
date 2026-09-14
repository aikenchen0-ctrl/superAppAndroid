package com.blinkvoice.visual.api;

import android.content.Context;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import com.blinkvoice.visual.debug.BlinkDebugLogger;
import com.blinkvoice.visual.detector.BlinkDetector;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SDK 级工具入口。用于在进入检测页前预热相机 Provider 和 MediaPipe 模型。
 */
public final class BlinkVoiceSdk {
    private static final ExecutorService PRELOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean PRELOAD_STARTED = new AtomicBoolean(false);
    private static final Object PRELOAD_LOCK = new Object();
    private static final ArrayList<PreloadCallback> PRELOAD_CALLBACKS = new ArrayList<>();
    private static BlinkDetector preloadedDetector;

    private BlinkVoiceSdk() {
    }

    public interface PreloadCallback {
        void onPreloadComplete(boolean detectorReady);
    }

    /**
     * 异步预热 CameraX Provider 和 MediaPipe 模型，降低后续进入检测页时的冷启动等待。
     *
     * <p>该方法不会打开或绑定摄像头；宿主仍需在真正检测前申请 CAMERA 权限。</p>
     */
    public static void preload(Context context) {
        preload(context, null);
    }

    /**
     * 异步预热 CameraX Provider 和 MediaPipe 模型，并在模型缓存可被接管时回调。
     */
    public static void preload(Context context, PreloadCallback callback) {
        if (context == null) {
            notifyPreloadCallback(callback, false);
            return;
        }
        List<PreloadCallback> callbacksToNotify = null;
        synchronized (PRELOAD_LOCK) {
            if (callback != null) {
                PRELOAD_CALLBACKS.add(callback);
            }
            if (preloadedDetector != null) {
                callbacksToNotify = drainCallbacksLocked();
            } else if (!PRELOAD_STARTED.compareAndSet(false, true)) {
                return;
            }
        }
        if (callbacksToNotify != null) {
            notifyPreloadCallbacks(callbacksToNotify, true);
            return;
        }
        Context appContext = context.getApplicationContext();
        ProcessCameraProvider.getInstance(appContext).addListener(
                () -> BlinkDebugLogger.log(false, "preload_camera_provider_ready"),
                ContextCompat.getMainExecutor(appContext)
        );
        PRELOAD_EXECUTOR.execute(() -> {
            BlinkDetector detector = new BlinkDetector(appContext, new NoOpBlinkListener());
            boolean detectorReady = false;
            List<PreloadCallback> completedCallbacks;
            try {
                detector.setup();
                synchronized (PRELOAD_LOCK) {
                    if (preloadedDetector == null) {
                        preloadedDetector = detector;
                        detector = null;
                    }
                    detectorReady = preloadedDetector != null;
                    PRELOAD_STARTED.set(false);
                    completedCallbacks = drainCallbacksLocked();
                }
            } catch (Exception ignored) {
                synchronized (PRELOAD_LOCK) {
                    PRELOAD_STARTED.set(false);
                    completedCallbacks = drainCallbacksLocked();
                }
            } finally {
                if (detector != null) {
                    detector.close();
                }
            }
            notifyPreloadCallbacks(completedCallbacks, detectorReady);
        });
    }

    static BlinkDetector claimPreloadedDetector(BlinkDetector.BlinkListener listener) {
        synchronized (PRELOAD_LOCK) {
            if (preloadedDetector == null) {
                return null;
            }
            BlinkDetector detector = preloadedDetector;
            preloadedDetector = null;
            PRELOAD_STARTED.set(false);
            detector.setListener(listener);
            return detector;
        }
    }

    private static List<PreloadCallback> drainCallbacksLocked() {
        ArrayList<PreloadCallback> callbacks = new ArrayList<>(PRELOAD_CALLBACKS);
        PRELOAD_CALLBACKS.clear();
        return callbacks;
    }

    private static void notifyPreloadCallbacks(List<PreloadCallback> callbacks, boolean detectorReady) {
        for (PreloadCallback callback : callbacks) {
            notifyPreloadCallback(callback, detectorReady);
        }
    }

    private static void notifyPreloadCallback(PreloadCallback callback, boolean detectorReady) {
        if (callback != null) {
            callback.onPreloadComplete(detectorReady);
        }
    }

    private static final class NoOpBlinkListener implements BlinkDetector.BlinkListener {
        @Override
        public void onResult(
                FaceLandmarkerResult result,
                long frameTimeMs,
                float leftEla,
                float rightEla,
                boolean leftClosed,
                boolean rightClosed,
                int blinkCount,
                long inferenceMs,
                int imageWidth,
                int imageHeight,
                int rotationDegrees
        ) {
        }

        @Override
        public void onError(String error) {
        }
    }
}
