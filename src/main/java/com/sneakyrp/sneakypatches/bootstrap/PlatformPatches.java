package com.sneakyrp.sneakypatches.bootstrap;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Select before target discovery: absent platform classes must never be resolved. */
public abstract class PlatformPatches implements IMixinConfigPlugin {
    protected boolean active;
    protected static ClassLoader loader() { return Thread.currentThread().getContextClassLoader(); }
    static boolean velocity(ClassLoader loader) {
        return loader.getResource("com/velocitypowered/proxy/Velocity.class") != null;
    }
    static boolean paper(ClassLoader loader) {
        return !velocity(loader) && loader.getResource("net/minecraft/world/entity/item/ItemEntity.class") != null;
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return active; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public abstract List<String> getMixins();
}
