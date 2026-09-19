package com.sneakyrp.sneakypatches.packs.mixin;

import com.sneakyrp.sneakypatches.packs.PackHooks;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketContext;
import net.momirealms.craftengine.proxy.common.network.listener.common.ResourcePackSendListener;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ResourcePackSendListener.class, remap = false)
public abstract class PackSendMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void sneakypatches$before(ChannelConnection connection, ProxyPlayer player, PacketContext packet, CallbackInfo ci) {
        if (player != null && packet.clientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_3))
            PackHooks.guarded(connection, () -> PackHooks.beforeSend(connection, player, packet));
    }
    @Inject(method = "handle", at = @At("RETURN"))
    private void sneakypatches$after(ChannelConnection connection, ProxyPlayer player, PacketContext packet, CallbackInfo ci) {
        if (player != null && packet.clientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_3))
            PackHooks.guarded(connection, () -> PackHooks.afterSend(player, packet));
    }
}
