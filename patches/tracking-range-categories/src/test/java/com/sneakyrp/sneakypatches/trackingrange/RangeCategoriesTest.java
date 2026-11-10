package com.sneakyrp.sneakypatches.trackingrange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RangeCategoriesTest {
    private static RangeCategories parse(String... lines) {
        return RangeCategories.parse(List.of(lines));
    }

    @Test
    void parsesCategoriesIgnoringCommentsBlanksAndSpacing() {
        var categories = parse("# comment", "", "  near = 32 ", "far=128", "   # indented comment");
        assertEquals(Map.of("near", 32, "far", 128), categories.ranges());
        assertTrue(categories.problems().isEmpty());
    }

    @Test
    void skipsEveryKindOfBadLineAndSaysWhich() {
        var categories = parse(
            "no_equals",            // 1
            "Near = 32",            // 2 uppercase
            "near-far = 32",        // 3 dash
            " = 32",                // 4 empty name
            "near = thirty",        // 5 not a number
            "near = 32.5",          // 6 not whole
            "near = 0",             // 7 too small
            "near = -5",            // 8 negative
            "near = 513",           // 9 too big
            "near = 99999999999",   // 10 overflow
            "ok = 64",              // 11 fine
            "ok = 96",              // 12 duplicate
            "a".repeat(33) + " = 5" // 13 name too long
        );
        assertEquals(Map.of("ok", 64), categories.ranges());
        assertEquals(12, categories.problems().size(), String.join("\n", categories.problems()));
        for (int line = 1; line <= 13; line++) {
            if (line == 11) continue;
            String prefix = "line " + line + ":";
            assertTrue(categories.problems().stream().anyMatch(p -> p.startsWith(prefix)), "no problem for " + prefix);
        }
    }

    @Test
    void boundsAreInclusive() {
        assertEquals(Map.of("tiny", 1, "huge", 512), parse("tiny = 1", "huge = 512").ranges());
    }

    @Test
    void untaggedAndOtherTagsGetNoCategory() {
        var categories = parse("far = 128");
        var unknown = new ArrayList<String>();
        assertEquals(RangeCategories.NONE, categories.rangeFor(Set.of(), unknown::add));
        assertEquals(RangeCategories.NONE,
            categories.rangeFor(Set.of("far", "tracking", "trackingfar", "x.tracking.far"), unknown::add));
        assertTrue(unknown.isEmpty());
    }

    @Test
    void tagPicksItsCategory() {
        var categories = parse("near = 32", "far = 128");
        assertEquals(32, categories.rangeFor(Set.of("tracking.near"), n -> fail()));
        assertEquals(128, categories.rangeFor(Set.of("unrelated", "tracking.far"), n -> fail()));
    }

    @Test
    void widestCategoryWinsWhateverTheOrder() {
        var categories = parse("near = 32", "far = 128", "mid = 64");
        assertEquals(128, categories.rangeFor(List.of("tracking.near", "tracking.far", "tracking.mid"), n -> fail()));
        assertEquals(128, categories.rangeFor(List.of("tracking.far", "tracking.near"), n -> fail()));
    }

    @Test
    void unknownCategoryIsReportedAndIgnored() {
        var categories = parse("far = 128");
        var unknown = new ArrayList<String>();
        assertEquals(RangeCategories.NONE, categories.rangeFor(List.of("tracking.fra"), unknown::add));
        assertEquals(128, categories.rangeFor(List.of("tracking.fra", "tracking.far"), unknown::add));
        assertEquals(List.of("fra", "fra"), unknown);
    }

    @Test
    void emptyTagSuffixIsUnknownNotACrash() {
        var unknown = new ArrayList<String>();
        assertEquals(RangeCategories.NONE, parse("far = 128").rangeFor(List.of("tracking."), unknown::add));
        assertEquals(List.of(""), unknown);
    }

    @Test
    void emptyFileHasNoCategories() {
        assertTrue(parse().ranges().isEmpty());
        assertTrue(RangeCategories.EMPTY.ranges().isEmpty());
    }

    @Test
    void displayViewScaleIsOffUnlessSet() {
        assertEquals(0, parse("far = 128").displayViewScale());
        assertEquals(RangeCategories.NONE, parse("far = 128").displayLimit(1.0f));
    }

    @Test
    void displayViewScaleParsesAndLimits() {
        var c = parse("@display_view_scale = 1.0", "far = 128");
        assertTrue(c.problems().isEmpty(), String.join("\n", c.problems()));
        assertEquals(Map.of("far", 128), c.ranges());
        assertEquals(64, c.displayLimit(1.0f));
        assertEquals(32, c.displayLimit(0.5f));
        assertEquals(256, c.displayLimit(4.0f));
        assertEquals(7, c.displayLimit(0.1f));                // 6.4 rounds up
        assertEquals(1, c.displayLimit(0.001f));              // never below 1
        assertEquals(RangeCategories.NONE, c.displayLimit(0f));
        assertEquals(RangeCategories.NONE, c.displayLimit(-1f));
        assertEquals(RangeCategories.NONE, c.displayLimit(Float.NaN));
        assertEquals(RangeCategories.NONE, c.displayLimit(Float.POSITIVE_INFINITY));
        assertEquals(96, parse("@display_view_scale = 1.5").displayLimit(1.0f));
        assertEquals(0, parse("@display_view_scale = 0").displayViewScale());
    }

    @Test
    void badSettingsAreSkippedAndReported() {
        var c = parse("@display_view_scale = 0.2", "@display_view_scale = 9", "@display_view_scale = lots",
            "@display_view_scale = NaN", "@display_scale = 1", "@display_view_scale = 2", "@display_view_scale = 3");
        assertEquals(2.0, c.displayViewScale());
        assertEquals(6, c.problems().size(), String.join("\n", c.problems()));
        assertTrue(c.problems().get(5).contains("set twice"));
        assertTrue(c.problems().get(4).contains("unknown setting"));
    }

    @Test
    void typeDefaultsParseAndOnlyReplaceTheTypesSet() {
        var c = parse("@default.display = 64", "@default.players = 96", "long = 128");
        assertTrue(c.problems().isEmpty(), String.join("\n", c.problems()));
        assertEquals(Map.of("display", 64, "players", 96), c.typeDefaults());
        assertEquals(Map.of("long", 128), c.ranges());          // not a category
        assertEquals(64, c.typeDefault("display", 128));
        assertEquals(96, c.typeDefault("players", 128));
        assertEquals(96, c.typeDefault("animals", 96));          // not set: spigot.yml value
        assertEquals(64, c.typeDefault("other", 64));
        assertEquals(400, parse("@default.misc = 400").typeDefault("misc", 96));  // can raise too
        assertEquals(Map.of(), parse().typeDefaults());
        for (String type : RangeCategories.TYPES) {
            assertEquals(Map.of(type, 32), parse("@default." + type + " = 32").typeDefaults());
        }
    }

    @Test
    void spigotMeansNoOverride() {
        var c = parse("@default.display = spigot", "@default.misc = SPIGOT", "@default.other = 48");
        assertTrue(c.problems().isEmpty(), String.join("\n", c.problems()));
        assertEquals(Map.of("other", 48), c.typeDefaults());
        assertEquals(128, c.typeDefault("display", 128));
        assertEquals(96, c.typeDefault("misc", 96));
        // A type is still set once only, whether by a number or by 'spigot'.
        var twice = parse("@default.display = spigot", "@default.display = 64");
        assertEquals(Map.of(), twice.typeDefaults());
        assertEquals(1, twice.problems().size());
        assertTrue(twice.problems().getFirst().contains("set twice; keeping the first (spigot)"));
        assertEquals(1, parse("@default.display = spigots").problems().size());
    }

    @Test
    void badTypeDefaultsAreSkippedAndReported() {
        var c = parse(
            "@default.item = 32",        // 1 unknown type
            "@default.Display = 32",     // 2 wrong case
            "@default. = 32",            // 3 empty type
            "@default.display = 0",      // 4 too small: 0 would stop tracking
            "@default.display = 513",    // 5 too big
            "@default.display = 6x4",    // 6 not a number
            "@default.display = 48",     // 7 fine
            "@default.display = 64",     // 8 set twice
            "@default = 64"              // 9 no type
        );
        assertEquals(Map.of("display", 48), c.typeDefaults());
        assertEquals(8, c.problems().size(), String.join("\n", c.problems()));
        for (int line : new int[] {1, 2, 3, 4, 5, 6, 8, 9}) {
            assertTrue(c.problems().stream().anyMatch(p -> p.startsWith("line " + line + ":")), "no problem for " + line);
        }
        assertTrue(c.problems().get(6).contains("set twice"));
        assertTrue(c.problems().getFirst().contains("players, animals, monsters, misc, display, other"));
    }

    @Test
    void tagAliasPutsAnotherPluginsTagInACategory() {
        var c = parse("short = 32", "long = 128", "@tag.grounditem = short", "@tag.Lights.Big+1 = long");
        assertTrue(c.problems().isEmpty(), String.join("\n", c.problems()));
        assertEquals(Map.of("grounditem", "short", "Lights.Big+1", "long"), c.tagAliases());
        assertEquals(32, c.rangeFor(Set.of("grounditem"), n -> fail()));
        assertEquals(128, c.rangeFor(Set.of("Lights.Big+1"), n -> fail()));
        assertEquals(RangeCategories.NONE, c.rangeFor(Set.of("GroundItem", "grounditem2", "short"), n -> fail()));
        assertTrue(c.decidesRange("grounditem"));
        assertTrue(c.decidesRange("tracking.anything"));
        assertFalse(c.decidesRange("GroundItem"));
        assertFalse(c.decidesRange(null));
    }

    @Test
    void aliasAndCategoryTagsTogetherWidestWins() {
        var c = parse("short = 32", "long = 128", "@tag.grounditem = short");
        assertEquals(128, c.rangeFor(List.of("grounditem", "tracking.long"), n -> fail()));
        assertEquals(128, c.rangeFor(List.of("tracking.long", "grounditem"), n -> fail()));
        // An unknown category tag next to an aliased tag: the alias still applies, the unknown one is reported.
        var unknown = new ArrayList<String>();
        assertEquals(32, c.rangeFor(List.of("tracking.nope", "grounditem"), unknown::add));
        assertEquals(List.of("nope"), unknown);
    }

    @Test
    void aliasMayNameACategoryDefinedLater() {
        var c = parse("@tag.grounditem = short", "short = 32");
        assertTrue(c.problems().isEmpty(), String.join("\n", c.problems()));
        assertEquals(32, c.rangeFor(Set.of("grounditem"), n -> fail()));
    }

    @Test
    void badAliasesAreSkippedAndReportedInLineOrder() {
        var c = parse(
            "short = 32",                    // 1 fine
            "@tag.grounditem = shrt",        // 2 no such category
            "@tag. = short",                 // 3 empty tag
            "@tag.has space = short",        // 4 not a tag character
            "@tag.tracking.short = short",   // 5 already a category tag
            "@tag.drops = short",            // 6 fine
            "@tag.drops = short",            // 7 set twice
            "@tag." + "a".repeat(65) + " = short", // 8 too long
            "@tags.drops = short",           // 9 unknown setting
            "@tag.removed = gone"            // 10 category removed from the file
        );
        assertEquals(Map.of("drops", "short"), c.tagAliases());
        assertEquals(8, c.problems().size(), String.join("\n", c.problems()));
        int previous = 0;
        for (String problem : c.problems()) {
            int line = Integer.parseInt(problem.substring(5, problem.indexOf(':')));
            assertTrue(line > previous, "not in line order: " + c.problems());
            previous = line;
        }
        assertTrue(c.problems().getFirst().contains("names no category 'shrt' (categories: short)"));
        assertTrue(c.problems().get(4).startsWith("line 7: @tag.drops is set twice; keeping the first (short)"));
        assertTrue(c.problems().get(6).startsWith("line 9: unknown setting"));
        // Without the alias, the tag decides nothing: the entity keeps its normal range.
        assertEquals(RangeCategories.NONE, c.rangeFor(Set.of("grounditem"), n -> fail()));
        assertFalse(c.decidesRange("grounditem"));
    }

    @Test
    void noAliasesByDefault() {
        assertEquals(Map.of(), parse("short = 32").tagAliases());
        assertEquals(Map.of(), RangeCategories.EMPTY.tagAliases());
    }

    @Test
    void categoryTagDetection() {
        assertTrue(RangeCategories.isCategoryTag("tracking.far"));
        assertFalse(RangeCategories.isCategoryTag("far"));
        assertFalse(RangeCategories.isCategoryTag(null));
    }
}
