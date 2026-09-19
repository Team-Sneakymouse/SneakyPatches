package com.sneakyrp.sneakypatches.packs;

import java.util.UUID;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketContext;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackRequest;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;

public final class PackHooks {
    private PackHooks() { }
    public static PackOrder order(ProxyPlayer player) { return ((OrderedPlayer) player).sneakypatches$order(); }

    private static ResourcePackRequest request(PacketContext packet) {
        var payload = packet.payload();
        UUID id = payload.readUUID();
        String url = payload.readUtf(32767);
        String hash = payload.readUtf(40);
        packet.payload(); // Reset before CE reads the same packet.
        return new ResourcePackRequest(id, url, hash);
    }

    public static void beforeSend(ChannelConnection connection, ProxyPlayer player, PacketContext packet) {
        if (packet.state() != ConnectionState.CONFIGURATION) return;
        ResourcePackRequest request = request(packet);
        order(player).request(request.uniqueId());
        // Resolve a filtered alias through the exact CE decision, including its
        // installed-status and UUID-conflict checks. Do not infer it from hash alone.
        // Every real push appends, including same-UUID hash replacements.
        // Only an installed, filterable generation can retain its position.
        UUID actualId = null;
        var decision = player.resourcePackSession().prepareRequest(request);
        if (decision.filtered()) {
            actualId = ((SessionAccess) (Object) player.resourcePackSession()).sneakypatches$clientId(request.hash());
        }
        order(player).before(actualId, id -> pop(connection, player, id));
    }

    public static void afterSend(ProxyPlayer player, PacketContext packet) {
        var request = request(packet);
        UUID actualId = packet.isCancelled()
                ? player.resourcePackSession().prepareRemove(request.uniqueId()).forwardedId()
                : request.uniqueId();
        if (!packet.isCancelled()) order(player).remove(actualId);
        order(player).pushed(actualId, packet.state() == ConnectionState.CONFIGURATION);
    }

    public static void pop(ChannelConnection connection, ProxyPlayer player, UUID id) {
        var session = player.resourcePackSession();
        var removal = session.prepareRemove(id);
        boolean sent = connection.sendClientbound(ConnectionState.CONFIGURATION,
                PacketType.Configuration.Server.RESOURCE_PACK_REMOVE, true,
                payload -> { payload.writeBoolean(true); payload.writeUUID(removal.forwardedId()); });
        if (!sent) throw new IllegalStateException("Unable to send required pack removal");
        session.commitRemove(removal);
    }

    public static void guarded(ChannelConnection connection, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | Error failure) {
            // CE's dispatcher otherwise logs and forwards an unmodified packet.
            // Do not let that admit a player with a partially reconciled stack.
            connection.channel().close();
            throw failure;
        }
    }
}
