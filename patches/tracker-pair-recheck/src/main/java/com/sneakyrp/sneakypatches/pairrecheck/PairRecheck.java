package com.sneakyrp.sneakypatches.pairrecheck;

/**
 * Settings and counters for re-checking a still entity's players only every few ticks.
 *
 * <p>Paper re-checks every entity against every candidate player every tick. Most entities on a server (ground
 * items, displays, furniture) never move, so for them a pair can only change when the player moves, a chunk finishes
 * sending, or a plugin hides or shows the entity. Hide and show already update the tracker immediately (Paper's API
 * does that itself), so for the rest a short delay is the only cost: an entity that is still is checked once every
 * {@link #INTERVAL} ticks, spread across ticks by entity id. An entity that moved since its last check is checked
 * that tick, as is any entity just added to the tracker.
 */
public final class PairRecheck {
    /**
     * Ticks between checks of an entity that has not moved; {@code -Dsneakypatches.trackerRecheckTicks=N}. 1 or less
     * turns the patch off: every entity is checked every tick, as in Paper.
     */
    public static final int INTERVAL = Integer.getInteger("sneakypatches.trackerRecheckTicks", 5);
    /** Entity tracker ticks skipped, and run (server thread only; for diagnostics). */
    public static long skipped, checked;

    static {
        // Loaded with the first tracker tick: says once, in the startup log, what is in force.
        com.mojang.logging.LogUtils.getLogger().info(INTERVAL > 1
            ? "[SneakyPatches] Tracker pair re-check: still entities are checked every {} ticks"
            : "[SneakyPatches] Tracker pair re-check: off (sneakypatches.trackerRecheckTicks={})", INTERVAL);
    }

    private PairRecheck() {
    }

    /** Whether a still entity with this id is checked on this server tick. */
    public static boolean due(int tick, int entityId) {
        return INTERVAL <= 1 || Math.floorMod(tick + entityId, INTERVAL) == 0;
    }
}
