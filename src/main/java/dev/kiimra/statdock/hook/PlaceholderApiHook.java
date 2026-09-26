package dev.kiimra.statdock.hook;

import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/**
 * Bridge to PlaceholderAPI. Only instantiated after {@link #isAvailable}
 * confirms PlaceholderAPI is enabled, so its classes are never touched when it
 * is not installed. Placeholders are parsed without a player, so server-wide
 * ones (e.g. {@code %server_online%}) resolve while player-specific ones are
 * left as-is. PlaceholderAPI is not thread-safe: call {@link #apply} on the
 * main thread only.
 */
public final class PlaceholderApiHook implements UnaryOperator<String> {

    public static final String PLUGIN_NAME = "PlaceholderAPI";

    private final Logger logger;
    private boolean warned;

    public PlaceholderApiHook(Logger logger) {
        this.logger = logger;
    }

    public static boolean isAvailable(Server server) {
        Plugin plugin = server.getPluginManager().getPlugin(PLUGIN_NAME);
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public String apply(String text) {
        try {
            return PlaceholderAPI.setPlaceholders((OfflinePlayer) null, text);
        } catch (Throwable t) {
            // A broken expansion must never stop the statdock; show the raw text instead.
            if (!warned) {
                warned = true;
                logger.warning("PlaceholderAPI failed to parse \"" + text + "\" (" + t
                        + "); leaving placeholders unparsed. Further errors are not logged.");
            }
            return text;
        }
    }
}
