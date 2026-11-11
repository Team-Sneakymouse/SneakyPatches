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

    /**
     * Setting prefix: {@code @tag.<scoreboard tag> = <category>} puts every entity carrying that existing scoreboard
     * tag in the category, as if it also had {@code tracking.<category>}. For tags other plugins already set.
     */
    public static final String TAG_ALIAS_PREFIX = "@tag.";
    /** Characters Minecraft allows in a scoreboard tag (unquoted /tag argument). */
    private static final Pattern SCOREBOARD_TAG = Pattern.compile("[A-Za-z0-9_.+-]{1,64}");

    public static final RangeCategories EMPTY = new RangeCategories(Map.of(), Map.of(), Map.of(), 0, List.of());

    private final Map<String, Integer> ranges;
    private final Map<String, Integer> typeDefaults;
    /** Scoreboard tag -> category name, only for categories that exist. */
    private final Map<String, String> tagAliases;
    private final double displayViewScale;
    private final List<String> problems;

    private RangeCategories(Map<String, Integer> ranges, Map<String, Integer> typeDefaults,
                            Map<String, String> tagAliases, double displayViewScale, List<String> problems) {
        this.ranges = ranges;
        this.typeDefaults = typeDefaults;
        this.tagAliases = tagAliases;
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
        // Tag aliases may name categories defined later in the file: resolved after the loop.
        Map<String, String> aliasLines = new LinkedHashMap<>();
        Map<String, Integer> aliasLineNumbers = new LinkedHashMap<>();
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
            if (name.startsWith(TAG_ALIAS_PREFIX)) {
                String tag = name.substring(TAG_ALIAS_PREFIX.length());
                if (!SCOREBOARD_TAG.matcher(tag).matches()) {
                    problems.add("line " + number + ": '" + name + "' needs a scoreboard tag of 1-64 characters "
                        + "(A-Z, a-z, 0-9, _ . + -) after " + TAG_ALIAS_PREFIX);
                    continue;
                }
                if (tag.startsWith(TAG_PREFIX)) {
                    problems.add("line " + number + ": '" + tag + "' is already a category tag; " + TAG_ALIAS_PREFIX
                        + " is for other plugins' tags");
                    continue;
                }
                if (aliasLines.containsKey(tag)) {
                    problems.add("line " + number + ": " + name + " is set twice; keeping the first ("
                        + aliasLines.get(tag) + ")");
                    continue;
                }
                aliasLines.put(tag, value);
                aliasLineNumbers.put(tag, number);
                continue;
            }
            if (name.startsWith("@")) {
                if (!name.equals(DISPLAY_VIEW_SCALE)) {
                    problems.add("line " + number + ": unknown setting '" + name + "' (known: " + DISPLAY_VIEW_SCALE
                        + ", " + DEFAULT_PREFIX + "<" + String.join("|", TYPES) + ">, " + TAG_ALIAS_PREFIX
                        + "<scoreboard tag>)");
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
        Map<String, String> tagAliases = new LinkedHashMap<>();
        for (Map.Entry<String, String> alias : aliasLines.entrySet()) {
            String category = alias.getValue();
            if (!ranges.containsKey(category)) {
                problems.add("line " + aliasLineNumbers.get(alias.getKey()) + ": " + TAG_ALIAS_PREFIX + alias.getKey()
                    + " names no category '" + category + "' (categories: "
                    + (ranges.isEmpty() ? "none" : String.join(", ", ranges.keySet())) + ")");
                continue;
            }
            tagAliases.put(alias.getKey(), category);
        }
        // Problems in line order, whichever pass found them.
        problems.sort(java.util.Comparator.comparingInt(RangeCategories::lineOf));
        return new RangeCategories(Collections.unmodifiableMap(ranges), Collections.unmodifiableMap(typeDefaults),
            Collections.unmodifiableMap(tagAliases), scale, List.copyOf(problems));
    }

    /** The line number a problem message starts with ("line N: ..."). */
    private static int lineOf(String problem) {
        try {
            return Integer.parseInt(problem.substring("line ".length(), problem.indexOf(':')));
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
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

    /**
     * The widest range among the category tags in {@code tags} and the tags with an {@code @tag.} alias, or
     * {@link #NONE}.
     */
    public int rangeFor(Iterable<String> tags, UnknownSink unknown) {
        int best = NONE;
        for (String tag : tags) {
            Integer range;
            if (tag.startsWith(TAG_PREFIX)) {
                String name = tag.substring(TAG_PREFIX.length());
                range = ranges.get(name);
                if (range == null) {
                    unknown.accept(name);
                    continue;
                }
            } else {
                String category = tagAliases.get(tag);
                if (category == null) continue;
                range = ranges.get(category);
            }
            if (range > best) best = range;
        }
        return best;
    }

    public static boolean isCategoryTag(String tag) {
        return tag != null && tag.startsWith(TAG_PREFIX);
    }

    /** Whether {@code tag} decides a range here: a category tag, or a tag with an {@code @tag.} alias. */
    public boolean decidesRange(String tag) {
        return isCategoryTag(tag) || (tag != null && tagAliases.containsKey(tag));
    }

    /** {@code @tag.} aliases in force: scoreboard tag -> category name. */
    public Map<String, String> tagAliases() {
        return tagAliases;
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
