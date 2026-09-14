package com.zhifa.univerge.eyes.aispect;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class AispectModelPreloadGate {
    private boolean scheduled;
    private boolean closed;

    public boolean schedule(Executor executor, Runnable preload) {
        if (executor == null || preload == null) {
            return false;
        }
        synchronized (this) {
            if (scheduled || closed) {
                return false;
            }
            scheduled = true;
        }
        try {
            executor.execute(() -> {
                synchronized (AispectModelPreloadGate.this) {
                    if (closed) {
                        return;
                    }
                }
                preload.run();
            });
            return true;
        } catch (RejectedExecutionException error) {
            synchronized (this) {
                if (!closed) {
                    scheduled = false;
                }
            }
            return false;
        }
    }

    public synchronized void close() {
        closed = true;
    }
}
