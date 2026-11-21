package com.sneakyrp.sneakypatches.idlesend.mixin;

import com.sneakyrp.sneakypatches.idlesend.IdleSend;
import com.sneakyrp.sneakypatches.idlesend.IdleSendSkip;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The one place the skip is decided: Paper's tracker loop, which already leaves the send out for an entity without
 * viewers outside entity-ticking chunks. Other callers of {@code sendChanges()} (plugins that run their own
 * {@code ServerEntity}, such as Citizens' packet trackers) are not touched.
 */
@Mixin(value = ChunkMap.class, remap = false)
abstract class TrackerLoopIdleMixin {
    @Redirect(method = "newTrackerTick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerEntity;sendChanges()V", remap = false),
        remap = false, require = 1, allow = 1)
    private void sneakyPatches$sendUnlessIdle(ServerEntity serverEntity) {
        if (IdleSendSkip.ENABLED && ((IdleSend) serverEntity).sneakyPatches$skipSend()) return;
        serverEntity.sendChanges();
    }
}
