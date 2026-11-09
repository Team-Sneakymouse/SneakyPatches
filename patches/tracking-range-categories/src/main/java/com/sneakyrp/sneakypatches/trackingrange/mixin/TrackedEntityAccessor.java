package com.sneakyrp.sneakypatches.trackingrange.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tracker's range in blocks, before entity-broadcast-range-percentage and passengers. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity", remap = false)
public interface TrackedEntityAccessor {
    @Accessor(value = "range", remap = false)
    int sneakyPatches$range();

    @Mutable
    @Accessor(value = "range", remap = false)
    void sneakyPatches$setRange(int range);
}
