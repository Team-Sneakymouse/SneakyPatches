package com.sneakyrp.sneakypatches.bootstrap;

import java.net.URL;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformPatchesTest {
    static ClassLoader resources(String... names) {
        var paths = Set.of(names);
        return new ClassLoader(null) {
            @Override public URL getResource(String name) {
                try { return paths.contains(name) ? new URL("file:/test") : null; }
                catch (Exception e) { throw new AssertionError(e); }
            }
        };
    }
    @Test void platformsAreExclusive() {
        var paper = resources("net/minecraft/world/entity/item/ItemEntity.class");
        var velocity = resources("com/velocitypowered/proxy/Velocity.class");
        assertTrue(PlatformPatches.paper(paper)); assertFalse(PlatformPatches.velocity(paper));
        assertTrue(PlatformPatches.velocity(velocity)); assertFalse(PlatformPatches.paper(velocity));
        assertFalse(PlatformPatches.paper(resources())); assertFalse(PlatformPatches.velocity(resources()));
    }
}
