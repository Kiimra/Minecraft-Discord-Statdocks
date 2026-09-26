package dev.kiimra.statdock.engine;

import java.util.function.UnaryOperator;
import dev.kiimra.statdock.util.Text;
import dev.kiimra.statdock.util.TimeFormatter;

/**
 * Replaces {@code {placeholder}} tokens in a template with live server values.
 * Supported tokens: {@code {online} {max} {uptime} {tps} {ip} {port}
 * {version} {record} {players}}. External {@code %placeholders%} (e.g. from
 * PlaceholderAPI) are handled by {@link #resolve(String, Context, UnaryOperator)}.
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

    /**
     * Resolves the built-in tokens first (so they can be nested inside external
     * placeholders, e.g. {@code %math_0_{max}-{online}%}), then hands the result
     * to {@code external} and strips colour codes, which Discord can't show.
     * When {@code external} touches Bukkit (PlaceholderAPI does), call this on
     * the main thread.
     */
    public static String resolve(String template, Context ctx, UnaryOperator<String> external) {
        String text = resolve(template, ctx);
        if (external != null && text.indexOf('%') >= 0) {
            text = external.apply(text);
        }
        return Text.stripColor(text);
    }
}
