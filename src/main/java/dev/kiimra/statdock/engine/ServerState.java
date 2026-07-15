package dev.kiimra.statdock.engine;

/**
 * The mutually exclusive states a statdock can display, in priority order:
 * a higher-priority state wins when several would apply at once (for example
 * {@link #OFFLINE} always wins during shutdown, and {@link #MAINTENANCE}
 * overrides normal player counts).
 */
public enum ServerState {
    OFFLINE("offline"),
    MAINTENANCE("maintenance"),
    LAG("lag"),
    ONLINE("online"),
    IDLE("idle");

    private final String key;

    ServerState(String key) {
        this.key = key;
    }

    /** Config key used for this state's template. */
    public String key() {
        return key;
    }
}
