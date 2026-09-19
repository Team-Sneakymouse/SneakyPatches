package com.sneakyrp.sneakypatches.packs.mixin;

import com.sneakyrp.sneakypatches.packs.SessionAccess;
import java.util.Map;
import java.util.UUID;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackSession;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ResourcePackSession.class, remap = false)
public abstract class SessionMixin implements SessionAccess {
    @Shadow @Final private Map<String, Object> recordsByHash;
    @Override public UUID sneakypatches$clientId(String hash) {
        return ((RecordMixin) recordsByHash.get(hash)).sneakypatches$clientId();
    }
}
