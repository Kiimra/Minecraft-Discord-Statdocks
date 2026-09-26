package dev.kiimra.statdock.engine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Holds the maintenance flag and persists it in {@code maintenance.txt} inside
 * the plugin data folder, so a server restarted (or reloaded) while under
 * maintenance comes back still showing the maintenance template.
 */
public final class MaintenanceStore {

    private final File file;
    private final Logger logger;
    private volatile boolean enabled;

    public MaintenanceStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "maintenance.txt");
        this.logger = logger;
        this.enabled = readFromDisk();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public synchronized void set(boolean enabled) {
        this.enabled = enabled;
        writeToDisk(enabled);
    }

    private boolean readFromDisk() {
        try {
            if (file.isFile()) {
                return Boolean.parseBoolean(Files.readString(file.toPath(), StandardCharsets.UTF_8).trim());
            }
        } catch (Exception e) {
            logger.warning("Could not read persisted maintenance mode (" + e.getMessage() + "); assuming off.");
        }
        return false;
    }

    private void writeToDisk(boolean value) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(file.toPath(), Boolean.toString(value), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warning("Could not persist maintenance mode (" + e.getMessage()
                    + "); it will reset on the next restart.");
        }
    }
}
