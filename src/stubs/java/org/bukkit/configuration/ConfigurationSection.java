package org.bukkit.configuration;

import java.util.List;
import java.util.Set;

/** Compile-only stub. See org.bukkit.plugin.Plugin. */
public interface ConfigurationSection {
    boolean contains(String path);

    boolean isConfigurationSection(String path);

    ConfigurationSection getConfigurationSection(String path);

    Set<String> getKeys(boolean deep);

    String getString(String path);

    String getString(String path, String def);

    int getInt(String path, int def);

    long getLong(String path, long def);

    double getDouble(String path, double def);

    boolean getBoolean(String path, boolean def);

    List<String> getStringList(String path);
}
