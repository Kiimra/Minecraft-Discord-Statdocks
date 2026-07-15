package org.bukkit.command;

import java.util.List;

/** Compile-only stub. See org.bukkit.plugin.Plugin. */
public interface TabCompleter {
    List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args);
}
