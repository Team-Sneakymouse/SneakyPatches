package com.sneakyrp.sneakypatches.trackingrange;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The class's first use loads the file. Regression: the template was null during static initialisation, so a
 * missing file was never written and the error was logged instead (found by the lab suite).
 */
class TrackingRangesConfigTest {
    @TempDir
    static Path dir;

    @Test
    void missingFileGetsTheTemplateWhichChangesNothingForUntaggedEntities() throws Exception {
        Path file = dir.resolve("nested").resolve("tracking-ranges.properties");
        System.setProperty(TrackingRanges.CONFIG_PROPERTY, file.toString());
        try {
            var loaded = TrackingRanges.categories();
            assertTrue(Files.exists(file), "template not written");
            assertTrue(Files.readString(file).contains("tracking.<name>"));
            var parsed = RangeCategories.parse(Files.readAllLines(file));
            assertTrue(parsed.problems().isEmpty(), String.join("\n", parsed.problems()));
            assertEquals(java.util.Map.of("short", 32, "medium", 64, "long", 128, "extreme", 256), parsed.ranges());
            assertEquals(parsed.ranges(), loaded.ranges());
            // Every type line is present and says 'spigot': no override, spigot.yml applies.
            for (String type : RangeCategories.TYPES) {
                assertTrue(Files.readAllLines(file).contains(RangeCategories.DEFAULT_PREFIX + type + " = spigot"), type);
                assertEquals(77, parsed.typeDefault(type, 77), type);
            }
            assertTrue(parsed.typeDefaults().isEmpty());
            assertEquals(0, parsed.displayViewScale());
        } finally {
            System.clearProperty(TrackingRanges.CONFIG_PROPERTY);
        }
    }
}
