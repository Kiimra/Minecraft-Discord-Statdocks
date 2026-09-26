package dev.kiimra.statdock.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void stripsLegacyAndHexColourCodes() {
        assertEquals("Online 5", Text.stripColor("§aOnline §l§x§1§2§3§4§5§65"));
    }

    @Test
    void keepsTextWithoutColourCodes() {
        assertEquals("🟢│Tom & Jerry 100%", Text.stripColor("🟢│Tom & Jerry 100%"));
    }

    @Test
    void keepsALoneTrailingSectionSign() {
        assertEquals("end§", Text.stripColor("end§"));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", Text.stripColor(null));
    }
}
