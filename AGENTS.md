# Agent instructions

Follow `../EmptyPlugin/AGENTS.md` for the shared rules.

## Repo-specific rules

- This is not a Bukkit plugin: no `JavaPlugin`, no `plugin.yml`, no Bukkit API. Keep each patch self-contained under `patches/<name>` in package `com.sneakyrp.sneakypatches.<patch>`, and never add one patch's dependencies to another.
- Mixins are gated by the platform selectors (`bootstrap/PlatformPatches`, `PaperPatches`, `VelocityPatches`) so Paper never resolves Velocity targets and the reverse. Register new mixins through them and add a target test like `ItemEntityTargetTest` / `VelocityDedupTargetTest`.
- Version pins (Paper, Mixin, Velocity, CE Proxy, Ignite) live in `gradle.properties` and the README. Don't bump them casually; re-verify against the hook tests and an isolated server first.
- Build jars come from outside: pass `-PcraftEngineProxyJar` and `-PvelocityJar` (or use ignored `.scratch/dependencies/`). Never commit them, and keep the README's tested CE Proxy SHA-256 accurate.
- Proxy-affecting changes (resource-pack-reconciliation) need an announced maintenance window and a full proxy restart. Don't ship a version bump without Rawb confirming.
