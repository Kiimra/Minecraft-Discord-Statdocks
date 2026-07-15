package dev.kiimra.statdock.util;

/**
 * Formats an uptime duration (in seconds) into a compact, human friendly
 * string. The format escalates with magnitude so it stays readable no matter
 * how long the server has been up:
 *
 * <ul>
 *   <li>less than an hour: {@code "45m"}</li>
 *   <li>less than a day:   {@code "2h 10m"}</li>
 *   <li>a day or more:     {@code "8d 23h"}</li>
 * </ul>
 */
public final class TimeFormatter {

    private TimeFormatter() {
    }

    public static String format(long totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
