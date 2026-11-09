package com.sneakyrp.sneakypatches.trackingrange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Named tracking range categories, parsed from {@code name = blocks} lines. Pure logic: no server classes, so it is
 * unit tested without Minecraft.
 *
 * <p>An entity joins a category with the scoreboard tag {@link #TAG_PREFIX}{@code <name>}. With several category
 * tags the widest range wins, so the result never depends on tag order.
 */
public final class RangeCategories {
    public static final String TAG_PREFIX = "tracking.";
    /** No category applies. */
    public static final int NONE = -1;
    public static final int MIN_RANGE = 1;
    /** Above 32 chunks (the client's maximum view distance) a range changes nothing. */
    public static final int MAX_RANGE = 512;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");

    /** Setting: assume players' "Entity Distance" is at most this (1.0 = 100%); 0 = off. */
    public static final String DISPLAY_VIEW_SCALE = "@display_view_scale";
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 5.0;
    /** Blocks a client draws a display at view_range 1.0 and 100% entity distance (Display.shouldRenderAtSqrDistance). */
    public static final double DISPLAY_BLOCKS_PER_VIEW_RANGE = 64.0;

    /**
     * Setting prefix: {@code @default.<type> = blocks} replaces spigot.yml {@code entity-tracking-range.<type>} for
     * entities without a category tag, in every world.
     */
    public static final String DEFAULT_PREFIX = "@default.";
    /** The spigot.yml entity-tracking-range keys, in spigot.yml order. */
    public static final List<String> TYPES = List.of("players", "animals", "monsters", "misc", "display", "other");
    /** {@code @default.<type> = spigot}: no override, the spigot.yml value applies (what the template writes). */
    public static final String USE_SPIGOT = "spigot";

    public static final RangeCategories EMPTY = new RangeCategories(Map.of(), Map.of(), 0, List.of());

    private final Map<String, Integer> ranges;
    private final Map<String, Integer> typeDefaults;
    private final double displayViewScale;
    private final List<String> problems;

    private RangeCategories(Map<String, Integer> ranges, Map<String, Integer> typeDefaults, double displayViewScale,
                            List<String> problems) {
        this.ranges = ranges;
        this.typeDefaults = typeDefaults;
        this.displayViewScale = displayViewScale;
        this.problems = problems;
    }

    /**
     * Parses a category file. A line that is wrong is skipped and reported in {@link #problems()}; entities in a
     * skipped category keep the server's range, which is how the server behaves without this patch.
     */
    public static RangeCategories parse(List<String> lines) {
        Map<String, Integer> ranges = new LinkedHashMap<>();
        Map<String, Integer> typeDefaults = new LinkedHashMap<>();
        Map<String, String> typesSeen = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        double scale = 0;
        boolean scaleSeen = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            int number = i + 1;
            if (line.isEmpty() || line.startsWith("#")) continue;
            int equals = line.indexOf('=');
            if (equals < 0) {
                problems.add("line " + number + ": expected 'name = blocks', got '" + line + "'");
                continue;
            }
            String name = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            if (name.startsWith(DEFAULT_PREFIX)) {
                String type = name.substring(DEFAULT_PREFIX.length());
                if (!TYPES.contains(type)) {
                    problems.add("line " + number + ": unknown type in '" + name + "' (known: "
                        + String.join(", ", TYPES) + ")");
                    continue;
                }
                boolean useSpigot = value.equalsIgnoreCase(USE_SPIGOT);
                Integer range = useSpigot ? null : blocks(name, value, number, problems);
                if (!useSpigot && range == null) continue;
                if (typesSeen.containsKey(type)) {
                    problems.add("line " + number + ": " + name + " is set twice; keeping the first ("
                        + typesSeen.get(type) + ")");
                    continue;
                }
                typesSeen.put(type, useSpigot ? USE_SPIGOT : String.valueOf(range));
                if (!useSpigot) typeDefaults.put(type, range);
                continue;
            }
            if (name.startsWith("@")) {
                if (!name.equals(DISPLAY_VIEW_SCALE)) {
                    problems.add("line " + number + ": unknown setting '" + name + "' (known: " + DISPLAY_VIEW_SCALE
                        + ", " + DEFAULT_PREFIX + "<" + String.join("|", TYPES) + ">)");
                    continue;
                }
                if (scaleSeen) {
                    problems.add("line " + number + ": " + name + " is set twice; keeping the first (" + scale + ")");
                    continue;
                }
                double parsed;
                try {
                    parsed = Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    parsed = Double.NaN;
                }
                if (!(parsed == 0 || (parsed >= MIN_SCALE && parsed <= MAX_SCALE))) {
                    problems.add("line " + number + ": " + name + " must be 0 (off) or " + MIN_SCALE + "-" + MAX_SCALE
                        + ", got '" + value + "'");
                    continue;
                }
                scale = parsed;
                scaleSeen = true;
                continue;
            }
            if (!NAME.matcher(name).matches()) {
                problems.add("line " + number + ": category name '" + name
                    + "' must be 1-32 characters of a-z, 0-9 and _");
                continue;
            }
            Integer range = blocks(name, value, number, problems);
            if (range == null) continue;
            if (ranges.containsKey(name)) {
                problems.add("line " + number + ": category '" + name + "' is defined twice; keeping the first ("
                    + ranges.get(name) + ")");
                continue;
            }
            ranges.put(name, range);
        }
        return new RangeCategories(Collections.unmodifiableMap(ranges), Collections.unmodifiableMap(typeDefaults),
            scale, List.copyOf(problems));
    }

    /** A whole number of blocks in range, or null after reporting the problem. */
    private static Integer blocks(String name, String value, int number, List<String> problems) {
        int range;
        try {
            range = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            problems.add("line " + number + ": range of '" + name + "' must be a whole number of blocks"
                + (name.startsWith(DEFAULT_PREFIX) ? " or " + USE_SPIGOT : "") + ", got '" + value + "'");
            return null;
        }
        if (range < MIN_RANGE || range > MAX_RANGE) {
            problems.add("line " + number + ": range of '" + name + "' must be " + MIN_RANGE + "-" + MAX_RANGE
                + " blocks, got " + range);
            return null;
        }
        return range;
    }

    /** The {@code @default.<type>} override for a spigot.yml type, or {@code configured} (the spigot.yml value). */
    public int typeDefault(String type, int configured) {
        Integer override = typeDefaults.get(type);
        return override == null ? configured : override;
    }

    /** The {@code @default.<type>} overrides that are set, by spigot.yml type. */
    public Map<String, Integer> typeDefaults() {
        return typeDefaults;
    }

    /** 0 when display limiting is off. */
    public double displayViewScale() {
        return displayViewScale;
    }

    /**
     * The farthest a client with entity distance {@link #displayViewScale()} draws a display of {@code viewRange},
     * rounded up, at least {@link #MIN_RANGE}; {@link #NONE} when display limiting is off or the view range is not a
     * positive finite number.
     */
    public int displayLimit(float viewRange) {
        if (displayViewScale == 0 || !(viewRange > 0) || Float.isInfinite(viewRange)) return NONE;
        double blocks = Math.ceil(viewRange * DISPLAY_BLOCKS_PER_VIEW_RANGE * displayViewScale);
        return (int) Math.max(MIN_RANGE, Math.min(Integer.MAX_VALUE, blocks));
    }

    /** The widest range among the category tags in {@code tags}, or {@link #NONE}. */
    public int rangeFor(Iterable<String> tags, UnknownSink unknown) {
        int best = NONE;
        for (String tag : tags) {
            if (!tag.startsWith(TAG_PREFIX)) continue;
            String name = tag.substring(TAG_PREFIX.length());
            Integer range = ranges.get(name);
            if (range == null) {
                unknown.accept(name);
            } else if (range > best) {
                best = range;
            }
        }
        return best;
    }

    public static boolean isCategoryTag(String tag) {
        return tag != null && tag.startsWith(TAG_PREFIX);
    }

    public Map<String, Integer> ranges() {
        return ranges;
    }

    public List<String> problems() {
        return problems;
    }

    /** Told about a category tag whose name is not configured. */
    @FunctionalInterface
    public interface UnknownSink {
        void accept(String name);
    }
}
