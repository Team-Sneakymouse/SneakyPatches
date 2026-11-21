# Idle send skip

Paper-only. Stops the entity tracker from checking entities nobody is watching for changes.

## Behavior

Every tick, Paper calls `ServerEntity.sendChanges()` for every tracked entity that has a viewer or stands in an
entity-ticking chunk. The call compares the entity's position, rotation, passengers and metadata with what was last
sent and sends the differences to its viewers. With no viewer it sends nothing, but it still does every comparison.
Outside entity-ticking chunks Paper already skips it; in force-loaded areas every chunk is entity-ticking, so every
display, ground item and item frame is checked every tick whether or not anyone is near.

With this patch, the tracker loop leaves the call out while an entity has no viewer, in ticking chunks too. When its
first viewer arrives, the entity's last-sent position, rotation, velocity and metadata are set to its current state
before the spawn packet is built, exactly as Paper sets them for a newly created tracker. From then on Paper sends its
changes every tick as before.

Not skipped:

- any entity whose tracker has at least one viewer, by Paper's own check (`moonrise$hasPlayers`, which reads whatever
  viewer set a plugin has put in the tracker),
- players, whose own connection is sent their changes, and plugin NPCs that subclass Paper's player class (Citizens'
  player NPCs),
- `sendChanges()` called by anything but the tracker loop: plugins that run a `ServerEntity` of their own (Citizens'
  packet trackers) are not touched.

What still runs on a skipped tick: the entity's pre-sync hook (`updateDataBeforeSync`). For living entities this is
where potion effects set the invisible and glowing flags, which the server itself reads (mob targeting).

What a skip leaves undone until the first viewer arrives: dirty metadata and attribute flags stay set, so that
viewer is sent those values twice (in the spawn data and in the first send); a pending velocity update is sent then
too. Passenger changes made while nobody watched are sent with the spawn data, as in Paper. The three entity flags
`sendChanges()` clears (`hurtMarked`, `needsSync`, `syncPosition`) wait for that first send.

If a viewer is ever added without spawn data (no Paper path does this), the next send is forced to be an absolute
position sync, the same way Paper handles hard-colliding entities.

## Setting

A JVM flag, read at startup:

```sh
-Dsneakypatches.idleSendSkip=false
```

turns the patch off: every entity is sent exactly as in Paper. Changing it needs a restart. Once the server is up,
the log says what is in force:

```text
[SneakyPatches] Idle send skip: entities without viewers are not checked for changes
```

## Targets

- `ChunkMap.newTrackerTick()`: its one `ServerEntity.sendChanges()` call is redirected and left out for a non-player
  entity with no viewers.
- `ServerEntity.sendPairingData(ServerPlayer, Consumer)`: at HEAD, after a skip, sets the fields the constructor
  sets (`positionCodec` base, `lastSentMovement`, `lastSentYRot`, `lastSentXRot`, `lastSentYHeadRot`, `wasOnGround`,
  `trackedDataValues`) from the entity's current state.
- `ServerEntity.synchronizer`: the tracker, asked whether it has viewers. `ServerEntity.teleportDelay`: set to 9999
  for the fallback above.

`PaperTargetTest` checks the field types, that the refresh sets everything the constructor sets, that the spawn
packet reads the current position and the last-sent rotation and velocity, that every new viewer goes through
`sendPairingData`, that the tracker loop calls `sendChanges` exactly once next to Paper's own viewer check, that the
tracker is its `ServerEntity`'s synchronizer, what `sendChanges` does besides sending (the pre-sync hook, events only
for players, three entity flags), and that a large `teleportDelay` leads to a position sync. A failure after a Paper
upgrade means: reread `ServerEntity` and the tracker loop before shipping.
