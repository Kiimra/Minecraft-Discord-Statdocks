package dev.kiimra.statdock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import dev.kiimra.statdock.command.StatdockCommand;
import dev.kiimra.statdock.config.PluginConfig;
import dev.kiimra.statdock.discord.DiscordRestClient;
import dev.kiimra.statdock.discord.GatewayPresenceClient;
import dev.kiimra.statdock.engine.IpResolver;
import dev.kiimra.statdock.engine.MaintenanceStore;
import dev.kiimra.statdock.engine.PlaceholderResolver;
import dev.kiimra.statdock.engine.RecordTracker;
import dev.kiimra.statdock.engine.ServerSnapshot;
import dev.kiimra.statdock.engine.StatdockEngine;
import dev.kiimra.statdock.engine.UptimeTracker;
import dev.kiimra.statdock.hook.PlaceholderApiHook;
import dev.kiimra.statdock.listener.PlayerActivityListener;

/**
 * Entry point. Wires the config, the REST-based statdock engine, and the
 * optional presence gateway together, and drives updates on a fixed schedule
 * plus immediate passes when players join/leave or an admin runs a command.
 * Every template is rendered on the main thread (PlaceholderAPI requires it);
 * only the Discord calls run async.
 */
public final class DiscordStatdockPlugin extends JavaPlugin {

    private UptimeTracker uptime;
    private PluginConfig config;
    private DiscordRestClient discord;
    private StatdockEngine engine;
    private GatewayPresenceClient gateway;
    private RecordTracker record;
    private MaintenanceStore maintenance;
    private UnaryOperator<String> externalPlaceholders;
    private String resolvedIp = "unknown";
    private volatile ServerSnapshot lastSnapshot = new ServerSnapshot(0, 0, 20.0, 0, "", "");
    private volatile Map<String, String> renderedPresence = Map.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        uptime = new UptimeTracker();
        maintenance = new MaintenanceStore(getDataFolder(), getLogger());
        if (maintenance.isEnabled()) {
            getLogger().info("Maintenance mode is ON (kept from the last run). Use /statdock maintenance off to end it.");
        }
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
        externalPlaceholders = hookPlaceholderApi();

        if (!config.hasToken()) {
            getLogger().warning("No bot token configured. Set 'token' in config.yml and run /statdock reload. "
                    + "The plugin is loaded but will not update any channels until then.");
            engine = null;
            return;
        }

        resolvedIp = new IpResolver(config.ip(), getLogger()).resolve(getServer().getIp());
        record = new RecordTracker(config.record().enabled(), config.record().persist(), getDataFolder(), getLogger());
        discord = new DiscordRestClient(config.token(), getLogger());
        engine = new StatdockEngine(config, discord, uptime, record, maintenance, resolvedIp,
                externalPlaceholders, getLogger());

        sampleSnapshot();
        renderPresence(lastSnapshot);

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

    /** Returns the PlaceholderAPI bridge when it is installed and enabled in config, else null. */
    private UnaryOperator<String> hookPlaceholderApi() {
        if (!config.placeholderApi() || !PlaceholderApiHook.isAvailable(getServer())) {
            return null;
        }
        getLogger().info("Hooked into PlaceholderAPI: %placeholders% can be used in templates and presence messages.");
        return new PlaceholderApiHook(getLogger());
    }

    /**
     * Samples the server and renders every template on the main thread (where
     * PlaceholderAPI is safe to call), then runs the Discord edits async.
     */
    public void dispatchUpdate() {
        sampleSnapshot();
        StatdockEngine current = engine;
        if (current == null) {
            return;
        }
        ServerSnapshot snapshot = lastSnapshot;
        Map<StatdockEngine.ManagedChannel, String> names = current.render(snapshot);
        renderPresence(snapshot);
        getServer().getScheduler().runTaskAsynchronously(this, () -> current.tick(names));
    }

    /** Requests an immediate, out-of-cycle update (used by join/quit/commands). */
    public void requestImmediateUpdate() {
        if (engine != null) {
            engine.requestImmediate();
        }
        dispatchUpdate();
    }

    /** Must be called on the main thread: reads live Bukkit state into a snapshot. */
    public ServerSnapshot sampleSnapshot() {
        int online = getServer().getOnlinePlayers().size();
        int max = getServer().getMaxPlayers();
        double tps = safeTps();
        int port = getServer().getPort();
        String version = safeVersion();
        String players = getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        lastSnapshot = new ServerSnapshot(online, max, tps, port, version, players);
        return lastSnapshot;
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

    /**
     * Main thread: pre-renders every presence message, because the gateway
     * cycles them from its own thread where PlaceholderAPI can't be called.
     */
    private void renderPresence(ServerSnapshot s) {
        PluginConfig current = config;
        if (current == null || !current.presence().enabled()) {
            return;
        }
        PlaceholderResolver.Context ctx = presenceContext(s);
        Map<String, String> rendered = new HashMap<>();
        for (String message : current.presence().messages()) {
            rendered.put(message, PlaceholderResolver.resolve(message, ctx, externalPlaceholders));
        }
        renderedPresence = rendered;
    }

    /** Called from the gateway thread; serves the last main-thread render. */
    private String resolvePresence(String template) {
        String rendered = renderedPresence.get(template);
        return rendered != null ? rendered : PlaceholderResolver.resolve(template, presenceContext(lastSnapshot));
    }

    private PlaceholderResolver.Context presenceContext(ServerSnapshot s) {
        return new PlaceholderResolver.Context(
                s.online(), s.max(), uptime.uptimeSeconds(), s.tps(), resolvedIp, s.port(),
                s.version(), record == null ? 0 : record.get(), s.playersCsv());
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

    public boolean placeholderApiHooked() {
        return externalPlaceholders != null;
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
