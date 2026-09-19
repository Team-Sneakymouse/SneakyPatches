package com.sneakyrp.sneakypatches.packs.mixin;

import com.sneakyrp.sneakypatches.packs.PackHooks;
import io.netty.buffer.ByteBuf;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.listener.PacketListenerManager;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.PacketSide;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PacketListenerManager.class, remap = false)
public abstract class ConfigurationMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void sneakypatches$boundary(ChannelConnection connection, ProxyPlayer player, PacketSide side,
            ByteBuf buffer, CallbackInfoReturnable<ByteBuf> cir) {
        if (player == null || side != PacketSide.SERVER || !buffer.isReadable()
                || !connection.clientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_3)) return;
        PackHooks.guarded(connection, () -> {
            int index = buffer.readerIndex();
            int id;
            try { id = new ProxyByteBuf(buffer).readVarInt(); }
            finally { buffer.readerIndex(index); }
            var state = connection.encoderState();
            if (state == ConnectionState.PLAY && id == PacketType.Play.Server.CONFIGURATION_START.getId(connection.clientVersion()))
                PackHooks.order(player).begin();
            if (state == ConnectionState.CONFIGURATION && id == PacketType.Configuration.Server.CONFIGURATION_END.getId(connection.clientVersion()))
                PackHooks.order(player).finish(pack -> PackHooks.pop(connection, player, pack));
        });
    }
}
