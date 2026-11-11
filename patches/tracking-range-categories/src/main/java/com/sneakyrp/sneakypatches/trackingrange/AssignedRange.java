package com.sneakyrp.sneakypatches.trackingrange;

/**
 * Added to ChunkMap$TrackedEntity: the range this patch (or Spigot's settings) last gave the tracker. When the stored
 * range differs, another plugin changed it by reflection and automatic updates leave it alone.
 */
public interface AssignedRange {
    int sneakyPatches$assigned();

    void sneakyPatches$setAssigned(int range);
}
