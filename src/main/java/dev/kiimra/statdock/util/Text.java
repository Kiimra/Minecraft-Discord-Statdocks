package dev.kiimra.statdock.util;

/** Small text helpers for console/command output. */
public final class Text {

    private static final char COLOR_CHAR = '§';
    private static final String CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

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
            if (chars[i] == '&' && CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * Removes section-sign colour/format codes (including {@code §x} hex
     * sequences), which Discord would otherwise show literally. Applied to
     * every rendered name, since PlaceholderAPI expansions often colour output.
     */
    public static String stripColor(String input) {
        if (input == null || input.indexOf(COLOR_CHAR) < 0) {
            return input == null ? "" : input;
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == COLOR_CHAR && i + 1 < input.length() && CODES.indexOf(input.charAt(i + 1)) > -1) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
