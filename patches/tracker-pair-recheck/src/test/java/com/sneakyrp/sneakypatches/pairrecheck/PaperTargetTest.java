package com.sneakyrp.sneakypatches.pairrecheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reads the pinned Paper classes (no Minecraft is started) and checks what skipping a still entity's tracker tick
 * relies on. A failure after a Paper upgrade means: reread the tracker code before shipping.
 */
class PaperTargetTest {
    private static final String CHUNK_MAP = "net/minecraft/server/level/ChunkMap";
    private static final String TRACKED = "net/minecraft/server/level/ChunkMap$TrackedEntity";
    private static final String SERVER_PLAYER = "net/minecraft/server/level/ServerPlayer";
    private static final String TICK = "moonrise$tick";
    private static final String TICK_DESC = "(Lca/spottedleaf/moonrise/common/misc/NearbyPlayers$TrackedChunk;)V";
    private static final String PLAYER_DESC = "(L" + SERVER_PLAYER + ";)V";

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

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && (owner == null || call.owner.equals(owner))
                && call.name.equals(name)) return true;
        }
        return false;
    }

    @Test
    void tickChecksThePlayersAndClearsThemWithoutAChunk() throws IOException {
        // The mixin skips this method's body; it must hold the player checks and nothing that has to run every tick.
        var tick = method(read(TRACKED), TICK, TICK_DESC);
        assertTrue(calls(tick, TRACKED, "updatePlayer"));
        assertTrue(calls(tick, TRACKED, "moonrise$clearPlayers"), "null chunk path; the mixin never skips it");
        assertFalse(calls(tick, null, "sendChanges"), "sending changes moved into the skipped tick");
    }

    @Test
    void changesAreSentOutsideTheSkippedTick() throws IOException {
        var loop = method(read(CHUNK_MAP), "newTrackerTick", "()V");
        assertTrue(calls(loop, null, TICK));
        assertTrue(calls(loop, "net/minecraft/server/level/ServerEntity", "sendChanges"));
    }

    @Test
    void serverTickCounterIsAStaticInt() throws IOException {
        var field = read("net/minecraft/server/MinecraftServer").fields.stream()
            .filter(f -> f.name.equals("currentTick")).findFirst().orElseThrow();
        assertEquals("I", field.desc);
        assertNotEquals(0, field.access & Opcodes.ACC_STATIC);
        assertNotEquals(0, field.access & Opcodes.ACC_PUBLIC);
    }

    @Test
    void newTrackersAndJoiningPlayersAreCheckedAtOnce() throws IOException {
        // Not left to the next tracker tick, so a skip cannot delay them.
        var add = method(read(CHUNK_MAP), "addEntity", "(Lnet/minecraft/world/entity/Entity;)V");
        assertTrue(calls(add, TRACKED, "updatePlayers"), "a new tracker checks every player");
        assertTrue(calls(add, TRACKED, "updatePlayer"), "a joining player is checked against every tracker");
    }

    @Test
    void hideAndShowUpdateTheTrackerAtOnce() throws IOException {
        var player = read("org/bukkit/craftbukkit/entity/CraftPlayer");
        assertTrue(calls(method(player, "unregisterEntity", null), TRACKED, "removePlayer"));
        // Build 116 shows through trackAndShowEntity (later builds: showEntity0).
        assertTrue(calls(method(player, "trackAndShowEntity", "(Lorg/bukkit/entity/Entity;Ljava/util/UUID;)V"),
            TRACKED, "updatePlayer"));
    }

    @Test
    void trackerHoldsItsRangeInAnIntField() throws IOException {
        // The mixin shadows it to notice range changes made at runtime by other patches.
        var field = read(TRACKED).fields.stream().filter(f -> f.name.equals("range")).findFirst().orElseThrow();
        assertEquals("I", field.desc);
        assertEquals(0, field.access & Opcodes.ACC_STATIC);
    }

    @Test
    void updatePlayerTakesAServerPlayer() throws IOException {
        method(read(TRACKED), "updatePlayer", PLAYER_DESC);
    }
}
