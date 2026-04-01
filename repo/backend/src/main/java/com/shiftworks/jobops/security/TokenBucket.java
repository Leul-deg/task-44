package com.shiftworks.jobops.security;

class TokenBucket {

    private final int capacity;
    private final double refillPerMillis;
    private double tokens;
    private long lastRefillTime;

    TokenBucket(int capacity, int refillPerMinute) {
        this.capacity = capacity;
        this.tokens = capacity;
        this.refillPerMillis = (double) refillPerMinute / 60000d;
        this.lastRefillTime = System.currentTimeMillis();
    }

    synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long millisSinceLast = now - lastRefillTime;
        if (millisSinceLast <= 0) {
            return;
        }
        double tokensToAdd = millisSinceLast * refillPerMillis;
        if (tokensToAdd >= 1) {
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTime = now;
        }
    }
}
