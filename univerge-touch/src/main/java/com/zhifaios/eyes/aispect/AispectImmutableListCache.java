package com.zhifaios.eyes.aispect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 中文注释：缓存只读模型列表快照，避免状态回调反复复制同一列表。 */
final class AispectImmutableListCache<T> {
    private List<T> snapshot;

    synchronized List<T> snapshot(List<T> source) {
        if (snapshot == null) {
            snapshot = Collections.unmodifiableList(new ArrayList<>(source));
        }
        return snapshot;
    }

    synchronized void invalidate() {
        snapshot = null;
    }
}
