package com.ceclientbridge.protocol;

/** Small dependency-free fixed-window limiter used to protect C2S probe handlers. */
public final class FixedWindowRateLimiter {
    private final int limit;
    private final long windowMillis;
    private long windowStart = Long.MIN_VALUE;
    private int acquired;

    public FixedWindowRateLimiter(int limit, long windowMillis) {
        if (limit <= 0 || windowMillis <= 0) throw new IllegalArgumentException("invalid rate limit");
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    public synchronized boolean tryAcquire(long nowMillis) {
        if (windowStart == Long.MIN_VALUE || nowMillis < windowStart || nowMillis - windowStart >= windowMillis) {
            windowStart = nowMillis;
            acquired = 0;
        }
        if (acquired >= limit) return false;
        acquired++;
        return true;
    }
}
