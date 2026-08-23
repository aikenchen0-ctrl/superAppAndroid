package com.zhifaios.eyes.aispect;

import org.json.JSONException;

import java.io.IOException;

final class AispectRemoteModelUpdateGate {
    interface Operation<T> {
        T run() throws IOException, JSONException;
    }

    private final Object serializationLock = new Object();
    private volatile boolean cancelled;

    <T> T runSerialized(Operation<T> operation) throws IOException, JSONException {
        synchronized (serializationLock) {
            return operation.run();
        }
    }

    void cancel() {
        cancelled = true;
    }

    boolean isCancelled() {
        return cancelled;
    }
}
