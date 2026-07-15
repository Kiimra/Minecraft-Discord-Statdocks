package org.bukkit.command;

/** Compile-only stub. See org.bukkit.plugin.Plugin. */
public interface PluginCommand extends Command {
    void setExecutor(CommandExecutor executor);

    void setTabCompleter(TabCompleter completer);
}
