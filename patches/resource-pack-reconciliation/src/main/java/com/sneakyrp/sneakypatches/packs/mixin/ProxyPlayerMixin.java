package com.sneakyrp.sneakypatches.packs.mixin;

import com.sneakyrp.sneakypatches.packs.OrderedPlayer;
import com.sneakyrp.sneakypatches.packs.PackOrder;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ProxyPlayer.class, remap = false)
public abstract class ProxyPlayerMixin implements OrderedPlayer {
    @Unique private final PackOrder sneakypatches$order = new PackOrder();
    @Override public PackOrder sneakypatches$order() { return sneakypatches$order; }
}
