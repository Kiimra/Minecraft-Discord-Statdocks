package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/** Compile-only stub. See org.bukkit.plugin.Plugin. */
public class PlayerJoinEvent extends Event {
    private final Player player;

    public PlayerJoinEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}
