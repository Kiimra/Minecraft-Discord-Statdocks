package dev.kiimra.statdock.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import dev.kiimra.statdock.DiscordStatdockPlugin;
import dev.kiimra.statdock.config.PluginConfig;
import dev.kiimra.statdock.engine.StatdockEngine;
import dev.kiimra.statdock.util.TimeFormatter;
import dev.kiimra.statdock.util.Text;

/** Handles {@code /statdock reload|forceupdate|status|maintenance|help}. */
public final class StatdockCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "statdock.admin";
    private static final List<String> SUBCOMMANDS =
            List.of("reload", "forceupdate", "status", "maintenance", "help");

    private final DiscordStatdockPlugin plugin;

    public StatdockCommand(DiscordStatdockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "forceupdate" -> handleForceUpdate(sender);
            case "status" -> handleStatus(sender);
            case "maintenance" -> handleMaintenance(sender, args);
            case "help" -> sendHelp(sender, label);
            default -> send(sender, msg("unknown-subcommand"));
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reload();
            send(sender, msg("reload-success"));
        } catch (Exception e) {
            send(sender, msg("reload-fail"));
            plugin.getLogger().warning("Reload failed: " + e.getMessage());
        }
    }

    private void handleForceUpdate(CommandSender sender) {
        if (plugin.engine() == null) {
            send(sender, "&cNo bot token configured - nothing to update.");
            return;
        }
        plugin.requestImmediateUpdate();
        send(sender, msg("force-update"));
    }

    private void handleMaintenance(CommandSender sender, String[] args) {
        StatdockEngine engine = plugin.engine();
        if (engine == null) {
            send(sender, "&cNo bot token configured - maintenance mode is unavailable.");
            return;
        }
        if (args.length < 2) {
            send(sender, msg("maintenance-usage") + " &7(currently "
                    + (engine.isMaintenance() ? "&aON" : "&cOFF") + "&7)");
            return;
        }
        String value = args[1].toLowerCase(Locale.ROOT);
        if (value.equals("on") || value.equals("true") || value.equals("enable")) {
            engine.setMaintenance(true);
            send(sender, msg("maintenance-on"));
        } else if (value.equals("off") || value.equals("false") || value.equals("disable")) {
            engine.setMaintenance(false);
            send(sender, msg("maintenance-off"));
        } else {
            send(sender, msg("maintenance-usage"));
        }
    }

    private void handleStatus(CommandSender sender) {
        PluginConfig config = plugin.config();
        StatdockEngine engine = plugin.engine();
        send(sender, "&bDiscordStatdockUpdater status");
        if (config == null || engine == null) {
            send(sender, "&7Token configured: &cno &7- set it in config.yml and run /statdock reload.");
            return;
        }
        send(sender, "&7Uptime: &f" + TimeFormatter.format(plugin.uptime().uptimeSeconds()));
        send(sender, "&7Resolved IP: &f" + plugin.resolvedIp());
        send(sender, "&7Maintenance: " + (engine.isMaintenance() ? "&aON" : "&7off"));
        send(sender, "&7Presence: " + (config.presence().enabled() ? "&aenabled" : "&7disabled"));
        send(sender, "&7Channels (&f" + engine.channels().size() + "&7):");
        long now = System.currentTimeMillis();
        for (StatdockEngine.ManagedChannel mc : engine.channels()) {
            String current = mc.lastAppliedName() == null ? "&8(not set yet)" : "&f" + mc.lastAppliedName();
            String suffix;
            if (mc.skipped()) {
                suffix = " &c[skipped]";
            } else {
                long wait = mc.millisUntilNextAllowed(now);
                suffix = wait > 0 ? " &8(next edit in " + (wait / 1000) + "s)" : "";
            }
            send(sender, "  &7- &b" + mc.name() + "&7: " + current + suffix);
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        send(sender, "&bDiscordStatdockUpdater &7commands:");
        send(sender, "&f/" + label + " reload &7- reload config, channels and token");
        send(sender, "&f/" + label + " forceupdate &7- update all channels now");
        send(sender, "&f/" + label + " status &7- show connection and channel status");
        send(sender, "&f/" + label + " maintenance <on|off> &7- toggle maintenance mode");
    }

    private String msg(String key) {
        return plugin.config() == null ? "" : plugin.config().message(key);
    }

    private void send(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String prefix = plugin.config() == null ? "" : plugin.config().message("prefix");
        sender.sendMessage(Text.color(prefix + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
            return filter(List.of("on", "off"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
