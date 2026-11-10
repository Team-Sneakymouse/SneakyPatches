package com.sneakyrp.sneakypatches.trackingrange;

import com.mojang.logging.LogUtils;
import com.sneakyrp.sneakypatches.trackingrange.mixin.TrackedEntityAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.slf4j.Logger;
import org.spigotmc.TrackingRange;

/**
 * Applies {@link RangeCategories} to the server's entity tracker.
 *
 * <p>An entity's range is decided when the tracker first sees it (spawn, chunk load) and again whenever a category
 * tag is added or removed through {@code addTag}/{@code removeTag} (Bukkit {@code addScoreboardTag}, {@code /tag}).
 * Editing the set returned by {@code getScoreboardTags()} directly is not seen until the entity is tracked again.
 *
 * <p>Public static methods are the patch's whole API, used by {@link TrackingRangesCommand} and test tooling; call
 * them on the server thread.
 */
public final class TrackingRanges {
    public static final String CONFIG_PROPERTY = "sneakypatches.trackingRanges";
    static final Path DEFAULT_CONFIG = Path.of("config", "sneakypatches", "tracking-ranges.properties");
    private static final int MAX_UNKNOWN_WARNINGS = 64;

    private static final Logger LOGGER = LogUtils.getLogger();
    // Must stay above `categories`: static fields initialise in order, and load() writes TEMPLATE.
    private static final List<String> TEMPLATE = List.of(
        "# SneakyPatches tracking ranges. How far (in blocks) entities are sent to players.",
        "# Apply edits with /trackingranges reload, no restart needed. A file with any bad line is refused and the",
        "# ranges in force stay; /trackingranges shows what is in force. Blocks: 1-512.",
        "",
        "# Categories. An entity joins one with the scoreboard tag tracking.<name>:",
        "#   /tag @e[type=item_display,limit=1,sort=nearest] add tracking.long",
        "# A category replaces every other range for that entity. Several tags: the widest wins. A vehicle is tracked",
        "# as far as its widest passenger. Names: a-z, 0-9, _. Add or remove categories freely.",
        "short = 32",
        "medium = 64",
        "long = 128",
        "extreme = 256",
        "",
        "# Tags other plugins already set: @tag.<scoreboard tag> = <category> puts every entity with that tag in the",
        "# category, as if it had tracking.<category>. Category tags still apply; the widest wins. Example:",
        "#   @tag.grounditem = short",
        "",
        "# Ranges for entities without a category tag, in every world. 'spigot' uses spigot.yml",
        "# entity-tracking-range; a number replaces it. Paper's spigot.yml defaults: players 128, animals 96,",
        "# monsters 96, misc 96 (items, item frames, paintings, xp), display 128, other 64 (everything else).",
        "@default.players = spigot",
        "@default.animals = spigot",
        "@default.monsters = spigot",
        "@default.misc = spigot",
        "@default.display = spigot",
        "@default.other = spigot",
        "",
        "# Display limit: 0 = off. Or 0.5-5.0: a display without a category is never sent farther than a client with",
        "# that Entity Distance setting draws it (view_range x 64 x this). 1.0 = the default 100%. Only lowers ranges.",
        "@display_view_scale = 0",
        "",
        "# Still applies on top: server.properties entity-broadcast-range-percentage scales every range, and nobody is",
        "# sent entities beyond their own view distance."
    );
    private static final Set<String> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();
    private static final RangeCategories.UnknownSink UNKNOWN = TrackingRanges::warnUnknown;
    private static volatile RangeCategories categories = load();

    private TrackingRanges() {
    }

    /** The current categories. */
    public static RangeCategories categories() {
        return categories;
    }

    /**
     * The range for {@code entity} given the range {@code computed} by the server's settings. Untracked entity types
     * (range 0) stay untracked, and the ender dragon keeps its world-wide range.
     */
    public static int adjust(Entity entity, int computed) {
        if (computed == 0 || entity instanceof EnderDragon) return computed;
        RangeCategories current = categories;
        int range = current.rangeFor(entity.entityTags(), UNKNOWN);
        if (range != RangeCategories.NONE) return range;
        // Without a category, a display is never tracked farther than a client draws it (only ever lowers).
        if (entity instanceof Display display) {
            int limit = current.displayLimit(display.getViewRange());
            if (limit != RangeCategories.NONE && limit < computed) return limit;
        }
        return computed;
    }

    /** spigot.yml's range for {@code type} ({@code configured}), or its {@code @default.<type>} override. */
    public static int typeDefault(String type, int configured) {
        return categories.typeDefault(type, configured);
    }

    /** Tracking range a tracked passenger adds to its vehicle; falls back to {@code fallback} if untracked. */
    public static int passengerRange(Entity passenger, int fallback) {
        ChunkMap.TrackedEntity tracker = passenger.moonrise$getTrackedEntity();
        return tracker == null ? fallback : ((TrackedEntityAccessor) tracker).sneakyPatches$range();
    }

    /** The range the tracker stores for {@code entity} (before scaling and passengers), or -1 if it isn't tracked. */
    public static int trackedRange(Entity entity) {
        ChunkMap.TrackedEntity tracker = entity.moonrise$getTrackedEntity();
        return tracker == null ? -1 : ((TrackedEntityAccessor) tracker).sneakyPatches$range();
    }

    /** Called after a tag was really added or removed. */
    public static void tagChanged(Entity entity, String tag) {
        if (categories.decidesRange(tag)) refresh(entity, true);
    }

    /** A display's view range changed: follow it, unless another plugin has set this tracker's range itself. */
    public static void viewRangeChanged(Display display) {
        if (categories.displayViewScale() != 0) refresh(display, false);
    }

    /**
     * Recomputes a tracked entity's range from the server's settings and its category tags. Replaces any range
     * another plugin set on it by reflection. Players move in or out of range on the next tracker tick.
     */
    public static void refresh(Entity entity) {
        refresh(entity, true);
    }

    /**
     * @param force replace the stored range even if another plugin set it (category tags are explicit); otherwise
     *              only while the stored range is still the one last assigned by the server or this patch
     */
    /** @return whether the stored range changed */
    static boolean refresh(Entity entity, boolean force) {
        ChunkMap.TrackedEntity tracker = entity.moonrise$getTrackedEntity();
        if (tracker == null) return false;
        TrackedEntityAccessor access = (TrackedEntityAccessor) tracker;
        AssignedRange owner = (AssignedRange) tracker;
        int stored = access.sneakyPatches$range();
        if (!force && stored != owner.sneakyPatches$assigned()) return false;
        int range = TrackingRange.getEntityTrackingRange(entity, entity.getType().clientTrackingRange() << 4);
        if (range == 0) return false;
        access.sneakyPatches$setRange(range);
        owner.sneakyPatches$setAssigned(range);
        return range != stored;
    }

    /**
     * What {@link #reload()} did.
     *
     * @param applied   whether the file was applied; if not, the previous categories are still in force
     * @param problems  why it was refused (every problem in the file), empty when applied
     * @param refreshed loaded entities whose tracking range changed
     */
    public record ReloadResult(boolean applied, List<String> problems, int refreshed) {
    }

    /**
     * Rereads the category file and, only if it is completely valid, applies it to every loaded entity at once. A
     * missing or unreadable file, or any bad line, changes nothing: the current categories stay. (Startup is more
     * lenient: there, bad lines are skipped and the rest applies.) Server thread only.
     */
    public static ReloadResult reload() {
        Path path = configPath();
        RangeCategories parsed;
        try {
            if (Files.notExists(path)) return refused(path, List.of(path + " does not exist"));
            parsed = RangeCategories.parse(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return refused(path, List.of("cannot read " + path + ": " + e));
        }
        if (!parsed.problems().isEmpty()) return refused(path, parsed.problems());
        categories = parsed;
        int refreshed = refreshAll();
        LOGGER.info("[SneakyPatches] Reloaded tracking range categories from {}: {}{}; {} entities changed range", path,
            parsed.ranges().isEmpty() ? "none" : parsed.ranges(),
            (parsed.tagAliases().isEmpty() ? "" : ", tag aliases " + parsed.tagAliases())
                + (parsed.typeDefaults().isEmpty() ? "" : ", type defaults " + parsed.typeDefaults())
                + (parsed.displayViewScale() == 0 ? "" : ", display view scale " + parsed.displayViewScale()),
            refreshed);
        return new ReloadResult(true, List.of(), refreshed);
    }

    private static ReloadResult refused(Path path, List<String> problems) {
        for (String problem : problems) {
            LOGGER.warn("[SneakyPatches] Tracking range reload refused, {}: {}", path, problem);
        }
        return new ReloadResult(false, List.copyOf(problems), 0);
    }

    private static int refreshAll() {
        WARNED_UNKNOWN.clear();
        RangeCategories current = categories;
        int refreshed = 0;
        for (ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                boolean tagged = false;
                for (String tag : entity.entityTags()) {
                    if (current.decidesRange(tag)) {
                        tagged = true;
                        break;
                    }
                }
                // Tagged (category or aliased tag): explicit, so it replaces even a range another plugin set. Untagged
                // (a type default or the display limit may have changed): only while the range is still ours.
                if (refresh(entity, tagged)) refreshed++;
            }
        }
        return refreshed;
    }

    static Path configPath() {
        String override = System.getProperty(CONFIG_PROPERTY);
        return override == null || override.isBlank() ? DEFAULT_CONFIG : Path.of(override);
    }

    private static RangeCategories load() {
        Path path = configPath();
        try {
            if (Files.notExists(path)) {
                Path parent = path.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.write(path, TEMPLATE, StandardCharsets.UTF_8);
                LOGGER.info("[SneakyPatches] Wrote {}: categories short, medium, long, extreme; untagged entities keep "
                    + "their spigot.yml ranges", path);
            }
            RangeCategories parsed = RangeCategories.parse(Files.readAllLines(path, StandardCharsets.UTF_8));
            for (String problem : parsed.problems()) {
                LOGGER.error("[SneakyPatches] {} {}: skipped", path, problem);
            }
            LOGGER.info("[SneakyPatches] Tracking range categories from {}: {}{}{}{}", path,
                parsed.ranges().isEmpty() ? "none" : parsed.ranges(),
                parsed.tagAliases().isEmpty() ? "" : "; tag aliases " + parsed.tagAliases(),
                parsed.typeDefaults().isEmpty() ? "" : "; type defaults (replace spigot.yml) " + parsed.typeDefaults(),
                parsed.displayViewScale() == 0 ? "" : "; display view scale " + parsed.displayViewScale());
            return parsed;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[SneakyPatches] Cannot read {}; every entity keeps the server's tracking range", path, e);
            return RangeCategories.EMPTY;
        }
    }

    private static void warnUnknown(String name) {
        if (WARNED_UNKNOWN.size() < MAX_UNKNOWN_WARNINGS && WARNED_UNKNOWN.add(name)) {
            LOGGER.warn("[SneakyPatches] Scoreboard tag '{}{}' names no tracking range category in {}; those entities "
                + "keep the server's range", RangeCategories.TAG_PREFIX, name, configPath());
        }
    }
}
