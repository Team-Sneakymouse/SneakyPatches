# Tracking range categories

Paper-only. Lets one entity be tracked (sent to players) farther or nearer than the `spigot.yml`
`entity-tracking-range` for its type, so the server can run short default ranges while the few entities that must
be seen from far away keep a long one.

## Behavior

An entity joins a category with the scoreboard tag `tracking.<name>` (`addScoreboardTag`, `/tag`, MagicSpells).
The widest tag wins, tag changes apply at once, and tags persist with the entity. A vehicle is tracked as far as
its widest passenger.

`config/sneakypatches/tracking-ranges.properties` is written on first start. As written it changes nothing for
untagged entities:

```properties
short = 32
medium = 64
long = 128
extreme = 256
# @tag.<scoreboard tag> = <category> for tags other plugins already set. Example:
#   @tag.grounditem = short
# One line per spigot.yml type; a number replaces spigot.yml for untagged entities.
@default.display = spigot
# 0.5-5.0: untagged displays are tracked no farther than clients draw them.
@display_view_scale = 0
```

### Tags other plugins already set

`@tag.<scoreboard tag> = <category>` puts every entity carrying that tag in the category, exactly as if it also had
`tracking.<category>`. Use it for entities another plugin creates and tags, without changing that plugin: drop-tech's
ground items carry `grounditem`, so `@tag.grounditem = short` sends them only 32 blocks instead of the 128 every
display gets from `spigot.yml`. The tag and the category apply the same way, live, through `/tag` and on reload:

- Tags are case-sensitive (`grounditem` is not `GroundItem`), 1-64 characters of `A-Z a-z 0-9 _ . + -`.
- `tracking.<name>` tags still apply; with several, the widest range wins, whether it came from a tag or an alias.
- The category must exist in the same file (it may be defined below the alias). An alias to an unknown category, a
  `tracking.` tag, a bad tag or a tag set twice is a bad line: refused on reload, skipped and logged at startup.
- Removing the alias line and reloading puts those entities back on their type's range.

`/trackingranges` lists each alias as `tag grounditem -> tracking.short = 32 blocks`.

### Commands

`/trackingranges` shows what is in force; `/trackingranges reload` applies the edited file to every loaded entity
without a restart (permission `sneakypatches.trackingranges`). Reload refuses the whole file if any line is bad and
keeps the current ranges. At startup, bad lines are skipped and logged, so anything wrong falls back to the normal
`spigot.yml` range. `entity-broadcast-range-percentage` and each player's view distance still apply.

## Targets

- `TrackingRange.getEntityTrackingRange`: the six `spigot.yml` type reads go through `@default.<type>`; the result
  goes through the category and display limit.
- `ChunkMap$TrackedEntity`: stores the assigned range; passengers use their tracker's range instead of recomputing.
- `Entity.addTag`/`removeTag` and `Display.setViewRange`: recompute the stored range when the tag is a category tag
  or has an `@tag.` alias.
- `PaperCommands.registerCommands`: registers `/trackingranges` next to `/paper`.

All injections use `require = 1`. Editing `getScoreboardTags()` directly bypasses `addTag` and applies only on the
next chunk load. Ranges set by reflection (SneakyArcade, ModelEngine) are kept on reload but replaced when a
category tag is added. `tracking-range-y` ignores categories.

## Checks

`PaperTargetTest` reads the pinned Paper classes and checks every assumption above; `RangeCategoriesTest` covers
the file format, aliases and bad input. The lab suite's `tag_aliases` test covers aliases live: adding and removing
the tag, a category tag beating the alias, case sensitivity, reload to another category, removing the alias, a
refused alias, and a restart. Verified on Paper `26.2-116-37dc545` with Ignite `1.2.1` and network bots comparing
the tracker, Paper's rule and the entities each client received. After upgrades, rerun the tests, start through
Ignite, and reread Paper's `ChunkMap` tracker changes for new range sources.
