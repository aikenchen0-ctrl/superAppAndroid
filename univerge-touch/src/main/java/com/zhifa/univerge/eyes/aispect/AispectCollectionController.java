package com.zhifa.univerge.eyes.aispect;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AispectCollectionController {
    private static final long RELEASE_POST_ROLL_SAFETY_MARGIN_MS = 8L;
    public interface Listener {
        void onStatusChanged(Status status);

        void onRecordSaved(AispectDataLogger.SavedRecord record, Status status);

        void onRecordFailed(Exception error, Status status);
    }

    public static final class Config {
        public long postTouchCaptureDelayMs = 0L;
        public boolean enableCnnDiagnostics = true;
        public double minimumSensorSampleRateHz = 120.0;
        public double maximumSensorSampleRateHz = 320.0;
        public int minimumSensorWindowFrameCount = 15;
        public float maximumTouchTravelPx = 36f;
    }

    public static final class Status {
        public final boolean enabled;
        public final AispectModels.DatasetLabel selectedLabel;
        public final int acceptedCount;
        public final int rejectedCount;
        public final String lastEventKey;
        public final String lastEventDetail;
        public final String datasetDirectoryPath;
        public final AispectDeviceCapabilityProfiler.Snapshot capability;
        public final AispectModels.ImpactPrediction latestPrediction;
        public final AispectImpactCNNClassifier.ModelInfo selectedModelInfo;
        public final boolean isLabelLocked;
        public final double latestSampleRateHz;
        public final double latestImpactMaxAbsDelta;
        public final String latestImpactLog;
        public final AispectModels.TouchEvent latestTouchEvent;
        public final AispectTouchNormalizationProfile.Scheme selectedNormalizationScheme;
        public final AispectTouchNormalizationProfile latestNormalizationProfile;
        public final AispectContactEncodingProfile.Scheme selectedContactEncodingScheme;
        public final AispectContactEncodingProfile latestContactEncodingProfile;
        public final int latestPredictionSequence;
        public final boolean selectedModelRecommended;
        public final boolean signalSensorAvailable;

        Status(
                boolean enabled,
                AispectModels.DatasetLabel selectedLabel,
                int acceptedCount,
                int rejectedCount,
                String lastEventKey,
                String lastEventDetail,
                String datasetDirectoryPath,
                AispectDeviceCapabilityProfiler.Snapshot capability,
                AispectModels.ImpactPrediction latestPrediction,
                AispectImpactCNNClassifier.ModelInfo selectedModelInfo,
                boolean isLabelLocked,
                double latestSampleRateHz,
                double latestImpactMaxAbsDelta,
                String latestImpactLog,
                AispectModels.TouchEvent latestTouchEvent,
                AispectTouchNormalizationProfile.Scheme selectedNormalizationScheme,
                AispectTouchNormalizationProfile latestNormalizationProfile,
                AispectContactEncodingProfile.Scheme selectedContactEncodingScheme,
                AispectContactEncodingProfile latestContactEncodingProfile,
                int latestPredictionSequence,
                boolean selectedModelRecommended,
                boolean signalSensorAvailable
        ) {
            this.enabled = enabled;
            this.selectedLabel = selectedLabel;
            this.acceptedCount = acceptedCount;
            this.rejectedCount = rejectedCount;
            this.lastEventKey = lastEventKey;
            this.lastEventDetail = lastEventDetail;
            this.datasetDirectoryPath = datasetDirectoryPath;
            this.capability = capability;
            this.latestPrediction = latestPrediction;
            this.selectedModelInfo = selectedModelInfo;
            this.isLabelLocked = isLabelLocked;
            this.latestSampleRateHz = latestSampleRateHz;
            this.latestImpactMaxAbsDelta = latestImpactMaxAbsDelta;
            this.latestImpactLog = latestImpactLog;
            this.latestTouchEvent = latestTouchEvent;
            this.selectedNormalizationScheme = selectedNormalizationScheme;
            this.latestNormalizationProfile = latestNormalizationProfile;
            this.selectedContactEncodingScheme = selectedContactEncodingScheme;
            this.latestContactEncodingProfile = latestContactEncodingProfile;
            this.latestPredictionSequence = latestPredictionSequence;
            this.selectedModelRecommended = selectedModelRecommended;
            this.signalSensorAvailable = signalSensorAvailable;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Config config = new Config();
    private final AispectInteractionEngine interactionEngine;
    private final AispectImpactSignalCollector signalCollector;
    private final AispectSignalWindowBuilder windowBuilder = new AispectSignalWindowBuilder();
    private final AispectDataLogger dataLogger;
    private final AispectImpactCNNClassifier classifier;
    private final AispectDeviceCapabilityProfiler capabilityProfiler = new AispectDeviceCapabilityProfiler();
    private final AispectTouchNormalizationEngine normalizationEngine = new AispectTouchNormalizationEngine();
    private final Object modelActivationLock = new Object();
    private final AispectRemoteModelUpdateGate remoteModelUpdateGate = new AispectRemoteModelUpdateGate();
    private final AispectPendingModelActivation pendingModelActivation = new AispectPendingModelActivation();
    private final ArrayList<AispectTouchFrame> activeTouchFrames = new ArrayList<>();
    private final ArrayList<AispectModels.TouchEvent> activeEvents = new ArrayList<>();
    private AispectModels.DatasetLabel selectedLabel = new AispectModels.DatasetLabel(AispectModels.MotionState.HANDHELD_STATIC, AispectModels.TouchStrength.LIGHT);
    private AispectModels.DatasetLabel activeLabel = selectedLabel;
    private Listener listener;
    private boolean enabled;
    private boolean recognitionRunning;
    private boolean activeTouch;
    private boolean activeSaveScheduled;
    private boolean activeShouldSaveSample = true;
    private boolean manualModelSelection;
    private boolean requireHttpsForRemoteModels = true;
    private String[] remoteModelAllowedHosts = new String[0];
    private String selectedModelId;
    private String activeModelId;
    private AispectTouchNormalizationProfile.Scheme activeNormalizationScheme = AispectTouchNormalizationProfile.Scheme.COVARIANCE_MATRIX;
    private AispectContactEncodingProfile.Scheme selectedContactEncodingScheme = AispectContactEncodingProfile.Scheme.GAUSSIAN_MAP;
    private AispectContactEncodingProfile.Scheme activeContactEncodingScheme = AispectContactEncodingProfile.Scheme.GAUSSIAN_MAP;
    private int acceptedCount;
    private int rejectedCount;
    private long collectionSessionStartedAtMillis;
    private String collectionSessionId = UUID.randomUUID().toString();
    private long downEventTimeMillis;
    private long downEventElapsedRealtimeNanos;
    private long downReceivedElapsedRealtimeNanos;
    private long liftEventElapsedRealtimeNanos;
    private double downTimestampSeconds;
    private int activeMaximumPointerCount;
    private double activeTouchTravelPx;
    private boolean hasPreviousTouchFrame;
    private float previousTouchX;
    private float previousTouchY;
    private int activeTouchSequence;
    private AispectModels.TouchEvent endingEvent;
    private Runnable completedTouchRunnable;
    private Runnable causalPredictionRunnable;
    private boolean causalPredictionPublished;
    private AispectModels.ImpactPrediction latestPrediction;
    private double latestSampleRateHz;
    private double latestImpactMaxAbsDelta;
    private String latestImpactLog;
    private AispectModels.TouchEvent latestTouchEvent;
    private AispectTouchNormalizationProfile latestNormalizationProfile;
    private AispectContactEncodingProfile latestContactEncodingProfile;
    private int latestPredictionSequence;
    private volatile long remoteModelActivationSequence;
    private String lastRemoteActivatedModelId;
    private String lastRemoteActivatedVersion;
    private String lastEventKey = "collector_idle";
    private String lastEventDetail;

    public AispectCollectionController(Context context) {
        dataLogger = new AispectDataLogger(context);
        classifier = new AispectImpactCNNClassifier(context);
        AispectImpactCNNClassifier.ModelInfo defaultModel = classifier.defaultModelInfo();
        AispectImpactCNNClassifier.ModelInfo activeDownloadedModel = classifier.activeDownloadedModelInfo();
        String activeDownloadedModelId = activeDownloadedModel == null ? null : activeDownloadedModel.id;
        String initialModelId = AispectTouchModelSelector.initialModelId(
                activeDownloadedModelId,
                capabilityProfiler.snapshot(),
                classifier.availableModels()
        );
        if (initialModelId == null && defaultModel != null) {
            initialModelId = defaultModel.id;
        }
        if (initialModelId != null) {
            selectedModelId = initialModelId;
            activeModelId = initialModelId;
            manualModelSelection = initialModelId.equals(activeDownloadedModelId);
        }
        signalCollector = new AispectImpactSignalCollector(context);
        interactionEngine = new AispectInteractionEngine(event -> {
            activeEvents.add(event);
            handleAispectEvent(event);
        });
    }

    public Config config() {
        return config;
    }

    public AispectDataLogger dataLogger() {
        return dataLogger;
    }

    public AispectInteractionEngine interactionEngine() {
        return interactionEngine;
    }

    public List<AispectImpactCNNClassifier.ModelInfo> availableModels() {
        return classifier.availableModels();
    }

    public void reloadModelCatalog() {
        if (!activeTouch) {
            classifier.reloadModelCatalog();
        }
    }

    public AispectImpactCNNClassifier.ModelInfo selectedModelInfo() {
        return classifier.modelInfoForId(selectedModelId);
    }

    public AispectImpactCNNClassifier.ModelInfo recommendedModelInfo() {
        return classifier.modelInfoForId(recommendedModelId(capabilityProfiler.snapshot()));
    }

    public void preloadSelectedModel() {
        String modelId = selectedModelId;
        if (modelId != null) {
            classifier.preloadModel(modelId);
        }
    }

    public AispectRemoteModelUpdater.Result refreshRemoteModel(
            String assignmentUrl,
            String deviceId
    ) throws IOException, JSONException {
        return remoteModelUpdateGate.runSerialized(
                () -> refreshRemoteModelSerialized(assignmentUrl, deviceId)
        );
    }

    public AispectRemoteModelUpdater.Result refreshCanonicalRemoteModel(
            String assignmentUrl,
            String sdkVersion
    ) throws IOException, JSONException {
        return remoteModelUpdateGate.runSerialized(
                () -> refreshCanonicalRemoteModelSerialized(assignmentUrl, sdkVersion)
        );
    }

    private AispectRemoteModelUpdater.Result refreshRemoteModelSerialized(
            String assignmentUrl,
            String deviceId
    ) throws IOException, JSONException {
        if (remoteModelUpdateGate.isCancelled()) {
            return cancelledModelUpdate("", "");
        }
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(classifier, requireHttpsForRemoteModels, remoteModelAllowedHosts);
        AispectRemoteModelUpdater.Result prepared = updater.prepare(assignmentUrl, deviceId);
        if (prepared.state != AispectRemoteModelUpdater.State.STAGED) {
            return prepared;
        }
        if (remoteModelUpdateGate.isCancelled()) {
            removeStagedQuietly(prepared.modelId, prepared.version);
            return cancelledModelUpdate(prepared.modelId, prepared.version);
        }

        AispectPendingModelActivation.Model superseded = null;
        AispectRemoteModelUpdater.Result result;
        synchronized (modelActivationLock) {
            if (remoteModelUpdateGate.isCancelled()) {
                result = cancelledModelUpdate(prepared.modelId, prepared.version);
            } else if (activeTouch) {
                superseded = pendingModelActivation.replace(prepared.modelId, prepared.version);
                result = prepared.deferred();
            } else {
                result = updater.activate(prepared.modelId, prepared.version);
                if (remoteModelUpdateGate.isCancelled()
                        && result.state == AispectRemoteModelUpdater.State.ACTIVATED) {
                    rollbackRemoteActivationLocked(prepared.modelId, prepared.version);
                    result = cancelledModelUpdate(prepared.modelId, prepared.version);
                } else {
                    applyActivatedModel(result);
                    if (result.state == AispectRemoteModelUpdater.State.ACTIVATED) {
                        recordRemoteActivationLocked(prepared.modelId, prepared.version);
                    }
                }
            }
        }
        if (result.reason.equals("update_cancelled")) {
            removeStagedQuietly(prepared.modelId, prepared.version);
        }
        if (superseded != null && !sameModel(superseded, prepared.modelId, prepared.version)) {
            classifier.downloadedModelStore().removeStaged(superseded.modelId, superseded.version);
        }
        if (result.state == AispectRemoteModelUpdater.State.ACTIVATED) {
            handler.post(this::emitStatus);
        }
        return result;
    }

    private AispectRemoteModelUpdater.Result refreshCanonicalRemoteModelSerialized(
            String assignmentUrl,
            String sdkVersion
    ) throws IOException, JSONException {
        if (remoteModelUpdateGate.isCancelled()) {
            return cancelledModelUpdate("", "");
        }
        AispectRemoteModelUpdater updater = new AispectRemoteModelUpdater(classifier, requireHttpsForRemoteModels, remoteModelAllowedHosts);
        AispectRemoteModelUpdater.Result prepared = updater.prepareCanonical(assignmentUrl, sdkVersion);
        if (prepared.state != AispectRemoteModelUpdater.State.STAGED) {
            return prepared;
        }
        if (remoteModelUpdateGate.isCancelled()) {
            removeStagedQuietly(prepared.modelId, prepared.version);
            return cancelledModelUpdate(prepared.modelId, prepared.version);
        }
        AispectPendingModelActivation.Model superseded = null;
        AispectRemoteModelUpdater.Result result;
        synchronized (modelActivationLock) {
            if (remoteModelUpdateGate.isCancelled()) {
                result = cancelledModelUpdate(prepared.modelId, prepared.version);
            } else if (activeTouch) {
                superseded = pendingModelActivation.replace(prepared.modelId, prepared.version);
                result = prepared.deferred();
            } else {
                result = updater.activate(prepared.modelId, prepared.version);
                if (remoteModelUpdateGate.isCancelled()
                        && result.state == AispectRemoteModelUpdater.State.ACTIVATED) {
                    rollbackRemoteActivationLocked(prepared.modelId, prepared.version);
                    result = cancelledModelUpdate(prepared.modelId, prepared.version);
                } else {
                    applyActivatedModel(result);
                    if (result.state == AispectRemoteModelUpdater.State.ACTIVATED) {
                        recordRemoteActivationLocked(prepared.modelId, prepared.version);
                    }
                }
            }
        }
        if (result.reason.equals("update_cancelled")) {
            removeStagedQuietly(prepared.modelId, prepared.version);
        }
        if (superseded != null && !superseded.matches(prepared.modelId, prepared.version)) {
            removeStagedQuietly(superseded.modelId, superseded.version);
        }
        if (result.state == AispectRemoteModelUpdater.State.ACTIVATED) {
            handler.post(this::emitStatus);
        }
        return result;
    }

    public void setAllowInsecureRemoteModelTransport(boolean allowInsecureTransport) {
        requireHttpsForRemoteModels = !allowInsecureTransport;
    }

    public void setRemoteModelAllowedHosts(String[] allowedHosts) {
        remoteModelAllowedHosts = allowedHosts == null ? new String[0] : allowedHosts.clone();
    }

    public void cancelPendingRemoteModel() {
        long activationSequenceBeforeCancel = remoteModelActivationSequence;
        remoteModelUpdateGate.cancel();
        AispectPendingModelActivation.Model pending;
        synchronized (modelActivationLock) {
            pending = pendingModelActivation.takeIfIdle(false);
            if (remoteModelActivationSequence > activationSequenceBeforeCancel
                    && lastRemoteActivatedModelId != null
                    && lastRemoteActivatedVersion != null) {
                rollbackRemoteActivationLocked(
                        lastRemoteActivatedModelId,
                        lastRemoteActivatedVersion
                );
            }
        }
        if (pending != null) {
            removeStagedQuietly(pending.modelId, pending.version);
        }
    }

    public boolean isSelectedModelSdkRecommended() {
        return isSelectedModelSdkRecommended(capabilityProfiler.snapshot());
    }

    public AispectTouchNormalizationProfile.Scheme selectedNormalizationScheme() {
        return normalizationEngine.selectedScheme();
    }

    public AispectContactEncodingProfile.Scheme selectedContactEncodingScheme() {
        return selectedContactEncodingScheme;
    }

    public void setSelectedContactEncodingScheme(AispectContactEncodingProfile.Scheme scheme) {
        if (enabled || activeTouch || scheme == null) {
            return;
        }
        selectedContactEncodingScheme = scheme;
        clearLatestPredictionSnapshot();
        lastEventKey = "collector_task_updated";
        lastEventDetail = scheme.key();
        emitStatus();
    }

    public void setSelectedNormalizationScheme(AispectTouchNormalizationProfile.Scheme scheme) {
        if (enabled || activeTouch || scheme == null) {
            return;
        }
        normalizationEngine.setSelectedScheme(scheme);
        clearLatestPredictionSnapshot();
        lastEventKey = "collector_task_updated";
        lastEventDetail = scheme.key();
        emitStatus();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSelectedLabel(AispectModels.DatasetLabel label) {
        if (enabled || activeTouch) {
            return;
        }
        selectedLabel = label;
        clearLatestPredictionSnapshot();
        lastEventKey = "collector_task_updated";
        lastEventDetail = label.datasetKey();
        emitStatus();
    }

    public void setSelectedModelId(String modelId) {
        if (activeTouch || modelId == null) {
            return;
        }
        AispectImpactCNNClassifier.ModelInfo info = classifier.modelInfoForId(modelId);
        if (info == null) {
            return;
        }
        selectedModelId = info.id;
        manualModelSelection = true;
        clearLatestPredictionSnapshot();
        lastEventKey = "collector_task_updated";
        lastEventDetail = info.id;
        emitStatus();
    }

    public AispectModels.DatasetLabel selectedLabel() {
        return selectedLabel;
    }

    public void startRecognition() {
        if (enabled || recognitionRunning) {
            return;
        }
        recognitionRunning = true;
        clearLatestPredictionSnapshot();
        signalCollector.start();
        lastEventKey = "collector_press_recognition_ready";
        lastEventDetail = null;
        emitStatus();
    }

    public void stopRecognition() {
        if (!recognitionRunning) {
            return;
        }
        recognitionRunning = false;
        interactionEngine.cancelTracking();
        synchronized (modelActivationLock) {
            activeTouch = false;
        }
        activeSaveScheduled = false;
        activeShouldSaveSample = true;
        activeTouchFrames.clear();
        activeEvents.clear();
        endingEvent = null;
        cancelCompletedTouchRunnable();
        cancelCausalPredictionRunnable();
        hasPreviousTouchFrame = false;
        signalCollector.stop();
        clearLatestPredictionSnapshot();
        lastEventKey = "collector_press_recognition_stopped";
        lastEventDetail = null;
        finishTouchAndActivatePendingModel();
        emitStatus();
    }

    public boolean isRecognitionRunning() {
        return recognitionRunning;
    }

    public void setEnabled(boolean enabled) {
        if (enabled && recognitionRunning) {
            stopRecognition();
        }
        this.enabled = enabled;
        clearLatestPredictionSnapshot();
        if (enabled) {
            acceptedCount = 0;
            rejectedCount = 0;
            collectionSessionStartedAtMillis = System.currentTimeMillis();
            collectionSessionId = UUID.randomUUID().toString();
            normalizationEngine.resetBaseline();
            signalCollector.start();
            lastEventKey = "collector_session_ready";
            lastEventDetail = selectedLabel.datasetKey();
        } else {
            synchronized (modelActivationLock) {
                activeTouch = false;
                activatePendingModelLocked();
            }
            activeSaveScheduled = false;
            activeTouchFrames.clear();
            activeEvents.clear();
            endingEvent = null;
            cancelCompletedTouchRunnable();
            cancelCausalPredictionRunnable();
            if (!recognitionRunning) {
                signalCollector.stop();
            }
            lastEventKey = "collector_session_stopped";
            lastEventDetail = null;
        }
        emitStatus();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTouchActive() {
        synchronized (modelActivationLock) {
            return activeTouch;
        }
    }

    public boolean handleMotionEvent(MotionEvent event, int width, int height) {
        if (!enabled || event == null) {
            return false;
        }
        return handleInputMotionEvent(event, width, height);
    }

    public boolean handleDiagnosticMotionEvent(MotionEvent event, int width, int height) {
        if (enabled || event == null) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !signalCollector.isRunning()) {
            signalCollector.start();
        }
        return handleInputMotionEvent(event, width, height);
    }

    public boolean handleRecognitionMotionEvent(MotionEvent event, int width, int height) {
        if (enabled || !recognitionRunning || event == null) {
            return false;
        }
        return handleInputMotionEvent(event, width, height);
    }

    private boolean handleInputMotionEvent(MotionEvent event, int width, int height) {
        AispectInputQuality.Reason eventReason = eventReason(event);
        if (AispectInputQuality.shouldRejectGestureInput(activeShouldSaveSample, eventReason)) {
            rejectActiveTouch(eventReason);
            return true;
        }
        collectTouchFrames(event, width, height);
        AispectInputQuality.Reason gestureReason = AispectInputQuality.gestureReason(
                activeMaximumPointerCount,
                activeTouchTravelPx,
                false,
                config.maximumTouchTravelPx
        );
        if (AispectInputQuality.shouldRejectGestureInput(activeShouldSaveSample, gestureReason)) {
            rejectActiveTouch(gestureReason);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            scheduleManualCompletion(new AispectModels.TouchEvent(
                    AispectModels.TouchKind.CANCEL,
                    event.getX(),
                    event.getY(),
                    event.getEventTime() / 1000.0,
                    null
            ));
            return true;
        }
        return interactionEngine.handleMotionEvent(event);
    }

    private static AispectInputQuality.Reason eventReason(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            return AispectInputQuality.Reason.MULTI_POINTER;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            return AispectInputQuality.Reason.DRAG_OR_CANCEL;
        }
        return AispectInputQuality.Reason.NONE;
    }

    public Status status() {
        return makeStatus();
    }

    private void collectTouchFrames(MotionEvent event, int width, int height) {
        int action = event.getActionMasked();
        int pointerIndex = Math.max(0, event.getActionIndex());
        long receivedUptimeMillis = SystemClock.uptimeMillis();
        long receivedElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        if (action == MotionEvent.ACTION_DOWN) {
            synchronized (modelActivationLock) {
                activeTouch = true;
                activeModelId = selectedModelId;
            }
            activeSaveScheduled = false;
            activeShouldSaveSample = enabled;
            activeTouchSequence += 1;
            activeLabel = selectedLabel;
            activeTouchFrames.clear();
            activeEvents.clear();
            endingEvent = null;
            clearLatestPredictionSnapshot();
            downEventTimeMillis = event.getDownTime();
            downEventElapsedRealtimeNanos = AispectCaptureClock.uptimeMillisToElapsedRealtimeNanos(
                    downEventTimeMillis,
                    receivedUptimeMillis,
                    receivedElapsedRealtimeNanos
            );
            downReceivedElapsedRealtimeNanos = receivedElapsedRealtimeNanos;
            liftEventElapsedRealtimeNanos = 0L;
            downTimestampSeconds = downEventTimeMillis / 1000.0;
            causalPredictionPublished = false;
            cancelCausalPredictionRunnable();
            activeNormalizationScheme = normalizationEngine.selectedScheme();
            activeContactEncodingScheme = selectedContactEncodingScheme;
            activeMaximumPointerCount = event.getPointerCount();
            activeTouchTravelPx = 0;
            hasPreviousTouchFrame = false;
            lastEventKey = activeShouldSaveSample
                    ? "collector_waiting_for_touch_end"
                    : recognitionRunning ? "collector_recognition_waiting" : "collector_diagnostic_waiting";
            lastEventDetail = activeLabel.datasetKey();
            signalCollector.updateTouchContext(unit(event.getX(), width), unit(event.getY(), height), downTimestampSeconds);
            emitStatus();
        }
        if (!activeTouch) {
            return;
        }
        int safePointerIndex = Math.min(pointerIndex, event.getPointerCount() - 1);
        for (int i = 0; i < event.getHistorySize(); i++) {
            AispectTouchFrame frame = AispectTouchFrame.fromHistorical(
                    event,
                    safePointerIndex,
                    i,
                    downEventTimeMillis,
                    width,
                    height,
                    downEventElapsedRealtimeNanos,
                    receivedUptimeMillis,
                    receivedElapsedRealtimeNanos
            );
            appendTouchFrame(frame);
        }
        AispectTouchFrame current = AispectTouchFrame.fromCurrent(
                event,
                safePointerIndex,
                downEventTimeMillis,
                width,
                height,
                downEventElapsedRealtimeNanos,
                receivedUptimeMillis,
                receivedElapsedRealtimeNanos
        );
        appendTouchFrame(current);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            liftEventElapsedRealtimeNanos = current.eventElapsedRealtimeNanos;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            scheduleCausalPrediction(activeTouchSequence);
        }
    }

    private void appendTouchFrame(AispectTouchFrame frame) {
        activeTouchFrames.add(frame);
        capabilityProfiler.observe(frame);
        activeMaximumPointerCount = Math.max(activeMaximumPointerCount, frame.pointerCount);
        if (hasPreviousTouchFrame) {
            double dx = frame.x - previousTouchX;
            double dy = frame.y - previousTouchY;
            activeTouchTravelPx += Math.sqrt(dx * dx + dy * dy);
        }
        previousTouchX = frame.x;
        previousTouchY = frame.y;
        hasPreviousTouchFrame = true;
        signalCollector.updateTouchContext(frame.xNorm, frame.yNorm, downTimestampSeconds);
    }

    private void scheduleCausalPrediction(final int sequence) {
        AispectImpactCNNClassifier.ModelInfo info = classifier.modelInfoForId(activeModelId);
        if (info == null || !AispectCausalPressFeatureBuilder.isTimeGridFeatureContract(info.featureContract)) {
            return;
        }
        cancelCausalPredictionRunnable();
        causalPredictionRunnable = new Runnable() {
            @Override
            public void run() {
                if (!activeTouch || activeTouchSequence != sequence || causalPredictionPublished) {
                    return;
                }
                AispectImpactSignalCollector.Snapshot snapshot = signalCollector.snapshot();
                AispectModels.ImpactFrame[] frames = snapshot.frames.toArray(
                        new AispectModels.ImpactFrame[0]
                );
                if (!canPublishCausalTimeGridPrediction(
                        frames,
                        downEventElapsedRealtimeNanos,
                        liftEventElapsedRealtimeNanos
                )) {
                    if (liftEventElapsedRealtimeNanos > 0L
                            && liftEventElapsedRealtimeNanos < downEventElapsedRealtimeNanos + 25_000_000L) {
                        lastEventKey = "collector_causal_window_rejected";
                        lastEventDetail = "lift_before_25ms";
                        causalPredictionRunnable = null;
                        emitStatus();
                        return;
                    }
                    handler.postDelayed(this, 4L);
                    return;
                }
                AispectSignalWindowBuilder.Window window = new AispectSignalWindowBuilder.Window(
                        frames,
                        0,
                        downEventElapsedRealtimeNanos / 1_000_000_000.0,
                        snapshot.sampleRateHz,
                        0.0,
                        frames.length
                );
                latestPrediction = classifier.predict(window, activeModelId, new ArrayList<>(activeTouchFrames));
                AispectTouchFrame latestFrame = activeTouchFrames.isEmpty()
                        ? null
                        : activeTouchFrames.get(activeTouchFrames.size() - 1);
                latestTouchEvent = new AispectModels.TouchEvent(
                        AispectModels.TouchKind.PRESS,
                        latestFrame == null ? 0.0f : latestFrame.x,
                        latestFrame == null ? 0.0f : latestFrame.y,
                        latestFrame == null
                                ? downTimestampSeconds
                                : latestFrame.eventTimeMillis / 1000.0,
                        null
                );
                causalPredictionPublished = true;
                latestPredictionSequence = sequence;
                latestSampleRateHz = snapshot.sampleRateHz;
                latestImpactMaxAbsDelta = 0.0;
                latestImpactLog = "causal time grid -15ms to +25ms";
                lastEventKey = latestPrediction == null
                        ? "collector_causal_model_unavailable"
                        : "collector_causal_prediction_ready";
                lastEventDetail = activeModelId;
                causalPredictionRunnable = null;
                emitStatus();
            }
        };
        handler.post(causalPredictionRunnable);
    }

    static boolean canPublishCausalTimeGridPrediction(
            AispectModels.ImpactFrame[] frames,
            long downEventElapsedRealtimeNanos,
            long liftEventElapsedRealtimeNanos
    ) {
        return downEventElapsedRealtimeNanos > 0L
                && (liftEventElapsedRealtimeNanos <= 0L
                || liftEventElapsedRealtimeNanos >= downEventElapsedRealtimeNanos + 25_000_000L)
                && AispectCausalPressFeatureBuilder.hasTimeGridSupport(
                frames,
                downEventElapsedRealtimeNanos
        );
    }

    private void cancelCausalPredictionRunnable() {
        if (causalPredictionRunnable != null) {
            handler.removeCallbacks(causalPredictionRunnable);
            causalPredictionRunnable = null;
        }
    }

    private void scheduleCompletedTouch(int sequence) {
        cancelCompletedTouchRunnable();
        completedTouchRunnable = () -> {
            completedTouchRunnable = null;
            finalizeCompletedTouch(sequence);
        };
        handler.postDelayed(completedTouchRunnable, completedTouchDelayMs());
    }

    static long releaseCompletionDelayMs(double sampleRateHz, int postFrames, long safetyMarginMs) {
        double safeSampleRateHz = Double.isFinite(sampleRateHz) && sampleRateHz > 0.0
                ? sampleRateHz
                : 120.0;
        long safeMarginMs = Math.max(0L, safetyMarginMs);
        double frameDelayMs = Math.ceil(Math.max(0, postFrames) * 1000.0 / safeSampleRateHz);
        if (!Double.isFinite(frameDelayMs) || frameDelayMs >= Long.MAX_VALUE - safeMarginMs) {
            return Long.MAX_VALUE;
        }
        return (long) frameDelayMs + safeMarginMs;
    }

    private long completedTouchDelayMs() {
        if (config.postTouchCaptureDelayMs > 0L) {
            return config.postTouchCaptureDelayMs;
        }
        return releaseCompletionDelayMs(
                signalCollector.snapshot().sampleRateHz,
                windowBuilder.config().postFrames,
                RELEASE_POST_ROLL_SAFETY_MARGIN_MS
        );
    }

    private void cancelCompletedTouchRunnable() {
        if (completedTouchRunnable != null) {
            handler.removeCallbacks(completedTouchRunnable);
            completedTouchRunnable = null;
        }
    }

    private void handleAispectEvent(AispectModels.TouchEvent event) {
        latestTouchEvent = event;
        switch (event.kind) {
            case DRAG_START:
                if (activeShouldSaveSample) {
                    return;
                }
                rejectActiveTouch(AispectInputQuality.Reason.DRAG_OR_CANCEL);
                return;
            case DRAG_END:
            case CANCEL:
                if (activeShouldSaveSample) {
                    scheduleManualCompletion(event);
                    return;
                }
                rejectActiveTouch(AispectInputQuality.Reason.DRAG_OR_CANCEL);
                return;
            case LIGHT_TAP:
            case HEAVY_TAP:
                scheduleManualCompletion(event);
                break;
            default:
                break;
        }
    }

    private static AispectModels.TouchKind touchKindForLabel(AispectModels.DatasetLabel label) {
        if (label != null && label.touchStrength == AispectModels.TouchStrength.HEAVY) {
            return AispectModels.TouchKind.HEAVY_TAP;
        }
        return AispectModels.TouchKind.LIGHT_TAP;
    }

    private void finalizeCompletedTouch(int sequence) {
        if (!activeTouch || endingEvent == null || activeTouchSequence != sequence) {
            return;
        }
        AispectImpactSignalCollector.Snapshot signalSnapshot = signalCollector.snapshot();
        AispectSignalWindowBuilder.Window impactWindow = windowBuilder.build(signalSnapshot.frames, endingEvent.timestampSeconds, signalSnapshot.sampleRateHz);
        AispectInputQuality.Reason signalReason = AispectInputQuality.signalReason(
                signalSnapshot.hasReliableLinearAcceleration,
                impactWindow.sampleRateHz,
                impactWindow.availableFrameCount,
                config.minimumSensorSampleRateHz,
                config.maximumSensorSampleRateHz,
                config.minimumSensorWindowFrameCount
        );
        if (AispectInputQuality.shouldRejectReleaseInput(activeShouldSaveSample, signalReason)) {
            rejectActiveTouch(signalReason);
            return;
        }
        AispectDeviceCapabilityProfiler.Snapshot capability = capabilityProfiler.snapshot();
        AispectContactPatch patch = AispectContactPatch.fromFrames(new ArrayList<>(activeTouchFrames));
        latestNormalizationProfile = normalizationEngine.evaluate(new ArrayList<>(activeTouchFrames), patch);
        latestContactEncodingProfile = AispectContactEncodingProfile.evaluate(new ArrayList<>(activeTouchFrames), patch, activeContactEncodingScheme);
        AispectImpactCNNClassifier.ModelInfo activeModel = classifier.modelInfoForId(activeModelId);
        boolean causalTimeGridModel = activeModel != null
                && AispectCausalPressFeatureBuilder.isTimeGridFeatureContract(activeModel.featureContract);
        if (!causalTimeGridModel) {
            String predictionModelId = AispectTouchModelSelector.predictionModelId(
                    activeModelId,
                    manualModelSelection,
                    capability,
                    classifier.availableModels()
            );
            if (predictionModelId != null) {
                activeModelId = predictionModelId;
                if (!manualModelSelection) {
                    selectedModelId = predictionModelId;
                }
            }
        }
        AispectModels.ImpactPrediction prediction = null;
        if (config.enableCnnDiagnostics) {
            if (causalTimeGridModel) {
                prediction = latestPrediction;
            } else {
                prediction = classifier.predict(
                        impactWindow,
                        activeModelId,
                        patch,
                        latestNormalizationProfile,
                        latestContactEncodingProfile
                );
                if (prediction == null && !classifier.isModelLoadable(activeModelId)) {
                    AispectImpactCNNClassifier.ModelInfo rollbackModel = classifier.rollbackDownloadedModel(activeModelId);
                    if (rollbackModel != null) {
                        activeModelId = rollbackModel.id;
                        selectedModelId = rollbackModel.id;
                        prediction = classifier.predict(
                                impactWindow,
                                rollbackModel.id,
                                patch,
                                latestNormalizationProfile,
                                latestContactEncodingProfile
                        );
                    }
                }
                if (prediction == null) {
                    prediction = classifier.predictBest(
                            impactWindow,
                            capability,
                            patch,
                            latestNormalizationProfile,
                            latestContactEncodingProfile
                    );
                }
            }
            if (prediction != null
                    && !causalTimeGridModel
                    && predictsHeavy(prediction)
                    && !prediction.isHeavy(impactWindow, patch, latestNormalizationProfile)
                    && AispectInputQuality.shouldRejectReleaseInput(
                            activeShouldSaveSample,
                            AispectInputQuality.Reason.HEAVY_EVIDENCE_INSUFFICIENT
                    )) {
                rejectActiveTouch(AispectInputQuality.Reason.HEAVY_EVIDENCE_INSUFFICIENT);
                return;
            }
        }
        latestPrediction = prediction;
        latestPredictionSequence = sequence;
        latestSampleRateHz = signalSnapshot.sampleRateHz;
        latestImpactMaxAbsDelta = impactWindow.maxAbsDelta;
        latestImpactLog = impactWindowLog(impactWindow, prediction, latestNormalizationProfile);
        if (!activeShouldSaveSample) {
            lastEventKey = "collector_diagnostic_ready";
            lastEventDetail = activeLabel.datasetKey();
            activeSaveScheduled = false;
            activeTouchFrames.clear();
            activeEvents.clear();
            endingEvent = null;
            if (!recognitionRunning) {
                signalCollector.stop();
            }
            emitStatus();
            finishTouchAndActivatePendingModel();
            return;
        }
        try {
            AispectDataLogger.SavedRecord record = dataLogger.writeSample(
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    collectionSessionId,
                    collectionSessionStartedAtMillis,
                    "accepted",
                    "",
                    activeLabel,
                    endingEvent.kind.key(),
                    endingEvent.liftOffset,
                    new ArrayList<>(activeEvents),
                    new ArrayList<>(activeTouchFrames),
                    patch,
                    latestContactEncodingProfile,
                    impactWindow,
                    capability,
                    latestNormalizationProfile,
                    null,
                    signalSnapshot
            );
            normalizationEngine.observe(latestNormalizationProfile);
            acceptedCount += 1;
            lastEventKey = "collector_event_saved";
            lastEventDetail = activeLabel.datasetKey();
            Status status = makeStatus();
            if (listener != null) {
                listener.onRecordSaved(record, status);
                listener.onStatusChanged(status);
            }
        } catch (IOException | JSONException error) {
            rejectedCount += 1;
            lastEventKey = "collector_event_write_failed";
            lastEventDetail = error.getClass().getSimpleName();
            Status status = makeStatus();
            if (listener != null) {
                listener.onRecordFailed(error, status);
                listener.onStatusChanged(status);
            }
        } finally {
            activeSaveScheduled = false;
            activeShouldSaveSample = true;
            cancelCausalPredictionRunnable();
            activeTouchFrames.clear();
            activeEvents.clear();
            endingEvent = null;
            finishTouchAndActivatePendingModel();
        }
    }

    private void scheduleManualCompletion(AispectModels.TouchEvent event) {
        if (activeSaveScheduled || event == null) {
            return;
        }
        activeSaveScheduled = true;
        endingEvent = new AispectModels.TouchEvent(
                touchKindForLabel(activeLabel),
                event.x,
                event.y,
                event.timestampSeconds,
                event.liftOffset
        );
        if (!activeEvents.isEmpty()) {
            activeEvents.set(activeEvents.size() - 1, endingEvent);
        }
        latestTouchEvent = endingEvent;
        int sequence = activeTouchSequence;
        lastEventKey = "collector_collecting_postroll";
        lastEventDetail = endingEvent.kind.key();
        emitStatus();
        scheduleCompletedTouch(sequence);
    }

    private void finishTouchAndActivatePendingModel() {
        AispectRemoteModelUpdater.Result result;
        synchronized (modelActivationLock) {
            activeTouch = false;
            result = activatePendingModelLocked();
        }
        if (result != null) {
            emitStatus();
        }
    }

    private void rejectActiveTouch(AispectInputQuality.Reason reason) {
        if (reason == null || reason == AispectInputQuality.Reason.NONE) {
            return;
        }
        interactionEngine.cancelTracking();
        synchronized (modelActivationLock) {
            activeTouch = false;
        }
        activeSaveScheduled = false;
        activeShouldSaveSample = true;
        activeTouchFrames.clear();
        activeEvents.clear();
        endingEvent = null;
        cancelCompletedTouchRunnable();
        cancelCausalPredictionRunnable();
        hasPreviousTouchFrame = false;
        if (!recognitionRunning) {
            signalCollector.stop();
        }
        clearLatestPredictionSnapshot();
        lastEventKey = "collector_input_rejected";
        lastEventDetail = reason.key();
        emitStatus();
        finishTouchAndActivatePendingModel();
    }

    private AispectRemoteModelUpdater.Result activatePendingModelLocked() {
        AispectPendingModelActivation.Model pending = pendingModelActivation.takeIfIdle(activeTouch);
        if (pending == null) {
            return null;
        }
        if (remoteModelUpdateGate.isCancelled()) {
            removeStagedQuietly(pending.modelId, pending.version);
            return cancelledModelUpdate(pending.modelId, pending.version);
        }
        try {
            AispectRemoteModelUpdater.Result result = new AispectRemoteModelUpdater(
                    classifier,
                    requireHttpsForRemoteModels
            ).activate(pending.modelId, pending.version);
            if (remoteModelUpdateGate.isCancelled()
                    && result.state == AispectRemoteModelUpdater.State.ACTIVATED) {
                rollbackRemoteActivationLocked(pending.modelId, pending.version);
                return cancelledModelUpdate(pending.modelId, pending.version);
            }
            applyActivatedModel(result);
            if (result.state == AispectRemoteModelUpdater.State.ACTIVATED) {
                recordRemoteActivationLocked(pending.modelId, pending.version);
            }
            return result;
        } catch (IOException | JSONException error) {
            lastEventKey = "collector_model_activation_failed";
            lastEventDetail = error.getClass().getSimpleName();
            return new AispectRemoteModelUpdater.Result(
                    AispectRemoteModelUpdater.State.REJECTED,
                    pending.modelId,
                    pending.version,
                    selectedModelId,
                    selectedModelInfo() == null ? "" : selectedModelInfo().version,
                    "activation_failed"
            );
        }
    }

    private void applyActivatedModel(AispectRemoteModelUpdater.Result result) {
        if (result == null || result.state != AispectRemoteModelUpdater.State.ACTIVATED) {
            return;
        }
        AispectImpactCNNClassifier.ModelInfo info = classifier.modelInfoForId(result.modelId);
        if (info == null) {
            return;
        }
        selectedModelId = info.id;
        manualModelSelection = true;
        clearLatestPredictionSnapshot();
        lastEventKey = "collector_model_activated";
        lastEventDetail = info.id + "@" + info.version;
    }

    private AispectRemoteModelUpdater.Result cancelledModelUpdate(String modelId, String version) {
        AispectImpactCNNClassifier.ModelInfo active = selectedModelInfo();
        return new AispectRemoteModelUpdater.Result(
                AispectRemoteModelUpdater.State.REJECTED,
                modelId,
                version,
                active == null ? "" : active.id,
                active == null ? "" : active.version,
                "update_cancelled"
        );
    }

    private void removeStagedQuietly(String modelId, String version) {
        try {
            classifier.downloadedModelStore().removeStaged(modelId, version);
        } catch (IOException ignored) {
            // 取消阶段只清理未引用版本，不能修改 current 或 previous。
        }
    }

    private void rollbackRemoteActivationLocked(String modelId, String version) {
        try {
            classifier.downloadedModelStore().rollbackActivation(modelId, version);
        } catch (IOException | JSONException error) {
            lastEventKey = "collector_model_rollback_failed";
            lastEventDetail = error.getClass().getSimpleName();
        } finally {
            classifier.reloadModelCatalog();
        }
        JSONObject restored = classifier.downloadedModelStore().readCurrent();
        AispectImpactCNNClassifier.ModelInfo restoredInfo = restored == null
                ? null
                : classifier.modelInfoForId(restored.optString("id", ""));
        if (restoredInfo == null) {
            restoredInfo = classifier.defaultModelInfo();
        }
        selectedModelId = restoredInfo == null ? null : restoredInfo.id;
        activeModelId = selectedModelId;
        lastRemoteActivatedModelId = null;
        lastRemoteActivatedVersion = null;
        clearLatestPredictionSnapshot();
    }

    private void recordRemoteActivationLocked(String modelId, String version) {
        lastRemoteActivatedModelId = modelId;
        lastRemoteActivatedVersion = version;
        remoteModelActivationSequence += 1L;
    }

    private static boolean sameModel(
            AispectPendingModelActivation.Model model,
            String modelId,
            String version
    ) {
        return model != null && model.matches(modelId, version);
    }

    private void emitStatus() {
        if (listener != null) {
            listener.onStatusChanged(makeStatus());
        }
    }

    private void clearLatestPredictionSnapshot() {
        latestPrediction = null;
        latestPredictionSequence = 0;
        latestTouchEvent = null;
        latestImpactMaxAbsDelta = 0;
        latestImpactLog = null;
        latestNormalizationProfile = null;
        latestContactEncodingProfile = null;
    }

    private Status makeStatus() {
        AispectDeviceCapabilityProfiler.Snapshot capability = capabilityProfiler.snapshot();
        AispectImpactCNNClassifier.ModelInfo statusModelInfo = classifier.modelInfoForId(
                activeTouch ? activeModelId : selectedModelId
        );
        return new Status(
                enabled,
                selectedLabel,
                acceptedCount,
                rejectedCount,
                lastEventKey,
                lastEventDetail,
                dataLogger.datasetDirectory().getAbsolutePath(),
                capability,
                latestPrediction,
                statusModelInfo,
                enabled || activeTouch,
                latestSampleRateHz,
                latestImpactMaxAbsDelta,
                latestImpactLog,
                latestTouchEvent,
                normalizationEngine.selectedScheme(),
                latestNormalizationProfile,
                selectedContactEncodingScheme,
                latestContactEncodingProfile,
                latestPredictionSequence,
                isSelectedModelSdkRecommended(capability),
                signalCollector.isAvailable()
        );
    }

    private String recommendedModelId(AispectDeviceCapabilityProfiler.Snapshot capability) {
        return AispectTouchModelSelector.recommendedModelId(capability, classifier.availableModels());
    }

    private boolean isSelectedModelSdkRecommended(AispectDeviceCapabilityProfiler.Snapshot capability) {
        return AispectTouchModelSelector.isRecommendedModel(selectedModelId, capability, classifier.availableModels());
    }

    private static String impactWindowLog(
            AispectSignalWindowBuilder.Window window,
            AispectModels.ImpactPrediction prediction,
            AispectTouchNormalizationProfile normalizationProfile
    ) {
        StringBuilder builder = new StringBuilder();
        if (prediction == null) {
            builder.append(String.format(Locale.US, "impact delta window max %.4f cnn unavailable", window.maxAbsDelta));
        } else {
            builder.append(String.format(
                    Locale.US,
                    "impact delta window max %.4f decision %s %.4f heavy %.4f light %.4f",
                    window.maxAbsDelta,
                    prediction.predictedLabel,
                    prediction.predictedProbability,
                    prediction.heavyProbability,
                    prediction.lightProbability
            ));
        }
        if (normalizationProfile != null) {
            builder.append('\n');
            builder.append(String.format(
                    Locale.US,
                    "touch norm %s major %.5f minor %.5f area %.5f conf %.2f",
                    normalizationProfile.selectedScheme.key(),
                    normalizationProfile.selectedResult.majorValue,
                    normalizationProfile.selectedResult.minorValue,
                    normalizationProfile.selectedResult.areaValue,
                    normalizationProfile.selectedResult.confidence
            ));
        }
        for (int i = 0; i < window.frames.length; i++) {
            AispectModels.ImpactFrame frame = window.frames[i];
            int frameIndex = window.firstFrameIndex + i;
            builder.append('\n');
            builder.append(String.format(
                    Locale.US,
                    "f%+d d%+.4f x%+.4f y%+.4f z%+.4f m%+.4f",
                    frameIndex,
                    frame.delta,
                    frame.x,
                    frame.y,
                    frame.z,
                    Math.abs(frame.delta)
            ));
        }
        return builder.toString();
    }

    private static boolean predictsHeavy(AispectModels.ImpactPrediction prediction) {
        String label = prediction == null ? "" : prediction.predictedLabel;
        return "heavy".equals(label) || label.endsWith("_heavy");
    }

    private static double unit(float value, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        double output = value / denominator;
        if (output < 0) {
            return 0;
        }
        if (output > 1) {
            return 1;
        }
        return output;
    }
}
