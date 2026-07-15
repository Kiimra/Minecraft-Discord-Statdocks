package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/** Compile-only stub. See org.bukkit.plugin.Plugin. */
public interface BukkitScheduler {
    BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period);

    BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period);

    BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task);

    BukkitTask runTask(Plugin plugin, Runnable task);

    BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay);

    void cancelTasks(Plugin plugin);
}
