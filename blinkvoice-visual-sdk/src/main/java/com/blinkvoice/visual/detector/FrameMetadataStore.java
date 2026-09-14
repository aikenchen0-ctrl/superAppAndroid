package com.blinkvoice.visual.detector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.HashMap;
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
    private final Map<Long, Deque<Metadata>> metadataByTimestamp = new HashMap<>();
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
        Metadata previous = metadataByFrame.remove(frame);
        if (previous != null) {
            removeMetadataTracking(previous);
        }
        Metadata metadata = new Metadata(frame, frameTimeMs, rotationDegrees, dispatchTimeMs);
        metadataByFrame.put(frame, metadata);
        insertionOrder.addLast(frame);
        metadataByTimestamp.computeIfAbsent(frameTimeMs, ignored -> new ArrayDeque<>()).addLast(metadata);
        while (metadataByFrame.size() > maxPending) {
            Object oldest = insertionOrder.removeFirst();
            Metadata evicted = metadataByFrame.remove(oldest);
            if (evicted != null) {
                removeTimestampEntry(evicted);
            }
        }
    }

    synchronized Metadata take(Object frame) {
        if (frame == null) {
            return null;
        }
        Metadata metadata = metadataByFrame.remove(frame);
        if (metadata != null) {
            removeMetadataTracking(metadata);
        }
        return metadata;
    }

    synchronized Metadata takeForFrameTime(long frameTimeMs) {
        Deque<Metadata> candidates = metadataByTimestamp.get(frameTimeMs);
        if (candidates == null) {
            return null;
        }
        Metadata metadata = null;
        while (!candidates.isEmpty() && metadata == null) {
            Metadata candidate = candidates.removeFirst();
            if (metadataByFrame.get(candidate.frame) == candidate) {
                metadata = candidate;
                metadataByFrame.remove(candidate.frame);
                removeIdentityFromOrder(candidate.frame);
            }
        }
        if (candidates.isEmpty()) {
            metadataByTimestamp.remove(frameTimeMs);
        }
        return metadata;
    }

    private void removeMetadataTracking(Metadata metadata) {
        removeIdentityFromOrder(metadata.frame);
        removeTimestampEntry(metadata);
    }

    private void removeTimestampEntry(Metadata metadata) {
        Deque<Metadata> candidates = metadataByTimestamp.get(metadata.frameTimeMs);
        if (candidates == null) {
            return;
        }
        Iterator<Metadata> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == metadata) {
                iterator.remove();
                break;
            }
        }
        if (candidates.isEmpty()) {
            metadataByTimestamp.remove(metadata.frameTimeMs);
        }
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
        metadataByTimestamp.clear();
        insertionOrder.clear();
    }

    static final class Metadata {
        private final Object frame;
        private final long frameTimeMs;
        private final int rotationDegrees;
        private final long dispatchTimeMs;

        Metadata(Object frame, long frameTimeMs, int rotationDegrees, long dispatchTimeMs) {
            this.frame = frame;
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
