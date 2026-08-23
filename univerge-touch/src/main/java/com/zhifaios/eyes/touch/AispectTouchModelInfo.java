package com.zhifaios.eyes.touch;

import com.zhifaios.eyes.aispect.AispectImpactCNNClassifier;

public final class AispectTouchModelInfo {
    public final String id;
    public final String version;
    public final String displayName;
    public final int inputChannels;
    public final int frameCount;
    public final int[] frameIndices;
    public final String[] featureNames;
    public final int classCount;
    public final String[] labelOrder;
    public final String windowMode;
    public final long captureDelayMs;

    public AispectTouchModelInfo(
            String id,
            String version,
            String displayName,
            int inputChannels,
            int frameCount,
            int[] frameIndices,
            String[] featureNames,
            int classCount,
            String[] labelOrder,
            String windowMode,
            long captureDelayMs
    ) {
        this.id = id == null ? "" : id;
        this.version = version == null ? "" : version;
        this.displayName = displayName == null ? this.id : displayName;
        this.inputChannels = inputChannels;
        this.frameCount = frameCount;
        this.frameIndices = frameIndices == null ? new int[0] : frameIndices.clone();
        this.featureNames = featureNames == null ? new String[0] : featureNames.clone();
        this.classCount = classCount;
        this.labelOrder = labelOrder == null ? new String[0] : labelOrder.clone();
        this.windowMode = windowMode == null ? "release" : windowMode;
        this.captureDelayMs = Math.max(0L, captureDelayMs);
    }

    public AispectTouchModelInfo(
            String id,
            String version,
            String displayName,
            int inputChannels,
            int frameCount,
            int[] frameIndices,
            String[] featureNames,
            int classCount,
            String[] labelOrder
    ) {
        this(
                id,
                version,
                displayName,
                inputChannels,
                frameCount,
                frameIndices,
                featureNames,
                classCount,
                labelOrder,
                "release",
                0L
        );
    }

    public AispectTouchModelInfo(
            String id,
            String displayName,
            int inputChannels,
            int frameCount,
            int[] frameIndices,
            String[] featureNames,
            int classCount,
            String[] labelOrder
    ) {
        this(id, "", displayName, inputChannels, frameCount, frameIndices, featureNames, classCount, labelOrder);
    }

    static AispectTouchModelInfo fromInternal(AispectImpactCNNClassifier.ModelInfo info) {
        if (info == null) {
            return null;
        }
        return new AispectTouchModelInfo(
                info.id,
                info.version,
                info.displayName,
                info.inputChannels,
                info.frameCount,
                info.frameIndices,
                info.featureNames,
                info.classCount,
                info.labelOrder,
                info.windowMode,
                info.captureDelayMs
        );
    }
}
