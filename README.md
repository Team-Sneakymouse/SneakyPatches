# SneakyPatches

Independent Paper Mixin patches packaged as one Ignite mod. Each patch owns its
source, Mixin configuration, tests, and behavior notes under `patches/`. The root
build combines their compiled output and generates `ignite.mod.json`.

## Build

Use JDK 25. The Gradle wrapper downloads the build tool and dependencies.

```powershell
.\gradlew.bat clean build
```

On Linux or macOS, run `./gradlew clean build`.

The installable artifact is `build/libs/SneakyPatches-0.1.0.jar`. Patch projects
do not produce separate JARs. Server classes and Sponge Mixin remain compile-only.

The initial target is Paper `26.2.build.116-stable`, corresponding to the supplied
`26.2-116-37dc545` investigation, with Ignite `1.2.1` and Java 25. Version pins live
in `gradle.properties`. Support for other server builds requires validation.

## Included patches

| Module | Behavior |
| --- | --- |
| [early-item-merge-cancellation](patches/early-item-merge-cancellation/README.md) | Returns at the start of item neighbor merging, globally |

All included patches are active. There is no runtime toggle or Bukkit lifecycle.
Each Mixin configuration is required, so target application failures stop startup.
Independent ownership does not guarantee compatibility if future patches modify
the same method. Review those interactions explicitly.

## Install

1. Run the verification checklist on an isolated copy of the target server.
2. Stop the server and place the root JAR in Ignite's `mods` directory, or the
   directory configured by `-Dignite.mods`. Remove older SneakyPatches JARs.
3. Start through Ignite and check its mod discovery and Mixin logs.

Updating or removing patches requires a full server restart. Removing this JAR
restores the underlying server behavior on the next start. Other installed
plugins can still cancel item merges.

## Add a patch

1. Create `patches/<patch-id>/` and register it in `settings.gradle.kts` using
   a flat project name, following the existing module.
2. Add a `build.gradle.kts` applying `io.papermc.paperweight.userdev` and declaring
   the pinned `paperweight.paperDevBundle`, as the first patch does.
3. Put Java code in a distinct package below `com.sneakyrp.sneakypatches`.
   Keep patch code self-contained and avoid dependencies on another patch or
   Bukkit plugin classes.
4. Add `src/main/resources/mixins.sneakypatches.<patch-id>.json`. Set `required`
   to `true`, list that patch's mixins, and require each injection to match.
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

## References

- [Ignite mod packaging](https://github.com/vectrix-space/ignite#configuring-your-mod)
- [Ignite 1.2.1 dependency versions](https://github.com/vectrix-space/ignite/blob/v1.2.1/gradle/libs.versions.toml)
- [Official Ignite mod template](https://github.com/vectrix-space/ignite-mod-template)
- [Paper userdev and unobfuscated 26.1+ builds](https://docs.papermc.io/paper/dev/userdev/)
