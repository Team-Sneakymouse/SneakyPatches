package com.sneakyrp.sneakypatches.pairrecheck.mixin;

import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import com.sneakyrp.sneakypatches.pairrecheck.PairRecheck;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips an entity's tracker tick (the check of every candidate player) while the entity is still, its tracker's range
 * is unchanged, and its turn has not come; see {@link PairRecheck}. Only the player checks are skipped: Paper still sends the entity's changes to
 * its viewers every tick. Players are always checked. A skipped tick changes nothing, so the next full tick does all
 * the work Paper would have, including dropping players who left the candidate list in between.
 *
 * <p>Priority 900 puts this ahead of other patches that replace the same tick (such as the range-sized lookup), so a
 * skip skips them too.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity", remap = false, priority = 900)
abstract class TrackedEntityRecheckMixin {
    @Shadow(remap = false)
    @Final
    private Entity entity;
    /**
     * The tracker's own range. Final in Paper; patches that change ranges at runtime (tracking-range-categories:
     * tag changes, /trackingranges reload) rewrite it, and a change must not wait for the entity's turn.
     */
    @Shadow(remap = false)
    @Final
    private int range;

    @Unique
    private double sneakyPatches$checkedX = Double.NaN;
    @Unique
    private double sneakyPatches$checkedY = Double.NaN;
    @Unique
    private double sneakyPatches$checkedZ = Double.NaN;
    @Unique
    private int sneakyPatches$checkedRange = -1;

    @Inject(method = "moonrise$tick(Lca/spottedleaf/moonrise/common/misc/NearbyPlayers$TrackedChunk;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void sneakyPatches$skipStill(NearbyPlayers.TrackedChunk chunk, CallbackInfo ci) {
        // No chunk: Paper clears the players, which must not wait.
        if (PairRecheck.INTERVAL <= 1 || chunk == null || entity instanceof ServerPlayer) return;
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        // NaN never equals itself, so a new tracker's first tick is always a check.
        boolean still = x == sneakyPatches$checkedX && y == sneakyPatches$checkedY && z == sneakyPatches$checkedZ
            && range == sneakyPatches$checkedRange;
        if (still && !PairRecheck.due(MinecraftServer.currentTick, entity.getId())) {
            PairRecheck.skipped++;
            ci.cancel();
            return;
        }
        sneakyPatches$checkedX = x;
        sneakyPatches$checkedY = y;
        sneakyPatches$checkedZ = z;
        sneakyPatches$checkedRange = range;
        PairRecheck.checked++;
    }
}
