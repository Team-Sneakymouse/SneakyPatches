package com.sneakyrp.sneakypatches.rangelookup;

/**
 * Setting for the tracker's candidate player lookup; {@link PlayerList} chooses the list.
 *
 * <p>Paper checks every tracked entity against every player whose view distance covers the entity's chunk, whatever
 * the entity's tracking range. Moonrise also keeps, per chunk, the players within 3 chunks and within 10 chunks. The
 * patch reads the smallest of those lists that still holds every player within the entity's range.
 */
public final class RangeSizedLookup {
    /** {@code -Dsneakypatches.rangeSizedLookup=false} turns the patch off: Paper's view-distance list is used. */
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("sneakypatches.rangeSizedLookup", "true"));

    static {
        // Loaded with the first tracker tick: says once, in the startup log, what is in force.
        com.mojang.logging.LogUtils.getLogger().info(ENABLED
            ? "[SneakyPatches] Range-sized lookup: entities are checked against the players near their range"
            : "[SneakyPatches] Range-sized lookup: off (sneakypatches.rangeSizedLookup=false)");
    }

    private RangeSizedLookup() {
    }
}
