package com.sneakyrp.sneakypatches.trackingrange.mixin;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import com.sneakyrp.sneakypatches.trackingrange.AssignedRange;
import com.sneakyrp.sneakypatches.trackingrange.TrackingRanges;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A vehicle is tracked as far as its widest passenger. Paper recomputes each passenger's range from the server's
 * settings on every check (per player, per tick); use the range the passenger's own tracker already holds instead,
 * so a passenger's category counts and the per-check cost is a field read.
 *
 * <p>Also remembers the range the tracker was built with ({@link AssignedRange}).
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity", remap = false)
abstract class TrackedEntityMixin implements AssignedRange {
    @Unique
    private int sneakyPatches$assigned = -1;

    @Override
    public int sneakyPatches$assigned() {
        return this.sneakyPatches$assigned;
    }

    @Override
    public void sneakyPatches$setAssigned(int range) {
        this.sneakyPatches$assigned = range;
    }

    @Inject(method = "<init>(Lnet/minecraft/server/level/ChunkMap;Lnet/minecraft/world/entity/Entity;IIZ)V",
        at = @At("RETURN"), remap = false, require = 1)
    private void sneakyPatches$rememberRange(ChunkMap map, Entity entity, int range, int updateInterval,
                                             boolean trackDelta, CallbackInfo callback) {
        this.sneakyPatches$assigned = range;
    }

    @Redirect(
        method = "getEffectiveRange()I",
        at = @At(
            value = "INVOKE",
            target = "Lca/spottedleaf/moonrise/common/PlatformHooks;modifyEntityTrackingRange(Lnet/minecraft/world/entity/Entity;I)I",
            remap = false
        ),
        remap = false,
        require = 1,
        allow = 1
    )
    private int sneakyPatches$passengerRange(PlatformHooks hooks, Entity passenger, int defaultRange) {
        int tracked = TrackingRanges.passengerRange(passenger, -1);
        return tracked >= 0 ? tracked : hooks.modifyEntityTrackingRange(passenger, defaultRange);
    }
}
