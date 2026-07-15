package org.bukkit;

import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

/** Compile-only stub. See org.bukkit.plugin.Plugin. */
public interface Server {
    Collection<? extends Player> getOnlinePlayers();

    int getMaxPlayers();

    BukkitScheduler getScheduler();

    PluginManager getPluginManager();

    double[] getTPS();

    String getIp();

    int getPort();

    String getVersion();

    String getMinecraftVersion();

    String getName();
}
