package com.blinkvoice.visual.api.v2;

/** Platform-neutral factory implemented by an SDK runtime. */
public interface BlinkSessionFactory {
    BlinkSession create(BlinkOptions options, BlinkListener listener);
}
