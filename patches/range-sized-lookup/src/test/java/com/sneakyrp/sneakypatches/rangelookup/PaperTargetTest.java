package com.sneakyrp.sneakypatches.rangelookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reads the pinned Paper classes (no Minecraft is started) and checks what reading a smaller player list relies on.
 * A failure after a Paper upgrade means: reread the tracker tick and Moonrise's NearbyPlayers before shipping.
 */
class PaperTargetTest {
    private static final String TRACKED = "net/minecraft/server/level/ChunkMap$TrackedEntity";
    private static final String NEARBY = "ca/spottedleaf/moonrise/common/misc/NearbyPlayers";
    private static final String CHUNK = NEARBY + "$TrackedChunk";
    private static final String TYPE = NEARBY + "$NearbyMapType";
    private static final String TICK_DESC = "(L" + CHUNK + ";)V";

    private static ClassNode read(String name) throws IOException {
        var node = new ClassNode();
        try (var stream = PaperTargetTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, "Paper development server must be on the test classpath: " + name);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        var found = type.methods.stream().filter(m -> m.name.equals(name) && (desc == null || m.desc.equals(desc)))
            .toList();
        assertEquals(1, found.size(), type.name + "." + name + (desc == null ? "" : desc));
        return found.getFirst();
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        var list = new ArrayList<AbstractInsnNode>();
        for (var insn : method.instructions) {
            if (insn.getOpcode() >= 0) list.add(insn);
        }
        return list;
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        return real(method).stream().anyMatch(insn -> insn instanceof MethodInsnNode call
            && (owner == null || call.owner.equals(owner)) && call.name.equals(name));
    }

    private static int intConstant(AbstractInsnNode insn) {
        if (insn instanceof IntInsnNode push) return push.operand;
        if (insn instanceof InsnNode && insn.getOpcode() >= Opcodes.ICONST_0 && insn.getOpcode() <= Opcodes.ICONST_5) {
            return insn.getOpcode() - Opcodes.ICONST_0;
        }
        throw new AssertionError("not an int constant: opcode " + insn.getOpcode());
    }

    @Test
    void theTickLooksUpTheViewDistanceListOnceAndThenReadsItsBookkeeping() throws IOException {
        var insns = real(method(read(TRACKED), "moonrise$tick", TICK_DESC));
        int lookup = -1, lookups = 0, chunkRead = -1, contains = 0, updates = 0;
        for (int i = 0; i < insns.size(); i++) {
            var insn = insns.get(i);
            if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals(CHUNK) && call.name.equals("getPlayers")) {
                    lookups++;
                    lookup = i;
                    var list = (FieldInsnNode) insns.get(i - 1);
                    assertEquals(TYPE + ".VIEW_DISTANCE", list.owner + "." + list.name);
                }
                if (call.name.equals("contains")) contains++;
                if (call.owner.equals(TRACKED) && call.name.equals("updatePlayer")) updates++;
            }
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && field.name.equals("lastTrackedChunk") && chunkRead < 0) chunkRead = i;
        }
        assertEquals(1, lookups);
        // The redirect resets lastTrackedChunk to force the removal pass: Paper must read it after the lookup.
        assertTrue(chunkRead > lookup, "lastTrackedChunk is read before the lookup");
        // One list: the same one is walked for updatePlayer and asked which viewers are still candidates.
        assertEquals(1, updates);
        assertEquals(1, contains);
    }

    @Test
    void lastTrackedChunkCanBeReset() throws IOException {
        var field = read(TRACKED).fields.stream().filter(f -> f.name.equals("lastTrackedChunk")).findFirst()
            .orElseThrow();
        assertEquals("L" + CHUNK + ";", field.desc);
        assertEquals(0, field.access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC));
    }

    @Test
    void theSmallListsAreThreeAndTenChunks() throws IOException {
        var nearby = read(NEARBY);
        for (var field : nearby.fields) {
            switch (field.name) {
                case "GENERAL_REALLY_SMALL_AREA_VIEW_DISTANCE_BLOCKS" ->
                    assertEquals(PlayerList.REALLY_SMALL_BLOCKS, field.value);
                case "GENERAL_SMALL_AREA_VIEW_DISTANCE_BLOCKS" ->
                    assertEquals(PlayerList.SMALL_BLOCKS, field.value);
                default -> { }
            }
        }
        // And each player's area in those lists is kept at that radius: tickPlayer names the list, then updates the
        // player's area with (chunk x, chunk z, radius).
        var insns = real(method(nearby, "tickPlayer", null));
        int found = 0;
        for (int i = 0; i < insns.size(); i++) {
            if (!(insns.get(i) instanceof FieldInsnNode field) || !field.owner.equals(TYPE)) continue;
            int expected = switch (field.name) {
                case "GENERAL_REALLY_SMALL" -> 3;
                case "GENERAL_SMALL" -> 10;
                default -> -1;
            };
            if (expected < 0) continue;
            for (int j = i + 1; j < insns.size(); j++) {
                if (insns.get(j) instanceof MethodInsnNode call && call.name.equals("update")) {
                    assertEquals(expected, intConstant(insns.get(j - 1)), field.name);
                    found++;
                    break;
                }
            }
        }
        assertEquals(2, found);
    }

    @Test
    void anyListChangeBumpsTheChunksOneUpdateCount() throws IOException {
        // Paper's removal pass runs when the count changed; it must change when the chosen list did.
        var chunk = read(CHUNK);
        assertEquals(1, chunk.fields.stream().filter(f -> f.name.equals("updateCount")).count());
        for (var name : new String[] {"addPlayer", "removePlayer"}) {
            var first = real(method(chunk, name, null)).stream().filter(insn -> insn instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.PUTFIELD).findFirst().orElseThrow();
            assertEquals("updateCount", ((FieldInsnNode) first).name, name + " no longer bumps the count first");
        }
    }

    @Test
    void theRangeCheckIsHorizontalAndUsesTheEffectiveRange() throws IOException {
        var tracked = read(TRACKED);
        var update = method(tracked, "updatePlayer", null);
        assertTrue(calls(update, TRACKED, "getEffectiveRange"));
        assertTrue(calls(update, "net/minecraft/world/entity/Entity", "getX"));
        assertTrue(calls(update, "net/minecraft/world/entity/Entity", "getZ"));
        assertTrue(calls(update, "java/lang/Math", "min"), "the range is no longer capped by the view distance");
        assertEquals("()I", method(tracked, "getEffectiveRange", null).desc);
    }

    @Test
    void theListsFollowAPlayerWhoeverMovedThem() throws IOException {
        // Not only on the player's own movement packets: any change of section refreshes every list.
        var lookup = read("ca/spottedleaf/moonrise/patches/chunk_system/level/entity/server/ServerEntityLookup");
        assertTrue(calls(method(lookup, "entitySectionChangeCallback", null), NEARBY, "tickPlayer"));
    }
}
