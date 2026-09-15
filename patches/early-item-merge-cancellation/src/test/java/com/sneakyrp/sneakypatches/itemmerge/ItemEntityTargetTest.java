package com.sneakyrp.sneakypatches.itemmerge;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class ItemEntityTargetTest {
    @Test
    void pinnedPaperStillHasThePrivateMergeTargetAndCallsItFromTick() throws IOException {
        var owner = "net/minecraft/world/entity/item/ItemEntity";
        var type = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(stream, "Paper development server must be on the test classpath");
            new ClassReader(stream).accept(type, 0);
        }
        var targets = type.methods.stream()
            .filter(method -> method.name.equals("mergeWithNeighbours") && method.desc.equals("()V"))
            .toList();
        assertEquals(1, targets.size(), "Review the injection target after a Paper upgrade");
        var target = targets.getFirst();
        assertTrue((target.access & Opcodes.ACC_PRIVATE) != 0);
        assertEquals(0, target.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE));

        boolean calledFromTick = false;
        boolean queriesNeighbours = false;
        for (var method : type.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (method.name.equals("tick") && call.owner.equals(owner)
                        && call.name.equals(target.name) && call.desc.equals(target.desc)) {
                        calledFromTick = true;
                    }
                    if (method == target && call.name.equals("getEntitiesOfClass")) {
                        queriesNeighbours = true;
                    }
                }
            }
        }
        assertTrue(calledFromTick, "Item ticks must still use the guarded method");
        assertTrue(queriesNeighbours, "Review whether the neighbour query moved outside the guard");
    }
}
