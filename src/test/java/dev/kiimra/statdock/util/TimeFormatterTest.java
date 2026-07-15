package dev.kiimra.statdock.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimeFormatterTest {

    @Test
    void underOneHourShowsMinutesOnly() {
        assertEquals("0m", TimeFormatter.format(0));
        assertEquals("45m", TimeFormatter.format(45 * 60));
        assertEquals("59m", TimeFormatter.format(59 * 60 + 59));
    }

    @Test
    void underOneDayShowsHoursAndMinutes() {
        assertEquals("2h 10m", TimeFormatter.format(2 * 3600 + 10 * 60));
        assertEquals("1h 0m", TimeFormatter.format(3600));
    }

    @Test
    void aDayOrMoreShowsDaysAndHours() {
        // 215h 10m from the user's example collapses to days + hours.
        assertEquals("8d 23h", TimeFormatter.format(215L * 3600 + 10 * 60));
        assertEquals("1d 0h", TimeFormatter.format(86_400));
    }

    @Test
    void negativeIsClampedToZero() {
        assertEquals("0m", TimeFormatter.format(-5));
    }
}
