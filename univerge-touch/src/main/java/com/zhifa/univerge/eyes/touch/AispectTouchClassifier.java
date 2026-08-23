package com.zhifa.univerge.eyes.touch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import com.zhifa.univerge.eyes.aispect.AispectCollectionController;
import com.zhifa.univerge.eyes.aispect.AispectCanonicalModelRequest;
import com.zhifa.univerge.eyes.aispect.AispectDataLogger;
import com.zhifa.univerge.eyes.aispect.AispectImpactCNNClassifier;
import com.zhifa.univerge.eyes.aispect.AispectRemoteModelUpdater;
import com.zhifa.univerge.eyes.aispect.AispectModelUpdateReporter;
import com.zhifa.univerge.eyes.aispect.AispectModelPreloadGate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class AispectTouchClassifier implements AutoCloseable, AispectCollectionController.Listener {
    public static final String SDK_VERSION = "1.0.0";
    private final AispectCollectionController controller;
    private final String remoteModelBaseUrl;
    private final String remoteModelAppId;
    private final String remoteModelAppVersion;
    private final String remoteModelDeviceId;
    private final boolean allowInsecureRemoteModelTransport;
    private final Handler callbackHandler;
    private final ExecutorService modelUpdateExecutor;
    private final AispectModelPreloadGate modelPreloadGate;
    private final Object lifecycleLock = new Object();
    private volatile boolean closed;
    private AispectTouchListener listener;
    private volatile boolean running;
    private int lastEmittedPredictionSequence;
    private AispectTouchModelUpdateResult pendingUpdateReport;

    public AispectTouchClassifier(Context context) {
        this(context, AispectTouchConfig.defaultConfig());
    }

    public AispectTouchClassifier(Context context, AispectTouchConfig config) {
        AispectTouchConfig safeConfig = config == null ? AispectTouchConfig.defaultConfig() : config;
        controller = new AispectCollectionController(context);
        controller.config().enableCnnDiagnostics = safeConfig.enableCnnDiagnostics;
        controller.config().postTouchCaptureDelayMs = safeConfig.postTouchCaptureDelayMs;
        controller.config().maximumTouchTravelPx = safeConfig.maximumPressTravelPx;
        controller.interactionEngine().config().minimumSampleIntervalMs = safeConfig.minimumSampleIntervalMs;
        controller.interactionEngine().config().dragActivationRadiusPx = safeConfig.dragActivationRadiusPx;
        controller.interactionEngine().config().heavyTouchAreaThresholdPx2 = safeConfig.heavyTouchAreaThresholdPx2;
        allowInsecureRemoteModelTransport = safeConfig.allowInsecureRemoteModelTransport;
        controller.setAllowInsecureRemoteModelTransport(allowInsecureRemoteModelTransport);
        controller.setRemoteModelAllowedHosts(resolveAllowedHosts(safeConfig.remoteModelAllowedHosts, safeConfig.remoteModelBaseUrl));
        controller.setListener(this);
        remoteModelBaseUrl = safe(safeConfig.remoteModelBaseUrl);
        remoteModelAppId = safe(safeConfig.remoteModelAppId);
        remoteModelAppVersion = safe(safeConfig.remoteModelAppVersion);
        String configuredDeviceId = safe(safeConfig.remoteModelDeviceId).trim();
        remoteModelDeviceId = configuredDeviceId.isEmpty()
                ? AispectTouchDeviceId.getOrCreate(context)
                : configuredDeviceId;
        callbackHandler = new Handler(Looper.getMainLooper());
        modelPreloadGate = new AispectModelPreloadGate();
        modelUpdateExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "aispect-model-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void setListener(AispectTouchListener listener) {
        this.listener = listener;
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            if (!controller.isRecognitionRunning()) {
                controller.setEnabled(false);
                controller.startRecognition();
            }
            running = controller.isRecognitionRunning();
            lastEmittedPredictionSequence = 0;
            modelPreloadGate.schedule(modelUpdateExecutor, controller::preloadSelectedModel);
        }
    }

    public void stop() {
        synchronized (lifecycleLock) {
            running = false;
            controller.stopRecognition();
            lastEmittedPredictionSequence = 0;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean handleMotionEvent(MotionEvent event, int width, int height) {
        if (closed || !running || event == null) {
            return false;
        }
        return controller.handleRecognitionMotionEvent(event, width, height);
    }

    public List<AispectTouchModelInfo> availableModels() {
        List<AispectImpactCNNClassifier.ModelInfo> internalModels = controller.availableModels();
        ArrayList<AispectTouchModelInfo> output = new ArrayList<>();
        for (AispectImpactCNNClassifier.ModelInfo modelInfo : internalModels) {
            AispectTouchModelInfo publicInfo = AispectTouchModelInfo.fromInternal(modelInfo);
            if (publicInfo != null) {
                output.add(publicInfo);
            }
        }
        return Collections.unmodifiableList(output);
    }

    public AispectTouchModelInfo selectedModelInfo() {
        return AispectTouchModelInfo.fromInternal(controller.selectedModelInfo());
    }

    public AispectTouchModelInfo recommendedModelInfo() {
        return AispectTouchModelInfo.fromInternal(controller.recommendedModelInfo());
    }

    public String remoteModelDeviceId() {
        return remoteModelDeviceId;
    }

    public boolean isSelectedModelSdkRecommended() {
        return controller.isSelectedModelSdkRecommended();
    }

    public void setSelectedModelId(String modelId) {
        if (!closed) {
            controller.setSelectedModelId(modelId);
        }
    }

    public AispectTouchModelUpdateResult refreshRemoteModel() {
        String canonicalUrl = AispectCanonicalModelRequest.build(
                remoteModelBaseUrl,
                remoteModelAppId,
                remoteModelDeviceId,
                SDK_VERSION,
                remoteModelAppVersion,
                selectedModelInfo() == null ? "" : selectedModelInfo().id,
                selectedModelInfo() == null ? "" : selectedModelInfo().version,
                allowInsecureRemoteModelTransport
        );
        return canonicalUrl.isEmpty() ? failedResult("canonical_configuration_invalid") : refreshCanonicalRemoteModel(canonicalUrl);
    }

    private AispectTouchModelUpdateResult refreshCanonicalRemoteModel(String assignmentUrl) {
        if (closed) {
            return failedResult("classifier_closed");
        }
        try {
            AispectTouchModelUpdateResult result = publicUpdateResult(controller.refreshCanonicalRemoteModel(assignmentUrl, SDK_VERSION));
            if (result.status == AispectTouchModelUpdateStatus.DEFERRED) {
                pendingUpdateReport = result;
                return result;
            }
            if (result.status == AispectTouchModelUpdateStatus.ACTIVATED
                    || result.status == AispectTouchModelUpdateStatus.REJECTED
                    || result.status == AispectTouchModelUpdateStatus.FAILED) {
                AispectModelUpdateReporter.report(
                        remoteModelBaseUrl,
                        remoteModelAppId,
                        remoteModelDeviceId,
                        SDK_VERSION,
                        remoteModelAppVersion,
                        result.activeModelId,
                        result.requestedModelId,
                        result.requestedModelVersion,
                        result.status == AispectTouchModelUpdateStatus.ACTIVATED,
                        result.reason
                );
            }
            return result;
        } catch (Exception error) {
            AispectTouchModelUpdateResult result = failedResult(error.getClass().getSimpleName());
            AispectModelUpdateReporter.report(
                    remoteModelBaseUrl,
                    remoteModelAppId,
                    remoteModelDeviceId,
                    SDK_VERSION,
                    remoteModelAppVersion,
                    result.activeModelId,
                    result.requestedModelId,
                    result.requestedModelVersion,
                    false,
                    result.reason
            );
            return result;
        }
    }

    public void refreshRemoteModelAsync(AispectTouchModelUpdateListener updateListener) {
        refreshCanonicalRemoteModelAsync(updateListener);
    }

    private void refreshCanonicalRemoteModelAsync(AispectTouchModelUpdateListener updateListener) {
        try {
            modelUpdateExecutor.execute(() -> {
                AispectTouchModelUpdateResult result = refreshRemoteModel();
                callbackHandler.post(() -> {
                    if (!closed && updateListener != null) {
                        updateListener.onModelUpdateResult(result);
                    }
                });
            });
        } catch (RejectedExecutionException error) {
            callbackHandler.post(() -> {
                if (!closed && updateListener != null) {
                    updateListener.onModelUpdateError(new AispectTouchError(AispectTouchErrorCode.REMOTE_MODEL_REJECTED, error));
                }
            });
        }
    }


    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            running = false;
            modelPreloadGate.close();
            modelUpdateExecutor.shutdownNow();
            callbackHandler.removeCallbacksAndMessages(null);
            controller.cancelPendingRemoteModel();
            controller.stopRecognition();
            lastEmittedPredictionSequence = 0;
            controller.setListener(null);
            listener = null;
        }
    }

    @Override
    public void onStatusChanged(AispectCollectionController.Status status) {
        if (pendingUpdateReport != null && ("collector_model_activated".equals(status.lastEventKey)
                || "collector_model_activation_failed".equals(status.lastEventKey))) {
            AispectTouchModelUpdateResult pending = pendingUpdateReport;
            pendingUpdateReport = null;
            boolean success = "collector_model_activated".equals(status.lastEventKey);
            AispectModelUpdateReporter.report(
                    remoteModelBaseUrl, remoteModelAppId, remoteModelDeviceId, SDK_VERSION,
                    remoteModelAppVersion, pending.activeModelId, pending.requestedModelId,
                    pending.requestedModelVersion, success,
                    success ? "" : "activation_failed"
            );
        }
        if (closed || listener == null || status == null || !isCompletedPredictionEvent(status.lastEventKey)) {
            return;
        }
        if (status.latestPredictionSequence == 0 || status.latestPredictionSequence == lastEmittedPredictionSequence) {
            return;
        }
        lastEmittedPredictionSequence = status.latestPredictionSequence;
        if (status.latestTouchEvent == null) {
            listener.onTouchError(new AispectTouchError(
                    AispectTouchErrorCode.TOUCH_SEQUENCE_INVALID,
                    new IllegalStateException("touch event missing")
            ));
            return;
        }
        if (!status.signalSensorAvailable) {
            listener.onTouchError(new AispectTouchError(
                    AispectTouchErrorCode.SENSOR_UNAVAILABLE,
                    new IllegalStateException("motion sensor unavailable")
            ));
            return;
        }
        if (status.latestPrediction == null) {
            AispectTouchErrorCode code = status.selectedModelInfo == null
                    ? AispectTouchErrorCode.MODEL_UNAVAILABLE
                    : AispectTouchErrorCode.INFERENCE_FAILED;
            listener.onTouchError(new AispectTouchError(code, new IllegalStateException("prediction unavailable")));
            return;
        }
        listener.onTouchResult(AispectTouchResultMapper.fromPrediction(status.latestPrediction, status.latestTouchEvent));
    }

    @Override
    public void onRecordSaved(AispectDataLogger.SavedRecord record, AispectCollectionController.Status status) {
        onStatusChanged(status);
    }

    @Override
    public void onRecordFailed(Exception error, AispectCollectionController.Status status) {
        if (!closed && listener != null) {
            listener.onTouchError(new AispectTouchError(AispectTouchErrorCode.INFERENCE_FAILED, error));
        }
    }

    private AispectTouchModelUpdateResult failedResult(String reason) {
        AispectTouchModelInfo active = selectedModelInfo();
        return new AispectTouchModelUpdateResult(
                AispectTouchModelUpdateStatus.FAILED,
                "",
                "",
                active == null ? "" : active.id,
                active == null ? "" : active.version,
                reason
        );
    }

    private static AispectTouchModelUpdateResult publicUpdateResult(AispectRemoteModelUpdater.Result result) {
        AispectTouchModelUpdateStatus status;
        switch (result.state) {
            case NO_CHANGE:
                status = AispectTouchModelUpdateStatus.NO_CHANGE;
                break;
            case ACTIVATED:
                status = AispectTouchModelUpdateStatus.ACTIVATED;
                break;
            case DEFERRED:
            case STAGED:
                status = AispectTouchModelUpdateStatus.DEFERRED;
                break;
            case REJECTED:
            default:
                status = AispectTouchModelUpdateStatus.REJECTED;
                break;
        }
        return new AispectTouchModelUpdateResult(
                status,
                result.modelId,
                result.version,
                result.activeModelId,
                result.activeModelVersion,
                result.reason
        );
    }

    static boolean isCompletedPredictionEvent(String key) {
        return "collector_causal_prediction_ready".equals(key)
                || "collector_diagnostic_ready".equals(key)
                || "collector_event_saved".equals(key);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String[] resolveAllowedHosts(String[] configuredHosts, String baseUrl) {
        if (configuredHosts != null && configuredHosts.length > 0) {
            return configuredHosts.clone();
        }
        try {
            String host = new URL(baseUrl).getHost();
            return host == null || host.isEmpty() ? new String[0] : new String[]{host};
        } catch (Exception error) {
            return new String[0];
        }
    }
}
