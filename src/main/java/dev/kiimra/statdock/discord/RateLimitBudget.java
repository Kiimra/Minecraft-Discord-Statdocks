package dev.kiimra.statdock.discord;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client-side guard against Discord's channel-rename limit (about 2 renames
 * per 10 minutes per channel). Tracks recent edit timestamps in a sliding
 * window and, additionally, honours a hard block until a given time whenever
 * Discord replies 429. All times are epoch millis; the class is single-thread
 * confined (only touched from the engine tick).
 */
public final class RateLimitBudget {

    private final int maxEdits;
    private final long windowMillis;
    private final Deque<Long> editTimes = new ArrayDeque<>();
    private long blockedUntilMillis;

    public RateLimitBudget(int maxEdits, long windowMillis) {
        this.maxEdits = Math.max(1, maxEdits);
        this.windowMillis = Math.max(1_000L, windowMillis);
    }

    public boolean canEditNow(long now) {
        if (now < blockedUntilMillis) {
            return false;
        }
        prune(now);
        return editTimes.size() < maxEdits;
    }

    public void recordEdit(long now) {
        editTimes.addLast(now);
    }

    /** Records a hard 429 block; the channel won't be edited until it clears. */
    public void blockFor(long now, long millis) {
        long until = now + Math.max(0, millis);
        if (until > blockedUntilMillis) {
            blockedUntilMillis = until;
        }
    }

    /** Milliseconds until the next edit is permitted (0 if allowed right now). */
    public long millisUntilNextAllowed(long now) {
        long fromBlock = Math.max(0, blockedUntilMillis - now);
        prune(now);
        long fromWindow = 0;
        if (editTimes.size() >= maxEdits) {
            Long oldest = editTimes.peekFirst();
            if (oldest != null) {
                fromWindow = Math.max(0, (oldest + windowMillis) - now);
            }
        }
        return Math.max(fromBlock, fromWindow);
    }

    private void prune(long now) {
        long cutoff = now - windowMillis;
        while (!editTimes.isEmpty() && editTimes.peekFirst() <= cutoff) {
            editTimes.pollFirst();
        }
    }
}
