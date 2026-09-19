package com.sneakyrp.sneakypatches.packs.mixin;

import com.sneakyrp.sneakypatches.packs.Compatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.velocitypowered.proxy.Velocity", remap = false)
public abstract class VelocityStartupMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"))
    private static void sneakypatches$verify(String[] args, CallbackInfo ci) { Compatibility.verify(); }
}
