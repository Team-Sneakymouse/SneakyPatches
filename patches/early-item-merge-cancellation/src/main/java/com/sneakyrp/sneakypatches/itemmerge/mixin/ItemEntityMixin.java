package com.sneakyrp.sneakypatches.itemmerge.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemEntity.class, remap = false)
abstract class ItemEntityMixin {
    @Inject(
        method = "mergeWithNeighbours()V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 1,
        allow = 1
    )
    private void sneakyPatches$preventItemMerging(CallbackInfo callback) {
        callback.cancel();
    }
}
