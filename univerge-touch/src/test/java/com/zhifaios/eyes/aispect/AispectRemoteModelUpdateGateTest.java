package com.zhifaios.eyes.aispect;

import com.zhifa.univerge.eyes.aispect.AispectRemoteModelUpdateGate;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AispectRemoteModelUpdateGateTest {
    @Test
    public void cancelDoesNotWaitForActiveSerializedRefresh() throws Exception {
        AispectRemoteModelUpdateGate gate = new AispectRemoteModelUpdateGate();
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        CountDownLatch refreshFinished = new CountDownLatch(1);
        Thread refreshThread = new Thread(() -> {
            try {
                gate.runSerialized(() -> {
                    refreshStarted.countDown();
                    try {
                        releaseRefresh.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new java.io.IOException("refresh interrupted", error);
                    }
                    return null;
                });
            } catch (Exception ignored) {
                Thread.currentThread().interrupt();
            } finally {
                refreshFinished.countDown();
            }
        });
        refreshThread.start();
        Assert.assertTrue(refreshStarted.await(1, TimeUnit.SECONDS));
        CountDownLatch cancelFinished = new CountDownLatch(1);
        Thread cancelThread = new Thread(() -> {
            gate.cancel();
            cancelFinished.countDown();
        });
        cancelThread.start();

        try {
            Assert.assertTrue(cancelFinished.await(200, TimeUnit.MILLISECONDS));
            Assert.assertTrue(gate.isCancelled());
            Assert.assertEquals(1L, refreshFinished.getCount());
        } finally {
            releaseRefresh.countDown();
        }
        Assert.assertTrue(cancelFinished.await(1, TimeUnit.SECONDS));
        Assert.assertTrue(refreshFinished.await(1, TimeUnit.SECONDS));
    }
}
