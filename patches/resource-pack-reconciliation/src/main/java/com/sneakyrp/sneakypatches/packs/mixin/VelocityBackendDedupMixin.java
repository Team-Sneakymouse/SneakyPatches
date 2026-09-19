package com.sneakyrp.sneakypatches.packs.mixin;

import com.velocitypowered.proxy.connection.player.resourcepack.handler.ResourcePackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Velocity normally filters an unchanged backend pack before CE Proxy's raw
 * packet listener can observe it. Reconciliation needs the complete ordered
 * destination list, so delegate backend deduplication to CE Proxy. Calls from
 * proxy plugins and the public Player API are unaffected.
 */
@Mixin(targets = {
    "com.velocitypowered.proxy.connection.backend.ConfigSessionHandler",
    "com.velocitypowered.proxy.connection.backend.BackendPlaySessionHandler"
}, remap = false)
public abstract class VelocityBackendDedupMixin {
    @Redirect(
        // Velocity performs the check in the async event callback generated
        // from handle(ResourcePackRequestPacket), not in handle itself.
        method = "lambda$handle$0",
        at = @At(
            value = "INVOKE",
            target = "Lcom/velocitypowered/proxy/connection/player/resourcepack/handler/ResourcePackHandler;hasPackAppliedByHash([B)Z"
        ),
        require = 1,
        allow = 1
    )
    private boolean sneakypatches$delegateBackendDeduplication(
        ResourcePackHandler handler,
        byte[] hash
    ) {
        return false;
    }
}
