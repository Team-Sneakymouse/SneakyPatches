package com.sneakyrp.sneakypatches.rangelookup;

/** Which of a chunk's player lists covers a tracking range. */
public final class PlayerList {
    /** Moonrise's list radii in blocks ({@code NearbyPlayers.GENERAL_*_AREA_VIEW_DISTANCE_BLOCKS}; PaperTargetTest). */
    public static final int REALLY_SMALL_BLOCKS = 48, SMALL_BLOCKS = 160;
    public static final int REALLY_SMALL = 0, SMALL = 1, VIEW_DISTANCE = 2;

    private PlayerList() {
    }

    /**
     * The list for an effective tracking range in blocks. A player within {@code r} blocks of an entity on each axis
     * is at most {@code r / 16} chunks from the entity's chunk on each axis when {@code r} is a multiple of 16, so the
     * 3-chunk list covers 48 blocks and the 10-chunk list 160.
     */
    public static int listFor(int rangeBlocks) {
        if (rangeBlocks <= REALLY_SMALL_BLOCKS) return REALLY_SMALL;
        return rangeBlocks <= SMALL_BLOCKS ? SMALL : VIEW_DISTANCE;
    }
}
