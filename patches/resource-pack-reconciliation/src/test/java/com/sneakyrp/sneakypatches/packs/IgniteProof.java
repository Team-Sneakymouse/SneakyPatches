package com.sneakyrp.sneakypatches.packs;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.listener.PacketListenerManager;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.PacketSide;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketTypeCommon;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackResult;
import net.momirealms.craftengine.proxy.common.platform.BackendServer;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;

/** Runs inside real Ignite, with the actual transformed CE handlers and session. */
public final class IgniteProof {
    static UUID id(int n) { return new UUID(0, n); }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static final class Manager extends PacketListenerManager {
        Manager() { registerPacketListeners(); registerInternalRegistrations(); }
        @Override public ProxyCraftEngine plugin() { return null; }
        @Override public ErrorHandler errorHandler() { return (id, side, failure) -> { throw new AssertionError(failure); }; }
    }
    static final class Fixture implements AutoCloseable {
        final EmbeddedChannel channel = new EmbeddedChannel();
        final Manager manager = new Manager();
        final ChannelConnection connection = new ChannelConnection(channel, manager);
        final List<UUID> client = new ArrayList<>();
        int pushes, pops;
        final ProxyPlayer player = new ProxyPlayer(connection) {
            @Override public UUID uuid() { return id(999); }
            @Override public Object platform() { return this; }
            @Override public BackendServer server() { return null; }
            @Override public boolean sendServerPluginMessage(String channel, byte[] data) { return false; }
            @Override public Locale locale() { return Locale.ROOT; }
            @Override public void kick(String reason) { throw new AssertionError(reason); }
        };
        Fixture(ClientVersion version) {
            channel.pipeline().addLast("craftengine_proxy_packet_decoder", new ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("craftengine_proxy_packet_encoder", new ChannelOutboundHandlerAdapter());
            connection.setProtocolVersion(version.getProtocolVersion());
            connection.bind(player);
            connection.setConnectionState(ConnectionState.PLAY);
            check(player instanceof OrderedPlayer, "ProxyPlayer mixin not applied");
            check((Object) player.resourcePackSession() instanceof SessionAccess, "Session mixin not applied");
        }
        ByteBuf packet(PacketTypeCommon type, java.util.function.Consumer<ProxyByteBuf> writer) {
            ProxyByteBuf data = new ProxyByteBuf(Unpooled.buffer());
            data.writeVarInt(type.getId(connection.clientVersion())); writer.accept(data);
            ByteBuf original = data.source();
            ByteBuf result = manager.handle(connection, player, PacketSide.SERVER, original);
            if (result != original) original.release();
            drainPops();
            Object response;
            while ((response = channel.readInbound()) != null) ((ByteBuf) response).release();
            return result;
        }
        void drainPops() {
            ByteBuf bytes;
            while ((bytes = channel.readOutbound()) != null) {
                ProxyByteBuf packet = new ProxyByteBuf(bytes);
                check(packet.readVarInt() == PacketType.Configuration.Server.RESOURCE_PACK_REMOVE.getId(connection.clientVersion()), "Unexpected outbound packet");
                check(packet.readBoolean(), "Unexpected remove-all");
                UUID removed = packet.readUUID();
                check(client.remove(removed), "Removed absent client pack " + removed);
                pops++; bytes.release();
            }
        }
        void begin() {
            connection.setConnectionState(ConnectionState.PLAY);
            packet(PacketType.Play.Server.CONFIGURATION_START, p -> {}).release();
            connection.setDecoderState(ConnectionState.CONFIGURATION);
        }
        void push(int n, String hash) {
            ByteBuf result = packet(PacketType.Configuration.Server.RESOURCE_PACK_SEND, p -> {
                p.writeUUID(id(n)); p.writeUtf("https://example.invalid/" + n); p.writeUtf(hash); p.writeBoolean(true); p.writeBoolean(false);
            });
            if (result.isReadable()) {
                // Minecraft pushNewPack removes an old UUID and appends its
                // replacement; a changed hash does not preserve the old slot.
                client.remove(id(n)); client.add(id(n));
                pushes++;
                player.resourcePackSession().handleStatus(id(n), ResourcePackResult.SUCCESS_DOWNLOAD);
            }
            result.release();
        }
        void finish(int... ids) {
            packet(PacketType.Configuration.Server.CONFIGURATION_END, p -> {}).release();
            check(client.equals(java.util.Arrays.stream(ids).mapToObj(IgniteProof::id).toList()), "Client order " + client);
            check(PackHooks.order(player).snapshot().equals(client), "Tracking diverged");
        }
        @Override public void close() { channel.finishAndReleaseAll(); }
    }
    public static void main(String[] args) {
        Compatibility.verify();
        for (var version : List.of(ClientVersion.V_1_21_4, ClientVersion.V_26_2)) {
            try (var f = new Fixture(version)) {
                f.begin(); f.push(1,"a"); f.push(2,"b"); f.push(3,"c"); f.finish(1,2,3);
                int before = f.pushes;
                f.begin(); f.push(1,"a"); f.push(2,"b"); f.push(3,"c"); f.finish(1,2,3);
                check(f.pushes == before && f.pops == 0, "Same selection reloaded");
                f.begin(); f.push(1,"a"); f.push(3,"c"); f.finish(1,3);
                check(f.pushes == before, "Removal reloaded retained packs");
                f.begin(); f.push(1,"a"); f.push(2,"b"); f.push(3,"c"); f.finish(1,2,3);
                f.begin(); f.push(3,"c"); f.push(1,"a"); f.push(2,"b"); f.finish(3,1,2);
                f.begin(); f.push(3,"changed"); f.push(1,"a"); f.push(2,"b"); f.finish(3,1,2);
                // Another server requests a new UUID for the same installed hash.
                f.begin(); f.push(33,"changed"); f.push(1,"a"); f.push(2,"b"); f.finish(3,1,2);
                // Explicit PLAY removal through an alias must remove the actual ID.
                f.packet(PacketType.Play.Server.RESOURCE_PACK_REMOVE, p -> { p.writeBoolean(true); p.writeUUID(id(33)); }).release();
                f.client.remove(id(3));
                check(PackHooks.order(f.player).snapshot().equals(f.client), "Alias removal lost tracking");
                // A failed result must not be treated as an installed cached pack.
                f.player.resourcePackSession().handleStatus(id(1), ResourcePackResult.FAILED_DOWNLOAD);
                int count = f.pushes;
                f.begin(); f.push(1,"a"); f.push(2,"b"); f.finish(1,2);
                check(f.pushes > count, "Failed download was incorrectly filtered");
                f.begin(); f.finish();
            }
            System.out.println("PASS transformed CE packet handlers: " + version);
        }
    }
}
