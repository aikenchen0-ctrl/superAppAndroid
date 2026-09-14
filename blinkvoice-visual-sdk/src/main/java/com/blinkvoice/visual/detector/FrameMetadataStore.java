package com.blinkvoice.visual.detector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Associates asynchronous detector callbacks with the exact frame submitted to MediaPipe.
 * The identity-based key avoids relying on platform image equality and the bounded queue keeps
 * dropped callbacks from retaining an unbounded number of image objects.
 */
final class FrameMetadataStore {
    private static final int DEFAULT_MAX_PENDING = 32;

    private final int maxPending;
    private final Map<Object, Metadata> metadataByFrame = new IdentityHashMap<>();
    private final Deque<Object> insertionOrder = new ArrayDeque<>();

    FrameMetadataStore() {
        this(DEFAULT_MAX_PENDING);
    }

    FrameMetadataStore(int maxPending) {
        if (maxPending <= 0) {
            throw new IllegalArgumentException("maxPending must be positive");
        }
        this.maxPending = maxPending;
    }

    synchronized void record(Object frame, long frameTimeMs, int rotationDegrees, long dispatchTimeMs) {
        if (frame == null) {
            throw new IllegalArgumentException("frame must not be null");
        }
        Metadata previous = metadataByFrame.put(frame,
                new Metadata(frameTimeMs, rotationDegrees, dispatchTimeMs));
        if (previous == null) {
            insertionOrder.addLast(frame);
        }
        while (metadataByFrame.size() > maxPending) {
            Object oldest = insertionOrder.removeFirst();
            metadataByFrame.remove(oldest);
        }
    }

    synchronized Metadata take(Object frame) {
        if (frame == null) {
            return null;
        }
        Metadata metadata = metadataByFrame.remove(frame);
        if (metadata != null) {
            removeIdentityFromOrder(frame);
        }
        return metadata;
    }

    private void removeIdentityFromOrder(Object frame) {
        Iterator<Object> iterator = insertionOrder.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == frame) {
                iterator.remove();
                return;
            }
        }
    }

    synchronized void clear() {
        metadataByFrame.clear();
        insertionOrder.clear();
    }

    static final class Metadata {
        private final long frameTimeMs;
        private final int rotationDegrees;
        private final long dispatchTimeMs;

        Metadata(long frameTimeMs, int rotationDegrees, long dispatchTimeMs) {
            this.frameTimeMs = frameTimeMs;
            this.rotationDegrees = rotationDegrees;
            this.dispatchTimeMs = dispatchTimeMs;
        }

        long getFrameTimeMs() {
            return frameTimeMs;
        }

        int getRotationDegrees() {
            return rotationDegrees;
        }

        long getDispatchTimeMs() {
            return dispatchTimeMs;
        }
    }
}
