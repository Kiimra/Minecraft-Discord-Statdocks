package dev.kiimra.statdock.engine;

import dev.kiimra.statdock.util.TimeFormatter;

/**
 * Replaces {@code {placeholder}} tokens in a template with live server values.
 * Supported tokens: {@code {online} {max} {uptime} {tps} {ip} {port}
 * {version} {record} {players}}.
 */
public final class PlaceholderResolver {

    private PlaceholderResolver() {
    }

    /** Immutable snapshot of the values a template can reference. */
    public record Context(int online, int max, long uptimeSeconds, double tps, String ip, int port,
                          String version, int record, String players) {
    }

    public static String resolve(String template, Context ctx) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String tps = String.format(java.util.Locale.ROOT, "%.1f", Math.min(ctx.tps(), 20.0));
        return template
                .replace("{online}", Integer.toString(ctx.online()))
                .replace("{max}", Integer.toString(ctx.max()))
                .replace("{uptime}", TimeFormatter.format(ctx.uptimeSeconds()))
                .replace("{tps}", tps)
                .replace("{ip}", ctx.ip() == null ? "" : ctx.ip())
                .replace("{port}", Integer.toString(ctx.port()))
                .replace("{version}", ctx.version() == null ? "" : ctx.version())
                .replace("{record}", Integer.toString(ctx.record()))
                .replace("{players}", ctx.players() == null ? "" : ctx.players());
    }
}
