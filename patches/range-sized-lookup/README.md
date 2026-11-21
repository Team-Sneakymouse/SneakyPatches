# Range-sized lookup

Paper-only. Checks each tracked entity against the players near its tracking range instead of every player whose
view distance covers it.

## Behavior

Every tick, Paper's tracker checks an entity against every player in its chunk's view-distance list: with view
distance 16, players up to about 250 blocks away, also for a ground item tracked at 32 blocks. Each of those checks
ends in `updatePlayer`, which turns the player away on distance.

Moonrise keeps two smaller player lists per chunk: players within 3 chunks and players within 10 chunks. With this
patch the tracker reads the smallest list that still holds every player within the entity's range:

| Effective range | List |
| --- | --- |
| up to 48 blocks | players within 3 chunks |
| up to 160 blocks | players within 10 chunks |
| above | the view-distance list, as in Paper |

The range is the tracker's effective range, the value `updatePlayer` compares against: it includes the widest
passenger and `entity-broadcast-range-percentage`. Nothing after the lookup changes: every candidate still goes
through Paper's `updatePlayer` (distance, the player's view distance, chunk sent, `canSee`, events), and Citizens'
trackers, which override only `updatePlayer` and `removePlayer`, get the smaller list too.

Paper removes viewers who are no longer in the list only when the chunk's lists changed. When an entity switches to
another list (its range was changed by tracking-range-categories or a plugin, a wide passenger got on or off), the
patch makes Paper run that removal on the same tick.

Known difference: Citizens raises a tracker's range from an NPC's `TRACKING_RANGE` metadata inside `updatePlayer`,
so the raise happens when the first candidate player is checked. With a smaller list that is when a player comes
within the list's radius, not within view distance.

## Setting

A JVM flag, read at startup:

```sh
-Dsneakypatches.rangeSizedLookup=false
```

turns the patch off: the view-distance list is used for every entity. Changing it needs a restart. Once the server
is up, the log says what is in force:

```text
[SneakyPatches] Range-sized lookup: entities are checked against the players near their range
```

## Targets

- `ChunkMap$TrackedEntity.moonrise$tick(NearbyPlayers.TrackedChunk)`: its one `TrackedChunk.getPlayers(...)` call
  is redirected to choose the list.
- `ChunkMap$TrackedEntity.getEffectiveRange()`: read to choose it.
- `ChunkMap$TrackedEntity.lastTrackedChunk`: set to null on a list switch, which makes Paper's own removal pass run.

tracker-pair-recheck cancels the same tick at HEAD with a higher priority, so a skipped tick skips the lookup too.

`PaperTargetTest` checks that the tick looks up the view-distance list exactly once and reads `lastTrackedChunk`
after it, that the small lists are 3 and 10 chunks (48 and 160 blocks), that an add or remove in any list bumps the
chunk's one update count, that `updatePlayer` compares horizontal distance with the effective range, and that the
lists are refreshed whenever a player changes section. A failure after a Paper upgrade means: reread the tracker
tick and Moonrise's `NearbyPlayers` before shipping.
