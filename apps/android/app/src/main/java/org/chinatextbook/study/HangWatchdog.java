package org.chinatextbook.study;

/** Tracks one outstanding page heartbeat; a reply that never comes means the renderer's JS thread is stuck. */
final class HangWatchdog {
    private final long timeoutMillis;
    private long pingSentAt = -1;

    HangWatchdog(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    boolean shouldPing() {
        return pingSentAt < 0;
    }

    void pinged(long now) {
        pingSentAt = now;
    }

    void ponged() {
        pingSentAt = -1;
    }

    boolean isHung(long now) {
        return pingSentAt >= 0 && now - pingSentAt >= timeoutMillis;
    }

    void reset() {
        pingSentAt = -1;
    }
}
