package dev.kiimra.statdock.engine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Tracks the peak simultaneous player count for the {@code {record}}
 * placeholder. Disabled by default; when disabled the record stays at zero.
 * When {@code persist} is enabled the peak is stored in {@code record.txt}
 * inside the plugin data folder and survives restarts.
 */
public final class RecordTracker {

    private final boolean enabled;
    private final boolean persist;
    private final File file;
    private final Logger logger;
    private volatile int record;

    public RecordTracker(boolean enabled, boolean persist, File dataFolder, Logger logger) {
        this.enabled = enabled;
        this.persist = persist;
        this.file = new File(dataFolder, "record.txt");
        this.logger = logger;
        if (enabled && persist) {
            this.record = readFromDisk();
        }
    }

    /** Updates the peak if needed. Returns true when a new record was set. */
    public boolean update(int currentOnline) {
        if (!enabled) {
            return false;
        }
        if (currentOnline > record) {
            record = currentOnline;
            if (persist) {
                writeToDisk(record);
            }
            return true;
        }
        return false;
    }

    public int get() {
        return record;
    }

    private int readFromDisk() {
        try {
            if (file.isFile()) {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return Math.max(0, Integer.parseInt(content));
                }
            }
        } catch (Exception e) {
            logger.warning("Could not read persisted record (" + e.getMessage() + "); starting from 0.");
        }
        return 0;
    }

    private void writeToDisk(int value) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(file.toPath(), Integer.toString(value), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warning("Could not persist record (" + e.getMessage() + ").");
        }
    }
}
