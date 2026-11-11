package com.sneakyrp.sneakypatches.trackingrange.mixin;

import com.sneakyrp.sneakypatches.trackingrange.TrackingRangesCommand;
import io.papermc.paper.command.PaperCommands;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers /trackingranges right after Paper registers /paper, the same way. */
@Mixin(value = PaperCommands.class, remap = false)
abstract class PaperCommandsMixin {
    @Inject(
        method = "registerCommands(Lnet/minecraft/server/MinecraftServer;)V",
        at = @At("RETURN"),
        remap = false,
        require = 1
    )
    private static void sneakyPatches$registerTrackingRanges(MinecraftServer server, CallbackInfo callback) {
        TrackingRangesCommand.register(server);
    }
}
