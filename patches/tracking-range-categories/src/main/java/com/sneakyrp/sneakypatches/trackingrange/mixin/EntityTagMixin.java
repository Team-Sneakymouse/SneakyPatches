package com.sneakyrp.sneakypatches.trackingrange.mixin;

import com.sneakyrp.sneakypatches.trackingrange.TrackingRanges;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adding or removing a category tag on a tracked entity changes its range straight away. */
@Mixin(value = Entity.class, remap = false)
abstract class EntityTagMixin {
    @Inject(method = "addTag(Ljava/lang/String;)Z", at = @At("RETURN"), remap = false, require = 1)
    private void sneakyPatches$tagAdded(String tag, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) TrackingRanges.tagChanged((Entity) (Object) this, tag);
    }

    @Inject(method = "removeTag(Ljava/lang/String;)Z", at = @At("RETURN"), remap = false, require = 1)
    private void sneakyPatches$tagRemoved(String tag, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) TrackingRanges.tagChanged((Entity) (Object) this, tag);
    }
}
