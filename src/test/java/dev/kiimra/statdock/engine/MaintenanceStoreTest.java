package dev.kiimra.statdock.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaintenanceStoreTest {

    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir
    Path dataFolder;

    private MaintenanceStore newStore() {
        return new MaintenanceStore(dataFolder.toFile(), LOGGER);
    }

    @Test
    void offByDefault() {
        assertFalse(newStore().isEnabled());
    }

    @Test
    void survivesARestart() {
        newStore().set(true);
        assertTrue(newStore().isEnabled());

        newStore().set(false);
        assertFalse(newStore().isEnabled());
    }

    @Test
    void createsTheDataFolderWhenMissing() {
        File nested = dataFolder.resolve("plugins/Statdock").toFile();
        new MaintenanceStore(nested, LOGGER).set(true);
        assertTrue(new MaintenanceStore(nested, LOGGER).isEnabled());
    }

    @Test
    void unreadableContentMeansOff() throws Exception {
        Files.writeString(dataFolder.resolve("maintenance.txt"), "garbage");
        assertFalse(newStore().isEnabled());
    }
}
