package com.sneakyrp.sneakypatches.idlesend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reads the pinned Paper classes (no Minecraft is started) and checks what skipping a viewerless entity's send relies
 * on. A failure after a Paper upgrade means: reread ServerEntity and the tracker loop before shipping.
 */
class PaperTargetTest {
    private static final String SERVER_ENTITY = "net/minecraft/server/level/ServerEntity";
    private static final String TRACKED = "net/minecraft/server/level/ChunkMap$TrackedEntity";
    private static final String CHUNK_MAP = "net/minecraft/server/level/ChunkMap";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String ADD_PACKET = "net/minecraft/network/protocol/game/ClientboundAddEntityPacket";
    private static final String PAIRING_DESC =
        "(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V";
    /** The last-sent state the mixin brings up to date, as Paper's constructor sets it. */
    private static final Set<String> REFRESHED = Set.of("lastSentMovement", "lastSentYRot", "lastSentXRot",
        "lastSentYHeadRot", "wasOnGround", "trackedDataValues");

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

    private static Set<String> fields(MethodNode method, int opcode) {
        var names = new HashSet<String>();
        for (var insn : method.instructions) {
            if (insn instanceof FieldInsnNode field && field.getOpcode() == opcode
                && field.owner.equals(SERVER_ENTITY)) names.add(field.name);
        }
        return names;
    }

    @Test
    void shadowedFieldsHaveTheExpectedTypes() throws IOException {
        var type = read(SERVER_ENTITY);
        var expected = Map.ofEntries(
            Map.entry("entity", "Lnet/minecraft/world/entity/Entity;"),
            Map.entry("trackedPlayers", "Ljava/util/Set;"),
            Map.entry("synchronizer", "L" + SERVER_ENTITY + "$Synchronizer;"),
            Map.entry("positionCodec", "Lnet/minecraft/network/protocol/game/VecDeltaCodec;"),
            Map.entry("lastSentYRot", "B"),
            Map.entry("lastSentXRot", "B"),
            Map.entry("lastSentYHeadRot", "B"),
            Map.entry("lastSentMovement", "Lnet/minecraft/world/phys/Vec3;"),
            Map.entry("wasOnGround", "Z"),
            Map.entry("trackedDataValues", "Ljava/util/List;"),
            Map.entry("teleportDelay", "I"));
        for (var entry : expected.entrySet()) {
            var field = type.fields.stream().filter(f -> f.name.equals(entry.getKey())).findFirst();
            assertTrue(field.isPresent(), entry.getKey());
            assertEquals(entry.getValue(), field.get().desc, entry.getKey());
            assertEquals(0, field.get().access & Opcodes.ACC_STATIC, entry.getKey());
        }
    }

    @Test
    void theRefreshSetsWhatTheConstructorSets() throws IOException {
        // The mixin's refresh must leave a tracker as a new one starts. If the constructor starts setting more
        // last-sent state, the refresh must too.
        var constructor = method(read(SERVER_ENTITY), "<init>", null);
        var set = fields(constructor, Opcodes.PUTFIELD);
        assertTrue(set.containsAll(REFRESHED), "constructor no longer sets " + REFRESHED);
        assertTrue(calls(constructor, "net/minecraft/network/protocol/game/VecDeltaCodec", "setBase"));
        var other = new HashSet<>(set);
        other.removeAll(REFRESHED);
        // Construction-only fields: collaborators, settings and empty collections.
        other.removeAll(Set.of("positionCodec", "lastPassengers", "trackedPlayers", "level", "synchronizer", "entity",
            "updateInterval", "trackDelta"));
        assertEquals(Set.of(), other, "the constructor sets state the refresh does not");
    }

    @Test
    void theSpawnPacketReadsCurrentPositionAndLastSentRotation() throws IOException {
        // Why a stale last-sent state is wrong for a first viewer, and why the refresh covers it.
        var packet = method(read(ADD_PACKET), "<init>", "(L" + ENTITY + ";L" + SERVER_ENTITY + ";I)V");
        assertTrue(calls(packet, ENTITY, "trackingPosition"));
        for (var getter : new String[] {"getLastSentXRot", "getLastSentYRot", "getLastSentYHeadRot",
            "getLastSentMovement"}) {
            assertTrue(calls(packet, SERVER_ENTITY, getter), getter);
        }
    }

    @Test
    void everyNewViewerGoesThroughPairingData() throws IOException {
        // The refresh hooks sendPairingData: it must build the spawn packet and be the only way a viewer is paired.
        var type = read(SERVER_ENTITY);
        var pairing = method(type, "sendPairingData", PAIRING_DESC);
        assertTrue(calls(pairing, ENTITY, "getAddEntityPacket"));
        assertTrue(fields(pairing, Opcodes.GETFIELD).contains("trackedDataValues"));
        assertTrue(calls(method(type, "addPairing", null), SERVER_ENTITY, "sendPairingData"));
        assertTrue(calls(method(read(TRACKED), "updatePlayer", null), SERVER_ENTITY, "addPairing"));
    }

    @Test
    void theTrackerLoopCallsSendChangesOnce() throws IOException {
        // The redirect replaces exactly this call; Paper decides next to it whether an entity is sent at all.
        var loop = method(read(CHUNK_MAP), "newTrackerTick", "()V");
        int sends = 0;
        for (var insn : loop.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(SERVER_ENTITY)
                && call.name.equals("sendChanges") && call.desc.equals("()V")) sends++;
        }
        assertEquals(1, sends);
        assertTrue(calls(loop, null, "moonrise$hasPlayers"), "Paper's own viewer check moved");
        assertFalse(calls(method(read(TRACKED), "updatePlayer", null), SERVER_ENTITY, "sendChanges"));
    }

    @Test
    void theTrackerIsItsServerEntitysSynchronizer() throws IOException {
        // The skip asks the synchronizer whether the tracker has viewers (the set a plugin may have replaced).
        var tracker = read(TRACKED);
        assertTrue(tracker.interfaces.contains(SERVER_ENTITY + "$Synchronizer"));
        assertTrue(tracker.interfaces.contains(
            "ca/spottedleaf/moonrise/patches/entity_tracker/EntityTrackerTrackedEntity"));
        boolean readsSeenBy = false;
        for (var insn : method(tracker, "moonrise$hasPlayers", "()Z").instructions) {
            if (insn instanceof FieldInsnNode field && field.name.equals("seenBy")) readsSeenBy = true;
        }
        assertTrue(readsSeenBy, "moonrise$hasPlayers no longer reads the tracker's seenBy");
        // ServerEntity(level, entity, interval, trackDelta, synchronizer, trackedPlayers), built by the tracker
        // with itself and its seenBy.
        method(read(SERVER_ENTITY), "<init>", "(Lnet/minecraft/server/level/ServerLevel;L" + ENTITY + ";IZL"
            + SERVER_ENTITY + "$Synchronizer;Ljava/util/Set;)V");
    }

    @Test
    void whatSendChangesDoesBesidesSendingIsKeptOrHarmless() throws IOException {
        var send = method(read(SERVER_ENTITY), "sendChanges", "()V");
        // Events fired from it are for the entity's own player, which is never skipped.
        for (var insn : send.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals("callEvent")) {
                assertEquals("org/bukkit/event/player/PlayerVelocityEvent", call.owner);
            }
        }
        // It runs the entity's pre-sync hook, which the skip runs too: a living entity's effects set its invisible
        // and glowing flags there and nowhere else.
        assertTrue(calls(send, ENTITY, "updateDataBeforeSync"));
        assertNotEquals(0, method(read(ENTITY), "updateDataBeforeSync", "()V").access & Opcodes.ACC_PUBLIC);
        var living = read("net/minecraft/world/entity/LivingEntity");
        assertTrue(calls(method(living, "updateDataBeforeSync", "()V"), null, "updateDirtyEffects"));
        int callers = 0;
        for (var m : living.methods) {
            if (calls(m, null, "updateDirtyEffects")) callers++;
        }
        assertEquals(1, callers, "effects now update the flags somewhere else too: the skip may not need the hook");
        // Outside ServerEntity it writes only these entity flags, which wait for a viewer.
        var written = new HashSet<String>();
        for (var insn : send.instructions) {
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                && !field.owner.equals(SERVER_ENTITY)) written.add(field.owner + "." + field.name);
        }
        assertEquals(Set.of(ENTITY + ".hurtMarked", ENTITY + ".needsSync", ENTITY + ".syncPosition"), written);
    }

    @Test
    void aLargeTeleportDelayForcesAPositionSync() throws IOException {
        // The fallback for a viewer added without pairing data relies on Paper's own use of this value.
        var send = method(read(SERVER_ENTITY), "sendChanges", "()V");
        assertTrue(fields(send, Opcodes.PUTFIELD).contains("teleportDelay"));
        assertTrue(calls(send, "net/minecraft/network/protocol/game/ClientboundEntityPositionSyncPacket", "of"));
    }
}
