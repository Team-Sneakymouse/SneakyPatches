package com.sneakyrp.sneakypatches.packs;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class VelocityDedupTargetTest {
    @Test void currentVelocityHasExactlyTwoBackendPrefilterCallSites() throws IOException {
        for (String owner : List.of(
                "com/velocitypowered/proxy/connection/backend/ConfigSessionHandler",
                "com/velocitypowered/proxy/connection/backend/BackendPlaySessionHandler")) {
            var type = new ClassNode();
            try (var stream = getClass().getClassLoader().getResourceAsStream(owner + ".class")) {
                assertNotNull(stream, "Current Velocity JAR must be on the test classpath");
                new ClassReader(stream).accept(type, 0);
            }
            long calls = type.methods.stream()
                    .flatMap(method -> method.instructions.iterator().hasNext()
                            ? java.util.stream.StreamSupport.stream(
                                ((Iterable<org.objectweb.asm.tree.AbstractInsnNode>) () -> method.instructions.iterator()).spliterator(), false)
                            : java.util.stream.Stream.empty())
                    .filter(instruction -> instruction instanceof MethodInsnNode call
                            && call.owner.equals("com/velocitypowered/proxy/connection/player/resourcepack/handler/ResourcePackHandler")
                            && call.name.equals("hasPackAppliedByHash")
                            && call.desc.equals("([B)Z"))
                    .count();
            assertEquals(1, calls, "Review Velocity backend deduplication after an upgrade: " + owner);
        }
    }
}
