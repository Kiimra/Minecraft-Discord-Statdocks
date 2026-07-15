package dev.kiimra.statdock;

import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import dev.kiimra.statdock.command.StatdockCommand;
import dev.kiimra.statdock.config.PluginConfig;
import dev.kiimra.statdock.discord.DiscordRestClient;
import dev.kiimra.statdock.discord.GatewayPresenceClient;
import dev.kiimra.statdock.engine.IpResolver;
import dev.kiimra.statdock.engine.PlaceholderResolver;
import dev.kiimra.statdock.engine.RecordTracker;
import dev.kiimra.statdock.engine.ServerSnapshot;
import dev.kiimra.statdock.engine.StatdockEngine;
import dev.kiimra.statdock.engine.UptimeTracker;
import dev.kiimra.statdock.listener.PlayerActivityListener;

/**
 * Entry point. Wires the config, the REST-based statdock engine, and the
 * optional presence gateway together, and drives updates on a fixed schedule
 * plus immediate passes when players join/leave or an admin runs a command.
 */
public final class DiscordStatdockPlugin extends JavaPlugin {

    private UptimeTracker uptime;
    private PluginConfig config;
    private DiscordRestClient discord;
    private StatdockEngine engine;
    private GatewayPresenceClient gateway;
    private RecordTracker record;
    private String resolvedIp = "unknown";
    private volatile ServerSnapshot lastSnapshot = new ServerSnapshot(0, 0, 20.0, 0, "", "");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        uptime = new UptimeTracker();
        StatdockCommand command = new StatdockCommand(this);
        if (getCommand("statdock") != null) {
            getCommand("statdock").setExecutor(command);
            getCommand("statdock").setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new PlayerActivityListener(this), this);
        startServices();
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (gateway != null) {
            gateway.stop();
            gateway = null;
        }
        if (engine != null && config != null && config.hasToken()) {
            getLogger().info("Setting channels to offline before shutdown...");
            engine.pushOffline();
        }
    }

    /** (Re)builds every runtime component from the current config on disk. */
    public synchronized void startServices() {
        config = PluginConfig.load(getConfig(), getLogger());

        if (!config.hasToken()) {
            getLogger().warning("No bot token configured. Set 'token' in config.yml and run /statdock reload. "
                    + "The plugin is loaded but will not update any channels until then.");
            engine = null;
            return;
        }

        resolvedIp = new IpResolver(config.ip(), getLogger()).resolve(getServer().getIp());
        record = new RecordTracker(config.record().enabled(), config.record().persist(), getDataFolder(), getLogger());
        discord = new DiscordRestClient(config.token(), getLogger());
        engine = new StatdockEngine(config, discord, uptime, record, resolvedIp, getLogger());

        sampleSnapshot();

        // Validate channels off-thread so we never block server start on network.
        getServer().getScheduler().runTaskAsynchronously(this, () -> engine.validateChannels());

        long periodTicks = Math.max(1L, config.engineTickSeconds()) * 20L;
        getServer().getScheduler().runTaskTimer(this, this::dispatchUpdate, periodTicks, periodTicks);

        if (config.presence().enabled()) {
            gateway = new GatewayPresenceClient(config.token(), config.presence(), this::resolvePresence, getLogger());
            gateway.start();
            getLogger().info("Presence enabled: opening a lightweight gateway connection.");
        }

        getLogger().info("DiscordStatdockUpdater ready with " + config.channels().size()
                + " channel(s); IP resolved to '" + resolvedIp + "'.");
    }

    /** Samples the server on the main thread, then runs an engine pass async. */
    public void dispatchUpdate() {
        sampleSnapshot();
        StatdockEngine current = engine;
        if (current == null) {
            return;
        }
        ServerSnapshot snapshot = lastSnapshot;
        getServer().getScheduler().runTaskAsynchronously(this, () -> current.tick(snapshot));
    }

    /** Requests an immediate, out-of-cycle update (used by join/quit/commands). */
    public void requestImmediateUpdate() {
        if (engine != null) {
            engine.requestImmediate();
        }
        dispatchUpdate();
    }

    /** Must be called on the main thread: reads live Bukkit state into a snapshot. */
    private void sampleSnapshot() {
        int online = getServer().getOnlinePlayers().size();
        int max = getServer().getMaxPlayers();
        double tps = safeTps();
        int port = getServer().getPort();
        String version = safeVersion();
        String players = getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        lastSnapshot = new ServerSnapshot(online, max, tps, port, version, players);
    }

    private double safeTps() {
        try {
            double[] tps = getServer().getTPS();
            if (tps != null && tps.length > 0) {
                return tps[0];
            }
        } catch (Throwable ignored) {
            // Some server flavours may not expose TPS; treat as healthy.
        }
        return 20.0;
    }

    private String safeVersion() {
        try {
            return getServer().getMinecraftVersion();
        } catch (Throwable ignored) {
            try {
                return getServer().getVersion();
            } catch (Throwable ignored2) {
                return "";
            }
        }
    }

    private String resolvePresence(String template) {
        ServerSnapshot s = lastSnapshot;
        PlaceholderResolver.Context ctx = new PlaceholderResolver.Context(
                s.online(), s.max(), uptime.uptimeSeconds(), s.tps(), resolvedIp, s.port(),
                s.version(), record == null ? 0 : record.get(), s.playersCsv());
        return PlaceholderResolver.resolve(template, ctx);
    }

    public PluginConfig config() {
        return config;
    }

    public StatdockEngine engine() {
        return engine;
    }

    public String resolvedIp() {
        return resolvedIp;
    }

    public UptimeTracker uptime() {
        return uptime;
    }

    /** Full reload: tears down services (without touching uptime) and rebuilds. */
    public synchronized void reload() {
        getServer().getScheduler().cancelTasks(this);
        if (gateway != null) {
            gateway.stop();
            gateway = null;
        }
        reloadConfig();
        startServices();
    }
}
