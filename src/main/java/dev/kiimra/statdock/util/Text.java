package dev.kiimra.statdock.util;

/** Small text helpers for console/command output. */
public final class Text {

    private static final char COLOR_CHAR = '§';

    private Text() {
    }

    /**
     * Translates {@code &}-prefixed colour codes into the section-sign codes
     * Minecraft chat expects, so config authors can write {@code &a} etc.
     */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) > -1) {
                chars[i] = COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }
}
