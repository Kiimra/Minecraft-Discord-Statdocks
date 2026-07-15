package dev.kiimra.statdock.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import dev.kiimra.statdock.config.ChannelConfig;
import dev.kiimra.statdock.config.PluginConfig;
import dev.kiimra.statdock.discord.DiscordRestClient;
import dev.kiimra.statdock.discord.EditResult;
import dev.kiimra.statdock.discord.RateLimitBudget;

/**
 * Owns the update logic for every configured statdock: computes each channel's
 * desired name from the current {@link ServerState}, skips no-op edits, and
 * applies changes through Discord while respecting the per-channel rename
 * budget. All edits happen on the async engine thread; state transitions
 * (join/quit/maintenance) merely flag an immediate pass.
 */
public final class StatdockEngine {

    private static final long AUTH_RETRY_MILLIS = 60_000L;
    private static final long TRANSIENT_RETRY_MILLIS = 15_000L;
    private static final int MAX_NAME_LENGTH = 100;

    private final PluginConfig config;
    private final DiscordRestClient discord;
    private final UptimeTracker uptime;
    private final RecordTracker record;
    private final Logger logger;
    private final String resolvedIp;
    private final List<ManagedChannel> channels = new ArrayList<>();
    private final ReentrantLock tickLock = new ReentrantLock();

    private volatile boolean maintenance;
    private volatile boolean shuttingDown;

    public StatdockEngine(PluginConfig config, DiscordRestClient discord, UptimeTracker uptime,
                          RecordTracker record, String resolvedIp, Logger logger) {
        this.config = config;
        this.discord = discord;
        this.uptime = uptime;
        this.record = record;
        this.resolvedIp = resolvedIp;
        this.logger = logger;
        long windowMillis = config.rateLimit().windowSeconds() * 1000L;
        for (ChannelConfig cc : config.channels()) {
            channels.add(new ManagedChannel(cc, new RateLimitBudget(config.rateLimit().maxEdits(), windowMillis)));
        }
    }

    /** Validates each channel's type/existence against Discord; logs findings. */
    public void validateChannels() {
        for (ManagedChannel mc : channels) {
            discord.fetchChannel(mc.config.id()).ifPresentOrElse(info -> {
                if (!info.isSupported()) {
                    logger.warning("Channel '" + mc.config.name() + "' (" + mc.config.id() + ") is a "
                            + info.typeName() + " channel. Statdocks are meant for voice, stage, or category "
                            + "channels; renaming may still work but is not officially supported.");
                } else {
                    logger.info("Statdock '" + mc.config.name() + "' -> " + info.typeName()
                            + " channel \"" + info.name() + "\".");
                }
            }, () -> logger.warning("Could not verify channel '" + mc.config.name() + "' (" + mc.config.id()
                    + "). It will still be attempted; check the ID and the bot's access."));
        }
    }

    public void setMaintenance(boolean maintenance) {
        this.maintenance = maintenance;
        requestImmediate();
    }

    public boolean isMaintenance() {
        return maintenance;
    }

    /** Flags every channel for an out-of-cycle update on the next pass. */
    public void requestImmediate() {
        for (ManagedChannel mc : channels) {
            mc.immediatePending = true;
        }
    }

    public List<ManagedChannel> channels() {
        return channels;
    }

    /**
     * One engine pass. Safe to call from overlapping async tasks: if a pass is
     * already running the call is dropped (the next scheduled pass will catch
     * up, since desired names are recomputed every time).
     */
    public void tick(ServerSnapshot snapshot) {
        if (!tickLock.tryLock()) {
            return;
        }
        try {
            record.update(snapshot.online());
            long now = System.currentTimeMillis();
            for (ManagedChannel mc : channels) {
                if (mc.skipped) {
                    continue;
                }
                applyChannel(mc, snapshot, now);
            }
        } finally {
            tickLock.unlock();
        }
    }

    private void applyChannel(ManagedChannel mc, ServerSnapshot snapshot, long now) {
        ServerState state = computeState(snapshot);
        String desired = clampName(PlaceholderResolver.resolve(config.template(mc.config, state), context(snapshot)));
        if (desired.isEmpty()) {
            return; // Nothing to show for this state; leave the channel untouched.
        }
        if (desired.equals(mc.lastAppliedName)) {
            mc.immediatePending = false;
            return; // Dedup: never spend an edit on an identical name.
        }
        boolean due = mc.immediatePending
                || mc.lastAppliedName == null
                || (now - mc.lastApplyMillis) >= mc.config.intervalSeconds() * 1000L;
        if (!due || !mc.budget.canEditNow(now)) {
            return; // Not yet, or out of budget - the next pass will retry with a fresh name.
        }

        EditResult result = discord.setChannelName(mc.config.id(), desired);
        switch (result.outcome()) {
            case SUCCESS -> {
                mc.budget.recordEdit(now);
                mc.lastAppliedName = desired;
                mc.lastApplyMillis = now;
                mc.immediatePending = false;
            }
            case RATE_LIMITED -> mc.budget.blockFor(now, result.retryAfterMillis());
            case NOT_FOUND -> {
                mc.skipped = true;
                logger.warning("Channel '" + mc.config.name() + "' (" + mc.config.id()
                        + ") no longer exists on Discord - skipping it until reload.");
            }
            case UNAUTHORIZED -> {
                mc.budget.blockFor(now, AUTH_RETRY_MILLIS);
                warnThrottled(mc, now, "Invalid bot token while updating '" + mc.config.name()
                        + "'. Check the token in config.yml; retrying periodically.");
            }
            case FORBIDDEN -> {
                mc.budget.blockFor(now, AUTH_RETRY_MILLIS);
                warnThrottled(mc, now, "Bot lacks permission to edit '" + mc.config.name()
                        + "' (" + mc.config.id() + "). Grant it Manage Channel; retrying periodically.");
            }
            case TRANSIENT_ERROR -> mc.budget.blockFor(now, TRANSIENT_RETRY_MILLIS);
        }
    }

    /**
     * Best-effort synchronous push of the offline name to every channel, used
     * on shutdown before the server stops. Ignores the local budget (this is
     * the one edit that matters most) but a Discord 429 can still prevent it -
     * a documented limitation if the server later crashes.
     */
    public void pushOffline() {
        shuttingDown = true;
        ServerSnapshot snapshot = new ServerSnapshot(0, 0, 20.0, 0, "", "");
        for (ManagedChannel mc : channels) {
            if (mc.skipped) {
                continue;
            }
            String desired = clampName(
                    PlaceholderResolver.resolve(config.template(mc.config, ServerState.OFFLINE), context(snapshot)));
            if (desired.isEmpty() || desired.equals(mc.lastAppliedName)) {
                continue;
            }
            EditResult result = discord.setChannelName(mc.config.id(), desired);
            if (result.isSuccess()) {
                mc.lastAppliedName = desired;
            } else {
                logger.warning("Could not set offline name for '" + mc.config.name() + "': " + result.outcome()
                        + (result.detail() != null ? " (" + result.detail() + ")" : ""));
            }
        }
    }

    private ServerState computeState(ServerSnapshot snapshot) {
        if (shuttingDown) {
            return ServerState.OFFLINE;
        }
        if (maintenance) {
            return ServerState.MAINTENANCE;
        }
        if (config.lag().enabled() && snapshot.tps() < config.lag().thresholdTps()) {
            return ServerState.LAG;
        }
        if (snapshot.online() >= 1) {
            return ServerState.ONLINE;
        }
        return ServerState.IDLE;
    }

    private PlaceholderResolver.Context context(ServerSnapshot s) {
        return new PlaceholderResolver.Context(
                s.online(), s.max(), uptime.uptimeSeconds(), s.tps(),
                resolvedIp, s.port(), s.version(), record.get(), s.playersCsv());
    }

    private String clampName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        return trimmed;
    }

    private void warnThrottled(ManagedChannel mc, long now, String message) {
        if (now - mc.lastAuthWarnMillis >= AUTH_RETRY_MILLIS) {
            logger.warning(message);
            mc.lastAuthWarnMillis = now;
        }
    }

    /** Mutable per-channel runtime state. */
    public static final class ManagedChannel {
        private final ChannelConfig config;
        private final RateLimitBudget budget;
        private String lastAppliedName;
        private long lastApplyMillis;
        private boolean immediatePending = true;
        private boolean skipped;
        private long lastAuthWarnMillis;

        ManagedChannel(ChannelConfig config, RateLimitBudget budget) {
            this.config = config;
            this.budget = budget;
        }

        public String name() {
            return config.name();
        }

        public String id() {
            return config.id();
        }

        public String lastAppliedName() {
            return lastAppliedName;
        }

        public boolean skipped() {
            return skipped;
        }

        public long millisUntilNextAllowed(long now) {
            return budget.millisUntilNextAllowed(now);
        }
    }
}
