package com.sneakyrp.sneakypatches.packs.mixin;

import com.sneakyrp.sneakypatches.packs.PackHooks;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketContext;
import net.momirealms.craftengine.proxy.common.network.listener.common.ResourcePackRemoveListener;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackSession;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackRemoval;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ResourcePackRemoveListener.class, remap = false)
public abstract class PackRemoveMixin {
    @Redirect(method = "handle", at = @At(value = "INVOKE", target = "Lnet/momirealms/craftengine/proxy/common/network/resourcepack/ResourcePackSession;commitRemove(Lnet/momirealms/craftengine/proxy/common/network/resourcepack/ResourcePackRemoval;)V"))
    private void sneakypatches$removed(ResourcePackSession session, ResourcePackRemoval removal,
            ChannelConnection connection, ProxyPlayer player, PacketContext packet) {
        session.commitRemove(removal);
        PackHooks.order(player).remove(removal.forwardedId());
    }
}
