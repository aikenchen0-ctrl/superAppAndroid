package com.blinkvoice.visual.detector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FrameMetadataStoreTest {
    @Test
    public void matchesMetadataByFrameIdentityWhenSubmissionsOverlap() {
        FrameMetadataStore store = new FrameMetadataStore(4);
        Object first = new Object();
        Object second = new Object();

        store.record(first, 100L, 90, 10L);
        store.record(second, 200L, 270, 20L);

        FrameMetadataStore.Metadata firstMetadata = store.take(first);
        FrameMetadataStore.Metadata secondMetadata = store.take(second);

        assertEquals(100L, firstMetadata.getFrameTimeMs());
        assertEquals(90, firstMetadata.getRotationDegrees());
        assertEquals(10L, firstMetadata.getDispatchTimeMs());
        assertEquals(200L, secondMetadata.getFrameTimeMs());
        assertEquals(270, secondMetadata.getRotationDegrees());
        assertEquals(20L, secondMetadata.getDispatchTimeMs());
    }

    @Test
    public void evictsOldestPendingMetadataToKeepMemoryBounded() {
        FrameMetadataStore store = new FrameMetadataStore(2);
        Object first = new Object();
        Object second = new Object();
        Object third = new Object();

        store.record(first, 100L, 0, 1L);
        store.record(second, 200L, 0, 2L);
        store.record(third, 300L, 0, 3L);

        assertNull(store.take(first));
        assertEquals(200L, store.take(second).getFrameTimeMs());
        assertEquals(300L, store.take(third).getFrameTimeMs());
    }

    @Test
    public void removesTheExactFrameWhenDifferentFramesCompareEqual() {
        FrameMetadataStore store = new FrameMetadataStore(2);
        EqualFrame first = new EqualFrame();
        EqualFrame second = new EqualFrame();
        EqualFrame third = new EqualFrame();
        EqualFrame fourth = new EqualFrame();

        store.record(first, 100L, 0, 1L);
        store.record(second, 200L, 0, 2L);

        assertEquals(100L, store.take(first).getFrameTimeMs());
        assertEquals(200L, store.take(second).getFrameTimeMs());

        store.record(first, 100L, 0, 1L);
        store.record(second, 200L, 0, 2L);
        assertEquals(200L, store.take(second).getFrameTimeMs());
        store.record(third, 300L, 0, 3L);
        store.record(fourth, 400L, 0, 4L);

        assertNull(store.take(first));
        assertEquals(300L, store.take(third).getFrameTimeMs());
        assertEquals(400L, store.take(fourth).getFrameTimeMs());
    }

    private static final class EqualFrame {
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualFrame;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
