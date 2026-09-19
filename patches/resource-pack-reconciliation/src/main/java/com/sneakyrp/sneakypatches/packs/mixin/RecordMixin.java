package com.sneakyrp.sneakypatches.packs.mixin;

import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackRecord", remap = false)
public interface RecordMixin {
    @Accessor("clientPackId") UUID sneakypatches$clientId();
}
