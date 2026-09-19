package com.sneakyrp.sneakypatches.packs;

import java.util.Arrays;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackSession;
import net.momirealms.craftengine.proxy.common.network.listener.PacketListenerManager;
import net.momirealms.craftengine.proxy.common.network.listener.common.ResourcePackSendListener;
import net.momirealms.craftengine.proxy.common.network.listener.common.ResourcePackRemoveListener;

/** No maximum version check. Verify the required transformations before binding. */
public final class Compatibility {
    private Compatibility() { }
    public static void verify() {
        if (!OrderedPlayer.class.isAssignableFrom(ProxyPlayer.class)
                || !SessionAccess.class.isAssignableFrom(ResourcePackSession.class))
            throw new IllegalStateException("SneakyPatches: required CE Proxy interfaces were not applied");
        hook(ResourcePackSendListener.class, "sneakypatches$before");
        hook(ResourcePackSendListener.class, "sneakypatches$after");
        hook(ResourcePackRemoveListener.class, "sneakypatches$removed");
        hook(PacketListenerManager.class, "sneakypatches$boundary");
        try {
            hook(Class.forName("com.velocitypowered.proxy.connection.backend.ConfigSessionHandler", false,
                    Compatibility.class.getClassLoader()), "sneakypatches$delegateBackendDeduplication");
            hook(Class.forName("com.velocitypowered.proxy.connection.backend.BackendPlaySessionHandler", false,
                    Compatibility.class.getClassLoader()), "sneakypatches$delegateBackendDeduplication");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing Velocity backend session handler", e);
        }
        try {
            Class<?> record = Class.forName("net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackRecord", false, Compatibility.class.getClassLoader());
            hook(record, "sneakypatches$clientId");
        } catch (ClassNotFoundException e) { throw new IllegalStateException("Missing CE Proxy pack record", e); }
        System.out.println("[SneakyPatches] All resource-pack reconciliation hooks verified");
    }
    private static void hook(Class<?> type, String name) {
        if (Arrays.stream(type.getDeclaredMethods()).noneMatch(m -> m.getName().contains(name)))
            throw new IllegalStateException("SneakyPatches: missing hook " + type.getName() + ":" + name);
    }
}
