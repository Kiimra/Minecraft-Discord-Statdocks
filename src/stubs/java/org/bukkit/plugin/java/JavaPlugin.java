package org.bukkit.plugin.java;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Compile-only stub. See org.bukkit.plugin.Plugin. Every method body here
 * throws, because the real Paper implementation always replaces this class at
 * runtime; these stubs exist only to satisfy the compiler when building
 * offline.
 */
public abstract class JavaPlugin implements Plugin {
    private static String stub() {
        throw new UnsupportedOperationException("Bukkit API stub - not present at runtime");
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public Logger getLogger() {
        throw new UnsupportedOperationException(stub());
    }

    public FileConfiguration getConfig() {
        throw new UnsupportedOperationException(stub());
    }

    public void saveDefaultConfig() {
        throw new UnsupportedOperationException(stub());
    }

    public void reloadConfig() {
        throw new UnsupportedOperationException(stub());
    }

    public void saveResource(String resourcePath, boolean replace) {
        throw new UnsupportedOperationException(stub());
    }

    public File getDataFolder() {
        throw new UnsupportedOperationException(stub());
    }

    public Server getServer() {
        throw new UnsupportedOperationException(stub());
    }

    public PluginCommand getCommand(String name) {
        throw new UnsupportedOperationException(stub());
    }
}
