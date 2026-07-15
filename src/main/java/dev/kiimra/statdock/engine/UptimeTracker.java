package dev.kiimra.statdock.engine;

/**
 * Tracks how long the server has been up during the current run. Uptime is
 * intentionally session-scoped: it starts at zero on every boot and never
 * persists across restarts.
 */
public final class UptimeTracker {

    private final long startNanos;

    public UptimeTracker() {
        this.startNanos = System.nanoTime();
    }

    public long uptimeSeconds() {
        return (System.nanoTime() - startNanos) / 1_000_000_000L;
    }
}
