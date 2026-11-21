package com.sneakyrp.sneakypatches.idlesend;

/**
 * Setting and counters for skipping the tracker's per-tick send for entities nobody is watching.
 *
 * <p>Paper calls {@code ServerEntity.sendChanges()} every tick for every tracked entity that has a viewer or stands
 * in an entity-ticking chunk. With no viewer the call sends nothing: it only compares the entity's position, rotation,
 * passengers and metadata with what was last sent. In force-loaded areas that is most of the tracker's send time. The
 * patch skips the call while a non-player entity has no viewer, and brings the last-sent state up to date when its
 * first viewer arrives.
 */
public final class IdleSendSkip {
    /** {@code -Dsneakypatches.idleSendSkip=false} turns the patch off: every entity is sent as in Paper. */
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("sneakypatches.idleSendSkip", "true"));
    /** Sends skipped, and last-sent states brought up to date for a first viewer (server thread only; diagnostics). */
    public static long skipped, refreshed;

    static {
        // Loaded with the first tracker tick: says once, in the startup log, what is in force.
        com.mojang.logging.LogUtils.getLogger().info(ENABLED
            ? "[SneakyPatches] Idle send skip: entities without viewers are not checked for changes"
            : "[SneakyPatches] Idle send skip: off (sneakypatches.idleSendSkip=false)");
    }

    private IdleSendSkip() {
    }
}
