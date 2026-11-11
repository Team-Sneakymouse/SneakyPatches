package com.sneakyrp.sneakypatches.trackingrange.mixin;

import com.sneakyrp.sneakypatches.trackingrange.TrackingRanges;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A display's view range can change after it is tracked (Bukkit Display#setViewRange). */
@Mixin(value = Display.class, remap = false)
abstract class DisplayViewRangeMixin {
    @Inject(method = "setViewRange(F)V", at = @At("TAIL"), remap = false, require = 1)
    private void sneakyPatches$viewRangeChanged(float range, CallbackInfo callback) {
        TrackingRanges.viewRangeChanged((Display) (Object) this);
    }
}
