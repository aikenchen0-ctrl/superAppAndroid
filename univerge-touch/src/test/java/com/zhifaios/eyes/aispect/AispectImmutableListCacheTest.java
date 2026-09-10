package com.zhifaios.eyes.aispect;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AispectImmutableListCacheTest {
    @Test
    public void reusesImmutableSnapshotUntilInvalidated() {
        AispectImmutableListCache<String> cache = new AispectImmutableListCache<>();
        List<String> source = new ArrayList<>(Arrays.asList("model-a", "model-b"));

        List<String> first = cache.snapshot(source);
        List<String> second = cache.snapshot(source);

        Assert.assertSame(first, second);
        try {
            first.add("model-c");
            Assert.fail("snapshot must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: callers cannot mutate the shared snapshot.
        }

        cache.invalidate();
        Assert.assertNotSame(first, cache.snapshot(source));
    }
}
