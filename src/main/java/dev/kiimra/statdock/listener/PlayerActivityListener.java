package dev.kiimra.statdock.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import dev.kiimra.statdock.DiscordStatdockPlugin;

/**
 * Triggers an immediate statdock update when the player count changes, so the
 * 🟢/🌙 transition doesn't wait for the next scheduled cycle. The update is
 * scheduled one tick later so the online count already reflects the join/quit
 * (on quit the player is still listed until the tick completes).
 */
public final class PlayerActivityListener implements Listener {

    private final DiscordStatdockPlugin plugin;

    public PlayerActivityListener(DiscordStatdockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleUpdate();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        scheduleUpdate();
    }

    private void scheduleUpdate() {
        plugin.getServer().getScheduler().runTaskLater(plugin, plugin::requestImmediateUpdate, 1L);
    }
}
