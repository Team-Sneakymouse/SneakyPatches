# Resource pack reconciliation

Velocity-only patch for CE Proxy's configuration-phase pack handling. The
destination owns the complete ordered pack list, including an empty selection.
User-approved policy for this network; no managed-UUID allowlist is required.

## Behavior

CE Proxy still handles hashes, installed acknowledgements and UUID aliases.
SneakyPatches adds ordered per-connection tracking. At a configuration boundary
it starts a fresh destination list. As requests arrive, it removes old entries
that would put the next request in the wrong position. At configuration end it
removes any unrequested tail before forwarding the end packet.

- Equal selections produce no pack pushes or removals.
- Removal-only changes do not reapply retained packs.
- Insertion, changed hashes or reordering can reapply the affected suffix. A real
  Minecraft push appends even when replacing the same UUID. Unchanged ZIPs should
  load from Minecraft's cache, subject to client acceptance testing.
- Reapplied packs use real acknowledgements. There is no buffering until
  configuration end, which would deadlock against CE's acknowledgement gate.
- Explicit PLAY-phase removals and admin/dev refresh keep tracking synchronized.
- Backend deployment alone does not initiate any pack changes for connected users.
- A failed required removal closes that connection instead of admitting a player
  with a partially reconciled stack.

Duplicate physical pack identities within a destination list fail explicitly.
Different UUID aliases for the same installed pack across servers are supported.
Clients older than 1.20.3 retain CE's original behavior. Acceptance scope is
1.21.4 and 26.2 only.

## Targets and loading

Tested with Ignite 1.2.1, Java 25, Velocity
`4.2.1-SNAPSHOT (git-a6f9de95-b31)` and unchanged CE Proxy 26.8.1. No maximum
Velocity version is imposed. Required injections and a pre-main hook check
detect structural incompatibility; rerun behavior tests after upgrades.

The bootstrap scans the configured plugin directory for CE Proxy's entry class,
then registers that one JAR through IgniteAgent.addJar and
EmberClassLoader.addTransformationPath. The former also lets Ignite's ASM frame
writer resolve CE class hierarchies. Velocity's parent-first plugin loader uses
the transformed definitions. No plugin JAR is edited or bundled into this mod.
Multiple matching CE Proxy JARs are rejected. On Paper these steps never run.

Velocity also has its own backend hash deduplication before CE Proxy's raw packet
handler. The patch redirects only those checks in ConfigSessionHandler and
BackendPlaySessionHandler so every backend request reaches CE Proxy. It does not
change resource packs sent by proxy plugins or the public Player API. Required
redirect counts fail startup when Velocity moves those call sites.

## Verification

`gradlew build` checks the order algorithm, failure handling, platform selection,
the existing Paper target contract and final JAR contents.

For the transformed-handler proof, use an isolated directory with Ignite at
`ignite.jar`, the target Velocity at `velocity.jar`, CE Proxy in `plugins/`, and
the root SneakyPatches JAR in `mods/`.

Build `gradlew :resource-pack-reconciliation:igniteProofJar` and place the resulting
`patches/resource-pack-reconciliation/build/libs/sneakypatches-proof.jar` in that
test directory's `mods/`. Run:

```sh
java -Xmx512m -Dignite.locator=velocity -Dignite.jar=velocity.jar -Dignite.target=com.sneakyrp.sneakypatches.packs.IgniteProof -jar ignite.jar
```

This runs real transformed classes with Netty EmbeddedChannel, starts no proxy
listener and connects to no backend. Require both `PASS transformed CE packet
handlers` messages for `V_1_21_4` and `V_26_2`; Ignite can log a launch exception
without returning a nonzero exit code. The proof covers same-list filtering,
removal, middle insertion, reorder, content replacement, cross-server aliases,
explicit alias removal, retry after download failure and empty selections.

The root `paperProofJar` task similarly builds a test-only mod. Use the same
SneakyPatches JAR with Ignite's Paper locator and
`-Dignite.target=com.sneakyrp.sneakypatches.bootstrap.PaperProof`. It checks the
Paper injection and that Velocity patches are absent without starting a world.

Live acceptance passed on both supported clients with the seven/eight-pack
selection. A fresh dev connection omitted music, switching to backend inserted
music in the correct priority order, and returning to dev removed music again.
The unchanged packs were retained through the client cache. Required-pack failure
and explicit refresh had already passed in the resource-pipeline acceptance tests.
Rollback restores the original startup command and removes SneakyPatches from the
proxy mod directory; the upstream plugin JARs remain unchanged.

## Local results, September 19, 2026

- Clean Gradle build and bundle verification passed, including 106,276 order
  transitions and failure/duplicate-request cases.
- Real Ignite-transformed CE packet-handler proof passed for both endpoint
  protocols. Correcting the client model to append UUID replacements first
  reproduced an ordering failure; suffix reconciliation then fixed it.
- Full isolated Velocity 4.2.1-SNAPSHOT build 31 startup passed, including the
  pre-main hook verification and discovery of the unchanged CE Proxy plugin.
- The same final JAR passed Paper 26.2 build 116 transformation verification:
  item merge injection present, Velocity patches inactive. No world was started.
- The first live 0.2.0 test exposed a missing layer in the model. Velocity filtered
  the seven unchanged backend requests before CE Proxy could observe them. The
  patch therefore interpreted music as the complete destination list, removed the
  seven previous packs and forwarded music alone. Client log replay records the
  exact one-of-eight failure.
- Version 0.2.1 redirects Velocity's two backend-only prefilters to CE Proxy and
  checks the current call sites. The production proxy deployment passed the full
  seven/eight-pack switch on real 1.21.4 and 26.2 clients, including insertion,
  priority, removal and cache reuse.
