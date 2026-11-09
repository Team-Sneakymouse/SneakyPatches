package com.sneakyrp.sneakypatches.trackingrange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reads the pinned Paper classes (no Minecraft is started) and checks every assumption the patch makes. A failure
 * after a Paper upgrade means: reread the tracker code before shipping.
 */
class PaperTargetTest {
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String TRACKING_RANGE = "org/spigotmc/TrackingRange";
    private static final String CHUNK_MAP = "net/minecraft/server/level/ChunkMap";
    private static final String TRACKED = "net/minecraft/server/level/ChunkMap$TrackedEntity";
    private static final String HOOKS = "ca/spottedleaf/moonrise/common/PlatformHooks";
    private static final String RANGE_DESC = "(L" + ENTITY + ";I)I";

    private static ClassNode read(String name) throws IOException {
        var node = new ClassNode();
        try (var stream = PaperTargetTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, "Paper development server must be on the test classpath: " + name);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        var found = type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).toList();
        assertEquals(1, found.size(), type.name + "." + name + desc);
        return found.getFirst();
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name, String desc) {
        var result = new ArrayList<MethodInsnNode>();
        for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                && call.desc.equals(desc)) result.add(call);
        }
        return result;
    }

    @Test
    void spigotRangeMethodIsStaticAndReturnsZeroForUntrackedTypes() throws IOException {
        var method = method(read(TRACKING_RANGE), "getEntityTrackingRange", RANGE_DESC);
        assertNotEquals(0, method.access & Opcodes.ACC_STATIC);
        // Starts with: if (defaultRange == 0) return defaultRange; TrackingRanges.adjust keeps 0 as 0 too.
        var first = method.instructions.getFirst();
        while (first.getOpcode() < 0) first = first.getNext();
        assertEquals(Opcodes.ILOAD, first.getOpcode());
        assertEquals(Opcodes.IFNE, first.getNext().getOpcode());
    }

    @Test
    void spigotRangeMethodStillSpecialCasesTheDragon() throws IOException {
        var method = method(read(TRACKING_RANGE), "getEntityTrackingRange", RANGE_DESC);
        boolean dragon = false;
        for (var insn : method.instructions) {
            if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                && type.desc.equals("net/minecraft/world/entity/boss/enderdragon/EnderDragon")) dragon = true;
        }
        assertTrue(dragon, "Spigot no longer special-cases the dragon; review TrackingRanges.adjust");
    }

    @Test
    void spigotReadsEachTypeRangeExactlyOnceInTheRangeMethod() throws IOException {
        // @default.<type> redirects these six reads (require = 1, allow = 1 each).
        var trackingRange = read(TRACKING_RANGE);
        var method = method(trackingRange, "getEntityTrackingRange", RANGE_DESC);
        for (String field : List.of("playerTrackingRange", "animalTrackingRange", "monsterTrackingRange",
            "miscTrackingRange", "displayTrackingRange", "otherTrackingRange")) {
            int reads = 0;
            for (var insn : method.instructions) {
                if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD
                    && f.owner.equals("org/spigotmc/SpigotWorldConfig") && f.name.equals(field)) reads++;
            }
            assertEquals(1, reads, field + " reads in getEntityTrackingRange");
        }
        assertEquals(1, trackingRange.methods.stream().filter(m -> !m.name.startsWith("<")).count(),
            "TrackingRange has a new method that may pick ranges");
    }

    @Test
    void addEntityBuildsTheTrackerFromTheSpigotRange() throws IOException {
        var chunkMap = read(CHUNK_MAP);
        var add = method(chunkMap, "addEntity", "(L" + ENTITY + ";)V");
        assertEquals(1, calls(add, TRACKING_RANGE, "getEntityTrackingRange", RANGE_DESC).size());
        assertEquals(1, calls(add, TRACKED, "<init>", "(L" + CHUNK_MAP + ";L" + ENTITY + ";IIZ)V").size());
        int elsewhere = 0;
        for (var m : chunkMap.methods) {
            if (m != add) elsewhere += calls(m, TRACKING_RANGE, "getEntityTrackingRange", RANGE_DESC).size();
        }
        assertEquals(0, elsewhere, "ChunkMap asks Spigot for a range somewhere new");
    }

    @Test
    void trackerKeepsItsRangeInOneIntFieldReadOnlyByGetEffectiveRange() throws IOException {
        var tracked = read(TRACKED);
        FieldNode range = tracked.fields.stream().filter(f -> f.name.equals("range")).findFirst().orElseThrow();
        assertEquals("I", range.desc);
        for (var m : tracked.methods) {
            for (var insn : m.instructions) {
                if (insn instanceof FieldInsnNode field && field.owner.equals(TRACKED) && field.name.equals("range")) {
                    if (field.getOpcode() == Opcodes.PUTFIELD) assertEquals("<init>", m.name);
                    if (field.getOpcode() == Opcodes.GETFIELD) assertEquals("getEffectiveRange", m.name);
                }
            }
        }
    }

    @Test
    void effectiveRangeAsksForEachPassengerOnceThroughPlatformHooks() throws IOException {
        var tracked = read(TRACKED);
        var effective = method(tracked, "getEffectiveRange", "()I");
        assertEquals(1, calls(effective, HOOKS, "modifyEntityTrackingRange", RANGE_DESC).size());
        assertEquals(1, calls(effective, ENTITY, "getIndirectPassengers", "()Ljava/lang/Iterable;").size());
        assertFalse(calls(effective, TRACKED, "scaledRange", "(I)I").isEmpty(), "broadcast scaling moved");
        var update = method(tracked, "updatePlayer", "(Lnet/minecraft/server/level/ServerPlayer;)V");
        assertEquals(1, calls(update, TRACKED, "getEffectiveRange", "()I").size());
    }

    @Test
    void entityExposesTrackerAndTagMethods() throws IOException {
        var entity = read(ENTITY);
        method(entity, "moonrise$getTrackedEntity", "()L" + TRACKED + ";");
        method(entity, "addTag", "(Ljava/lang/String;)Z");
        method(entity, "removeTag", "(Ljava/lang/String;)Z");
        method(entity, "entityTags", "()Ljava/util/Set;");
    }

    @Test
    void bukkitScoreboardTagsGoThroughAddAndRemoveTag() throws IOException {
        var craft = read("org/bukkit/craftbukkit/entity/CraftEntity");
        assertEquals(1, calls(method(craft, "addScoreboardTag", "(Ljava/lang/String;)Z"), ENTITY, "addTag",
            "(Ljava/lang/String;)Z").size());
        assertEquals(1, calls(method(craft, "removeScoreboardTag", "(Ljava/lang/String;)Z"), ENTITY, "removeTag",
            "(Ljava/lang/String;)Z").size());
    }

    @Test
    void clientDrawsDisplaysWithinViewRangeTimes64TimesViewScale() throws IOException {
        // Shared (client and server) code: the rule @display_view_scale relies on.
        var display = read("net/minecraft/world/entity/Display");
        var render = method(display, "shouldRenderAtSqrDistance", "(D)Z");
        assertEquals(1, calls(render, "net/minecraft/world/entity/Display", "getViewRange", "()F").size());
        // Static Entity.getViewScale(), referenced through Display.
        assertEquals(1, calls(render, "net/minecraft/world/entity/Display", "getViewScale", "()D").size());
        boolean sixtyFour = false;
        for (var insn : render.instructions) {
            if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc && Double.valueOf(64.0).equals(ldc.cst)) {
                sixtyFour = true;
            }
        }
        assertTrue(sixtyFour, "display render distance is no longer view_range x 64: update DISPLAY_BLOCKS_PER_VIEW_RANGE");
        method(display, "setViewRange", "(F)V");
        method(display, "getViewRange", "()F");
    }

    @Test
    void paperRegistersItsBukkitCommandsInOneStaticMethod() throws IOException {
        // /trackingranges is registered at the end of this method, next to /paper.
        var commands = read("io/papermc/paper/command/PaperCommands");
        var register = method(commands, "registerCommands", "(Lnet/minecraft/server/MinecraftServer;)V");
        assertNotEquals(0, register.access & Opcodes.ACC_STATIC);
    }

    @Test
    void trackerConstructorTakesTheRange() throws IOException {
        method(read(TRACKED), "<init>", "(L" + CHUNK_MAP + ";L" + ENTITY + ";IIZ)V");
    }

    @Test
    void commonlyTaggedTypesDoNotOverrideTheTagMethods() throws IOException {
        for (String name : List.of("net/minecraft/server/level/ServerPlayer", "net/minecraft/world/entity/Display",
            "net/minecraft/world/entity/Display$ItemDisplay", "net/minecraft/world/entity/Interaction",
            "net/minecraft/world/entity/decoration/ArmorStand", "net/minecraft/world/entity/LivingEntity",
            "net/minecraft/world/entity/Mob", "net/minecraft/world/entity/item/ItemEntity")) {
            var type = read(name);
            assertTrue(type.methods.stream().noneMatch(m -> m.name.equals("addTag") || m.name.equals("removeTag")),
                name + " overrides a tag method");
        }
    }
}
