package dev.kiimra.statdock.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import dev.kiimra.statdock.engine.ServerState;

/**
 * Immutable snapshot of the plugin configuration. Built once on enable and
 * rebuilt on {@code /statdock reload}, so the running engine never reads a
 * half-updated config.
 */
public final class PluginConfig {

    private final String token;
    private final int engineTickSeconds;
    private final RateLimitConfig rateLimit;
    private final IpConfig ip;
    private final PresenceConfig presence;
    private final LagConfig lag;
    private final RecordConfig record;
    private final boolean placeholderApi;
    private final Map<ServerState, String> defaults;
    private final List<ChannelConfig> channels;
    private final Map<String, String> messages;

    private PluginConfig(String token, int engineTickSeconds, RateLimitConfig rateLimit, IpConfig ip,
                         PresenceConfig presence, LagConfig lag, RecordConfig record, boolean placeholderApi,
                         Map<ServerState, String> defaults, List<ChannelConfig> channels,
                         Map<String, String> messages) {
        this.token = token;
        this.engineTickSeconds = engineTickSeconds;
        this.rateLimit = rateLimit;
        this.ip = ip;
        this.presence = presence;
        this.lag = lag;
        this.record = record;
        this.placeholderApi = placeholderApi;
        this.defaults = defaults;
        this.channels = channels;
        this.messages = messages;
    }

    public String token() {
        return token;
    }

    public int engineTickSeconds() {
        return engineTickSeconds;
    }

    public RateLimitConfig rateLimit() {
        return rateLimit;
    }

    public IpConfig ip() {
        return ip;
    }

    public PresenceConfig presence() {
        return presence;
    }

    public LagConfig lag() {
        return lag;
    }

    public RecordConfig record() {
        return record;
    }

    /** Whether %placeholders% may be parsed through PlaceholderAPI when it is installed. */
    public boolean placeholderApi() {
        return placeholderApi;
    }

    public List<ChannelConfig> channels() {
        return channels;
    }

    /** Template for {@code state} on the given channel, falling back to the global default. */
    public String template(ChannelConfig channel, ServerState state) {
        String override = channel.templates().get(state);
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return defaults.getOrDefault(state, "");
    }

    public String message(String key) {
        return messages.getOrDefault(key, "");
    }

    public boolean hasToken() {
        return token != null && !token.isBlank() && !token.equals("YOUR_BOT_TOKEN");
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public static PluginConfig load(FileConfiguration cfg, Logger logger) {
        String token = cfg.getString("token", "");

        int tick = Math.max(1, cfg.getInt("engine.tick-seconds", 5));

        RateLimitConfig rate = new RateLimitConfig(
                Math.max(1, cfg.getInt("rate-limit.max-edits", 2)),
                Math.max(30, cfg.getInt("rate-limit.window-seconds", 600)));

        IpConfig ip = new IpConfig(
                cfg.getString("ip.override", ""),
                cfg.getBoolean("ip.resolve-public-ip", true),
                cfg.getString("ip.unknown-text", "unknown"));

        List<String> presenceMessages = cfg.getStringList("presence.messages");
        if (presenceMessages.isEmpty()) {
            presenceMessages = new ArrayList<>(List.of("{online}/{max} players"));
        }
        PresenceConfig presence = new PresenceConfig(
                cfg.getBoolean("presence.enabled", false),
                cfg.getString("presence.activity-type", "watching"),
                cfg.getString("presence.status", "online"),
                Math.max(15, cfg.getInt("presence.interval-seconds", 60)),
                presenceMessages);

        LagConfig lag = new LagConfig(
                cfg.getBoolean("lag.enabled", true),
                cfg.getDouble("lag.threshold-tps", 15.0));

        RecordConfig record = new RecordConfig(
                cfg.getBoolean("record.enabled", false),
                cfg.getBoolean("record.persist", false));

        boolean placeholderApi = cfg.getBoolean("placeholderapi.enabled", true);

        Map<ServerState, String> defaults = new EnumMap<>(ServerState.class);
        defaults.put(ServerState.ONLINE, cfg.getString("defaults.online", "🟢│Online {online}/{max} ({uptime})"));
        defaults.put(ServerState.IDLE, cfg.getString("defaults.idle", "🌙│Zzz... {online}/{max} ({uptime})"));
        defaults.put(ServerState.OFFLINE, cfg.getString("defaults.offline", "🔴│Offline"));
        defaults.put(ServerState.LAG, cfg.getString("defaults.lag", "🟡│Lag {online}/{max} ({tps} TPS)"));
        defaults.put(ServerState.MAINTENANCE, cfg.getString("defaults.maintenance", "🟣│Maintenance"));

        List<ChannelConfig> channels = new ArrayList<>();
        ConfigurationSection channelsSection = cfg.getConfigurationSection("channels");
        if (channelsSection != null) {
            for (String key : channelsSection.getKeys(false)) {
                ConfigurationSection cs = channelsSection.getConfigurationSection(key);
                if (cs == null) {
                    continue;
                }
                String id = cs.getString("id", "").trim();
                if (id.isEmpty() || id.startsWith("0000")) {
                    logger.warning("Channel '" + key + "' has no valid 'id' set - skipping it.");
                    continue;
                }
                int interval = cs.getInt("interval-seconds", 600);
                if (interval < 300) {
                    logger.warning("Channel '" + key + "' interval-seconds=" + interval
                            + " is below Discord's safe minimum; clamping to 300 (Discord allows ~2 renames / 10 min).");
                    interval = 300;
                }
                Map<ServerState, String> templates = new EnumMap<>(ServerState.class);
                for (ServerState state : ServerState.values()) {
                    if (cs.contains(state.key())) {
                        templates.put(state, cs.getString(state.key()));
                    }
                }
                channels.add(new ChannelConfig(key, id, interval, templates));
            }
        }
        if (channels.isEmpty()) {
            logger.warning("No channels configured. Add at least one entry under 'channels' in config.yml.");
        }

        Map<String, String> messages = new LinkedHashMap<>();
        putMessage(messages, cfg, "prefix", "&8[&bStatdock&8]&r ");
        putMessage(messages, cfg, "no-permission", "&cYou don't have permission to do that.");
        putMessage(messages, cfg, "reload-success", "&aConfiguration reloaded.");
        putMessage(messages, cfg, "reload-fail", "&cReload failed - check the console.");
        putMessage(messages, cfg, "force-update", "&aForcing an update of all statdocks...");
        putMessage(messages, cfg, "maintenance-on", "&aMaintenance mode enabled.");
        putMessage(messages, cfg, "maintenance-off", "&aMaintenance mode disabled.");
        putMessage(messages, cfg, "maintenance-usage", "&cUsage: /statdock maintenance <on|off>");
        putMessage(messages, cfg, "unknown-subcommand", "&cUnknown subcommand. Try /statdock help.");

        return new PluginConfig(token, tick, rate, ip, presence, lag, record, placeholderApi, defaults, channels,
                messages);
    }

    private static void putMessage(Map<String, String> map, FileConfiguration cfg, String key, String def) {
        map.put(key, cfg.getString("messages." + key, def));
    }

    // ------------------------------------------------------------------
    // Nested value types
    // ------------------------------------------------------------------

    public record RateLimitConfig(int maxEdits, int windowSeconds) {
    }

    public record IpConfig(String override, boolean resolvePublicIp, String unknownText) {
    }

    public record LagConfig(boolean enabled, double thresholdTps) {
    }

    public record RecordConfig(boolean enabled, boolean persist) {
    }

    public record PresenceConfig(boolean enabled, String activityType, String status,
                                 int intervalSeconds, List<String> messages) {

        /** Discord activity type integer for the configured name. */
        public int activityTypeId() {
            return switch (activityType == null ? "" : activityType.toLowerCase(Locale.ROOT)) {
                case "playing" -> 0;
                case "listening" -> 2;
                case "competing" -> 5;
                default -> 3; // watching
            };
        }
    }
}
