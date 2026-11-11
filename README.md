# SneakyPatches

Independent Paper and Velocity Mixin patches packaged as one Ignite mod. Each patch owns its
source, Mixin configuration, tests, and behavior notes under `patches/`. The root
build combines their compiled output and generates `ignite.mod.json`.

## Build

Use JDK 25. The Gradle wrapper downloads the build tool and dependencies.

```powershell
.\gradlew.bat clean build
```

On Linux or macOS, run `./gradlew clean build`.

The installable artifact is `build/libs/SneakyPatches-<version>.jar` (not the
`-sources` JAR). The version is the commit date in UTC and its 12-character hash,
`yyyy.MM.dd-<hash>`, so builds need a git checkout. Patch projects do not produce
separate JARs. Server classes and Sponge Mixin remain compile-only.

The Paper target is `26.2.build.116-stable`, corresponding to the supplied
`26.2-116-37dc545` investigation, with Ignite `1.2.1` and Java 25. Version pins live
in `gradle.properties`. The Velocity test target is the installed
`4.2.1-SNAPSHOT (git-a6f9de95-b31)` with CE Proxy 26.8.1. There is no maximum
Velocity version restriction. Future builds must still pass the hook and runtime
tests; an unrestricted version range is not a compatibility guarantee.

The Velocity patch compiles against the upstream CE Proxy and Velocity JARs, which
are not published to Maven. The build downloads the pinned
[CE Proxy 26.8.1](https://github.com/Xiao-MoMi/craft-engine-proxy/releases/tag/26.8.1)
velocity plugin (SHA-256 `e68aa410c21d480fb7d602031535735a4bd28016ac120aa8d3b0f4695dd3b9a8`)
and Velocity `4.2.1-SNAPSHOT` build 31 from PaperMC (SHA-256
`b2879bb60eba6be23e99fdd659084b7adbe1190d42b7ddf296356331f8ea7c9a`) once into
`<Gradle user home>/caches/sneakypatches/`, and fails if either checksum differs.
To build against other copies, for example the Velocity executable a proxy runs,
supply `-PcraftEngineProxyJar=<path>` or `-PvelocityJar=<path>`; these are used
without a checksum. Both are compile-only and are not included in SneakyPatches.

## Included patches

| Module | Behavior |
| --- | --- |
| [early-item-merge-cancellation](patches/early-item-merge-cancellation/README.md) | Returns at the start of item neighbor merging, globally |
| [resource-pack-reconciliation](patches/resource-pack-reconciliation/README.md) | Velocity + CE Proxy: reconciles the ordered destination pack stack during configuration |
| [tracking-range-categories](patches/tracking-range-categories/README.md) | Paper: per-entity tracking ranges by scoreboard tag (`tracking.<name>`, or any existing tag mapped with `@tag.<tag> = <category>`), reloadable with `/trackingranges` |
| [tracker-pair-recheck](patches/tracker-pair-recheck/README.md) | Paper: checks a still entity's players every few ticks (`-Dsneakypatches.trackerRecheckTicks`, default 5) instead of every tick |

Platform selectors return the relevant mixins before Mixin discovers targets.
Paper never resolves Velocity/CE Proxy targets. Velocity never resolves Paper
targets. Without CE Proxy installed, its patch is inactive. Each configuration
is required; the Velocity patch also verifies that its hooks applied before
Velocity's main method starts. There is no Bukkit plugin lifecycle.
Independent ownership does not guarantee compatibility if future patches modify
the same method. Review those interactions explicitly.

## Publishing

Pushes to `main` and manual runs of the `Publish Maven` workflow call the shared
[`Team-Sneakymouse/ci`](https://github.com/Team-Sneakymouse/ci) Gradle workflow. It
runs `build` (all patch tests and `verifyBundle`), then publishes the JAR, sources JAR
and POM to `https://maven.sneakyrp.com/releases` as
`io.github.team-sneakymouse:SneakyPatches:<version>`. Pushes that change only
Markdown files are skipped.

The POM sets `sneakyrp.deployment.type` to `ignite-mod`, which tells deployment
automation to install the JAR in Ignite's `mods/` directory. Publishing does not
restart any server; an update applies on the next restart.

The repository is public, so it inherits the `INFISICAL_IDENTITY_ID` and
`INFISICAL_DOMAIN` organization secrets; the Infisical identity must allow OIDC from
`Team-Sneakymouse/SneakyPatches`. Infisical project `lords-of-minecraft`, environment
`prod`, path `/Maven` provides `MAVEN_USERNAME` and `MAVEN_PASSWORD`.
`publishToMavenLocal` checks the publication without credentials.

## Install

1. Run the verification checklist on an isolated copy of the target server.
2. Stop the server and place the root JAR in Ignite's `mods` directory, or the
   directory configured by `-Dignite.mods`. Remove older SneakyPatches JARs.
3. Start through Ignite and check its mod discovery and Mixin logs.

On Velocity, keep the original Velocity and CE Proxy JARs. Start with Java 25:

```sh
java -Xmx1G -Dignite.locator=velocity -Dignite.jar=velocity.jar -jar ignite.jar
```

CE Proxy remains in `plugins/`. A non-default plugin directory can be specified
with `-Dsneakypatches.plugins=/path/to/plugins`. SneakyPatches registers that one
JAR with Ignite's library and transformation loaders so Mixin can patch it.
Do not copy CE Proxy into `mods/` or add duplicate CE Proxy JARs.

Configuration-phase packs are the destination's complete authoritative list.
This policy includes an empty list. Review it before using the patch on a proxy
where independent plugins send persistent packs. Installation on the shared
proxy requires an announced maintenance window. Version 0.2.1 is live and has
passed pack insertion, ordering and removal tests on 1.21.4 and 26.2 clients.

Updating or removing patches requires a full server restart. Removing this JAR
restores the underlying server behavior on the next start. Other installed
plugins can still cancel item merges.

## Add a patch

1. Create `patches/<patch-id>/` and register it in `settings.gradle.kts` using
   a flat project name, following the existing module.
2. Add compile-only dependencies in `build.gradle.kts`. Paper patches use
   `io.papermc.paperweight.userdev` and the pinned `paperweight.paperDevBundle`.
3. Put Java code in a distinct package below `com.sneakyrp.sneakypatches`.
   Keep patch code self-contained and avoid dependencies on another patch or
   Bukkit plugin classes.
4. Add `src/main/resources/mixins.sneakypatches.<patch-id>.json`. Set `required`
   to `true`, use a platform selector `plugin`, leave `mixins` empty, and list
   that patch's classes in `patchMixins` for bundle verification. The selector's
   `getMixins()` must return those names only on the matching platform.
   Require each injection to match.
   Use runtime names and `remap = false` for this Paper target.
5. Add a README describing the target, changed behavior, and upgrade checks.
   Add tests that check the target assumptions where useful.
6. Run `clean build`. The root build automatically registers the included
   module's configuration and includes its main output in the single JAR.

To omit a patch from a build, remove its inclusion and directory mapping from
`settings.gradle.kts`, then run `clean build`. Dependencies added to a patch are
not automatically shaded into the bundle. A patch needing an additional runtime
library must make an explicit packaging decision.

## Verification boundaries

`build` runs patch tests and `verifyBundle`. The latter verifies configuration
registration, required configs, listed Mixin class presence, and exclusion of
third-party classes. Duplicate archive paths fail the build.

The first patch's target test reads the real pinned Paper class without starting
Minecraft. It checks the method signature, tick call site, and neighbor-query
call. It does not apply Mixin or simulate item lifecycle. A successful build is
not a substitute for an Ignite launch and the patch's server acceptance checks.

The Velocity tests include all 106,276 ordered-subset transitions of five packs.
An additional test-only Ignite mod exercises the actual transformed CE session
and packet listeners for both supported client protocols. See the patch README
for running that proof. Never install either `*-proof.jar` on a real server.

## References

- [Ignite mod packaging](https://github.com/vectrix-space/ignite#configuring-your-mod)
- [Ignite 1.2.1 dependency versions](https://github.com/vectrix-space/ignite/blob/v1.2.1/gradle/libs.versions.toml)
- [Official Ignite mod template](https://github.com/vectrix-space/ignite-mod-template)
- [Paper userdev and unobfuscated 26.1+ builds](https://docs.papermc.io/paper/dev/userdev/)
