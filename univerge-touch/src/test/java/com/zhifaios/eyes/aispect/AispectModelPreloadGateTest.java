package com.zhifaios.eyes.aispect;

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AispectModelPreloadGateTest {
    @Test
    public void schedulesAtMostOnceForTheGateLifecycle() {
        AispectModelPreloadGate gate = new AispectModelPreloadGate();
        QueuedExecutor executor = new QueuedExecutor();

        assertTrue(gate.schedule(executor, () -> { }));
        assertFalse(gate.schedule(executor, () -> { }));
        assertEquals(1, executor.count);

        executor.runPending();

        assertFalse(gate.schedule(executor, () -> { }));
    }

    @Test
    public void permitsRetryAfterExecutorRejectsScheduling() {
        AispectModelPreloadGate gate = new AispectModelPreloadGate();

        assertFalse(gate.schedule(command -> {
            throw new RejectedExecutionException("closed");
        }, () -> { }));
        assertTrue(gate.schedule(Runnable::run, () -> { }));
    }

    @Test
    public void closeBeforeQueuedTaskPreventsPreloadFromRunning() {
        AispectModelPreloadGate gate = new AispectModelPreloadGate();
        QueuedExecutor executor = new QueuedExecutor();
        int[] runs = new int[]{0};

        assertTrue(gate.schedule(executor, () -> runs[0] += 1));
        gate.close();
        executor.runPending();

        assertEquals(0, runs[0]);
        assertFalse(gate.schedule(Runnable::run, () -> runs[0] += 1));
    }

    @Test
    public void closeAfterPreloadKeepsGateClosed() {
        AispectModelPreloadGate gate = new AispectModelPreloadGate();
        int[] runs = new int[]{0};

        assertTrue(gate.schedule(Runnable::run, () -> runs[0] += 1));
        gate.close();

        assertEquals(1, runs[0]);
        assertFalse(gate.schedule(Runnable::run, () -> runs[0] += 1));
        assertEquals(1, runs[0]);
    }

    private static final class QueuedExecutor implements Executor {
        int count;
        Runnable pending;

        @Override
        public void execute(Runnable command) {
            count += 1;
            pending = command;
        }

        void runPending() {
            pending.run();
            pending = null;
        }
    }
}
