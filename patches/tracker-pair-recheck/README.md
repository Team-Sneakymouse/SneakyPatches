# Tracker pair re-check

Paper-only. Cuts the entity tracker's per-tick work by checking an entity that has not moved against its candidate
players once every few ticks instead of every tick.

## Behavior

Every tick, Paper's tracker checks every loaded entity against every player whose view-distance square covers it:
distance, chunk sent, `canSee`, events. Most entities on a server never move (ground items, displays, furniture,
item frames), so for them the answer only changes when a player crosses the range edge or a chunk finishes sending.

With this patch, an entity whose position is exactly where it was at its last check is checked once every
`trackerRecheckTicks` ticks (default 5), spread across ticks by entity id so the work stays even. It is checked at
once, as in Paper, when:

- it moved at all since its last check (including riding a moving vehicle),
- its tracker's range changed (tracking-range-categories: a `tracking.` tag or `@tag.` alias added or removed, or
  `/trackingranges reload`),
- it was just added to the tracker (spawned, loaded, or entered the world): Paper checks every player immediately,
- it is a player.

Paper still sends every entity's changes (metadata, equipment, velocity) every tick; only the player checks are
skipped. A skipped tick changes nothing, so the next check does all the work Paper would have, including dropping
players who left in between.

What does not wait, because Paper handles it outside the tracker tick:

- a player joining, respawning or changing world (checked against every entity at once),
- `Player#hideEntity` / `showEntity` (and plugins using them for vanish).

The cost: a still entity can appear or disappear up to `trackerRecheckTicks - 1` ticks late when a player crosses
its range edge or a chunk arrives (4 ticks = 0.2 s by default). Walking, that is under a block past the edge;
sprinting about 1 block; flying with an elytra up to about 6 blocks.

## Setting

A JVM flag, read at startup:

```sh
-Dsneakypatches.trackerRecheckTicks=5
```

`1` (or less) turns the patch off: every entity is checked every tick, exactly as in Paper. Higher values save more
and lag more. Changing it needs a restart. Once the server is up, the log says what is in force:

```text
[SneakyPatches] Tracker pair re-check: still entities are checked every 5 ticks
```

## Targets

- `ChunkMap$TrackedEntity.moonrise$tick(NearbyPlayers$TrackedChunk)`: cancelled at HEAD for a still entity whose turn
  has not come. Priority 900, ahead of other patches on the same method, so a skip skips them too. Not cancelled when
  the chunk is null (Paper then clears the entity's players).
- `ChunkMap$TrackedEntity.range`: read to notice range changes other patches make at runtime. A passenger's
  range change reaches its still vehicle on the vehicle's next turn (up to 4 ticks).
- `MinecraftServer.currentTick`: the turn counter.

`PaperTargetTest` checks that the skipped method holds the player checks and not the change sending, that
`newTrackerTick` sends changes separately, that new trackers and joining players are checked at once in
`ChunkMap.addEntity`, and that hide/show update the tracker directly. A failure after a Paper upgrade means: reread
the tracker before shipping.

## Checks

Verified on Paper `26.2-116-37dc545` with Ignite `1.2.1`, in the tracking lab (network bots comparing the tracker,
Paper's rule and what each client received):

- The full tracking-range suite passes, with moving-player tests allowing the documented lag at the edge.
- `edge_latency` walks a player across range edges a step per tick: Paper tracks and untracks with 0 ticks' lag; with
  this patch at 5, 0-4 ticks (mean 2), never more.
- Live layout (the hub area from the 2026-11-09 backup, ~17,500 entities, live's `spigot.yml`, view distance 12),
  40 players, with `@tag.grounditem = short`: tracker time 11.1 ms to 5.9 ms spread through town, 11.2 ms to 3.2 ms
  all at the hub.

After upgrades, rerun the tests, start through Ignite, and rerun the lab's `edge_latency` and moving tests.
