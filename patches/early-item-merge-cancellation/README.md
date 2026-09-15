# Early item merge cancellation

Cancels `net.minecraft.world.entity.item.ItemEntity.mergeWithNeighbours()V` at
`HEAD`, before the neighbor query, item comparisons, and Bukkit `ItemMergeEvent`.
The policy applies to every item in every world, including existing and reloaded
entities. No item metadata or persisted flags are added.

The callback only cancels this method. Item ticking, pickup, inventory stacking,
and natural despawn remain outside the injected callback. Preventing merges also
prevents the age and pickup-delay transfers caused by successful merges. This
matches the intended global separate-drop policy.

Merge listeners, including MythicMobs' listener, will not receive events from
this path. Plugins depending on those events need compatibility testing.

The injection uses `require = 1`, `allow = 1`, and a required configuration.
These detect missing or excessive injection matches. They cannot detect all
changes to server behavior or interference from other mods.

## Server acceptance checklist

Use an isolated server with Paper `26.2-116-37dc545`, Ignite `1.2.1`, and the
production plugin/mod set. Enable `-Dmixin.debug.export=true` for the validation
launch and inspect the exported `ItemEntity` bytecode to confirm the cancellable
callback precedes the neighbor query. Check logs for successful mod discovery
and absence of Mixin application errors.

- Identical overlapping drops stay separate and retain their total quantities.
- Full and partial player pickup, full inventories, mob pickup, hoppers, and
  cancelled pickup events retain their baseline behavior.
- Picked-up items stack normally and preserve lore, components, and custom identity.
- Normal and configured despawn timing and cancelled `ItemDespawnEvent` work as
  before. Measure lifetime in ticks.
- Old drops, new drops, chunk unload/reload, and a restart follow the same policy.
- Mythic fancy-drop effects remain attached and clean up on pickup/removal.
- Profile equal piles of 100, 500, 1,000, and 3,000 items plus a spread-out control.
  Confirm neighbor merge queries and merge listeners disappear from this path.
  Repeat runs and compare tick times and entity counts against the existing
  cancellation-plugin baseline. No performance improvement is claimed yet.

After upgrades, rerun the bytecode target test and inspect the surrounding item
tick/merge code before repeating these checks.

## Design inputs

- [Original PRD](D:/Projects/SneakyMisc/.scratch/item-merge-cancellation/PRD.md)
- [Original Ignite research](D:/Projects/SneakyMisc/.scratch/item-merge-cancellation/ignite-research.md)
- [Captured Paper ItemEntity patch](https://github.com/PaperMC/Paper/blob/37dc545/paper-server/patches/sources/net/minecraft/world/entity/item/ItemEntity.java.patch)

The original research files are local context. The behavior and validation
requirements needed to maintain this module are summarized above.
