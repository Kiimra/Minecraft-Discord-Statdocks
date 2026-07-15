package dev.kiimra.statdock.config;

import java.util.Map;
import dev.kiimra.statdock.engine.ServerState;

/**
 * A single configured statdock. {@code name} is only used for logs/status;
 * the channel is always addressed by its Discord {@code id}. {@code templates}
 * holds per-state overrides; any state missing here falls back to the global
 * defaults (see {@link PluginConfig#template}).
 */
public record ChannelConfig(String name, String id, int intervalSeconds, Map<ServerState, String> templates) {
}
