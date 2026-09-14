package com.blinkvoice.visual.api.v2;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Platform-neutral session factory. The default constructor owns a serialized callback executor;
 * callers that already have an executor can inject it for lifecycle ownership and testing.
 */
public final class DefaultBlinkSessionFactory implements BlinkSessionFactory, AutoCloseable {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();

    private final Executor callbackExecutor;
    private final ExecutorService ownedExecutor;

    public DefaultBlinkSessionFactory() {
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "blink-session-callback-" + THREAD_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
        this.callbackExecutor = executor;
        this.ownedExecutor = executor;
    }

    public DefaultBlinkSessionFactory(Executor callbackExecutor) {
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.ownedExecutor = null;
    }

    @Override
    public BlinkSession create(BlinkOptions options, BlinkListener listener) {
        return new DefaultBlinkSession(
                options != null ? options : new BlinkOptions.Builder().build(),
                Objects.requireNonNull(listener, "listener"),
                callbackExecutor
        );
    }

    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }
}
