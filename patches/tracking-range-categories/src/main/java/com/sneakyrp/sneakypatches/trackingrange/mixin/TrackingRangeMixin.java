package com.sneakyrp.sneakypatches.trackingrange.mixin;

import com.sneakyrp.sneakypatches.trackingrange.TrackingRanges;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spigotmc.SpigotWorldConfig;
import org.spigotmc.TrackingRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every range Spigot's settings give an entity passes through here. Spigot still decides which spigot.yml type an
 * entity belongs to; each type's single read of its spigot.yml value goes through the {@code @default.<type>}
 * overrides, and the result, at each of the method's returns, through the categories and the display limit.
 */
@Mixin(value = TrackingRange.class, remap = false)
abstract class TrackingRangeMixin {
    private static final String METHOD = "getEntityTrackingRange(Lnet/minecraft/world/entity/Entity;I)I";

    @Inject(method = METHOD, at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static void sneakyPatches$applyCategory(Entity entity, int defaultRange,
                                                    CallbackInfoReturnable<Integer> callback) {
        int computed = callback.getReturnValueI();
        int range = TrackingRanges.adjust(entity, computed);
        if (range != computed) callback.setReturnValue(range);
    }

    @Redirect(method = METHOD, remap = false, require = 1, allow = 1, at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lorg/spigotmc/SpigotWorldConfig;playerTrackingRange:I", remap = false))
    private static int sneakyPatches$players(SpigotWorldConfig config) {
        return TrackingRanges.typeDefault("players", config.playerTrackingRange);
    }

    @Redirect(method = METHOD, remap = false, require = 1, allow = 1, at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lorg/spigotmc/SpigotWorldConfig;animalTrackingRange:I", remap = false))
    private static int sneakyPatches$animals(SpigotWorldConfig config) {
        return TrackingRanges.typeDefault("animals", config.animalTrackingRange);
    }

    @Redirect(method = METHOD, remap = false, require = 1, allow = 1, at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lorg/spigotmc/SpigotWorldConfig;monsterTrackingRange:I", remap = false))
    private static int sneakyPatches$monsters(SpigotWorldConfig config) {
        return TrackingRanges.typeDefault("monsters", config.monsterTrackingRange);
    }

    @Redirect(method = METHOD, remap = false, require = 1, allow = 1, at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lorg/spigotmc/SpigotWorldConfig;miscTrackingRange:I", remap = false))
    private static int sneakyPatches$misc(SpigotWorldConfig config) {
        return TrackingRanges.typeDefault("misc", config.miscTrackingRange);
    }

    @Redirect(method = METHOD, remap = false, require = 1, allow = 1, at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lorg/spigotmc/SpigotWorldConfig;displayTrackingRange:I", remap = false))
    private static int sneakyPatches$display(SpigotWorldConfig config) {
        return TrackingRanges.typeDefault("display", config.displayTrackingRange);
    }

    @Redirect(method = METHOD, remap = false, require = 1, allow = 1, at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lorg/spigotmc/SpigotWorldConfig;otherTrackingRange:I", remap = false))
    private static int sneakyPatches$other(SpigotWorldConfig config) {
        return TrackingRanges.typeDefault("other", config.otherTrackingRange);
    }
}
