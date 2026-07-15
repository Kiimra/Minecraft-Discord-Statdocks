package dev.kiimra.statdock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlaceholderResolverTest {

    private PlaceholderResolver.Context ctx(int online, double tps) {
        return new PlaceholderResolver.Context(online, 20, 2 * 3600 + 10 * 60, tps,
                "play.server.com", 25565, "1.21.8", 7, "Alice, Bob");
    }

    @Test
    void resolvesTheDefaultOnlineTemplate() {
        String out = PlaceholderResolver.resolve("🟢│Online {online}/{max} ({uptime})", ctx(1, 20.0));
        assertEquals("🟢│Online 1/20 (2h 10m)", out);
    }

    @Test
    void resolvesEveryPlaceholder() {
        String template = "{online} {max} {uptime} {tps} {ip} {port} {version} {record} {players}";
        assertEquals("3 20 2h 10m 18.3 play.server.com 25565 1.21.8 7 Alice, Bob",
                PlaceholderResolver.resolve(template, ctx(3, 18.34)));
    }

    @Test
    void tpsIsCappedAtTwenty() {
        assertEquals("20.0", PlaceholderResolver.resolve("{tps}", ctx(0, 21.5)));
    }

    @Test
    void emptyTemplateStaysEmpty() {
        assertEquals("", PlaceholderResolver.resolve("", ctx(0, 20.0)));
    }
}
