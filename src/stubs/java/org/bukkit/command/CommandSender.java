package org.bukkit.command;

/** Compile-only stub. See org.bukkit.plugin.Plugin. */
public interface CommandSender {
    void sendMessage(String message);

    boolean hasPermission(String permission);

    String getName();
}
