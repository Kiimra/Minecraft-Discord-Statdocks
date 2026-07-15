package dev.kiimra.statdock.engine;

/**
 * A point-in-time sample of server stats, taken on the main thread and handed
 * to the (async) engine so it never touches Bukkit state off-thread.
 */
public record ServerSnapshot(int online, int max, double tps, int port, String version, String playersCsv) {
}
