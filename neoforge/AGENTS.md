# NEOFORGE ADAPTER

## STATUS

This module is tracked but excluded from the current build.
Before invoking any `:neoforge:*` task, re-enable `neoforge` in root `settings.gradle.kts`.
Do not infer NeoForge health from the default root or Fabric build.

## WHERE TO LOOK

| Concern | Location |
|---------|----------|
| NeoForge mod entry and client registrations | `src/main/java/net/irisshaders/iris/platform/IrisForgeMod.java` |
| Platform service implementation | `src/main/java/net/irisshaders/iris/platform/IrisForgeHelpers.java` |
| ServiceLoader provider | `src/main/resources/META-INF/services/net.irisshaders.iris.platform.IrisPlatformHelpers` |
| Loader metadata and common mixin activation | `src/main/resources/META-INF/neoforge.mods.toml` |
| NeoForge and mod-compat mixins | `src/main/java/net/irisshaders/iris/mixin/forge/` |
| Loader mixin registration | `src/main/resources/mixins.iris.forge.json` |
| Injected common interfaces | `src/main/resources/interface_injections.json` |
| Access changes | `src/main/resources/META-INF/accesstransformer.cfg` |
| Compilation, runs, and JarJar | `build.gradle.kts` |

## PLATFORM WIRING

- Historical class/package names use `Forge`; keep them unless performing a complete coordinated rename.
- `IrisForgeMod` owns NeoForge event-bus key registration and the config-screen factory.
- `IrisForgeHelpers` is loaded by Java `ServiceLoader`; keep its provider file synchronized.
- Changes to the common platform interface require both loader implementations.
- NeoForge metadata activates common mixin configs plus `mixins.iris.forge.json`.
- Several loader mixins target optional mods with pseudo targets; preserve remap and optional-load semantics.

## ACCESS AND INTERFACES

- `interface_injections.json` is passed to NeoForge ModDev by `interfaceInjectionData`.
- It mirrors common runtime interfaces using named target classes, not Fabric intermediary names.
- `accesstransformer.cfg` supplies NeoForge equivalents for common access-widener needs.
- Shared member-access changes may require coordinated edits to both access files.
- Descriptor and owner drift is a compile/runtime failure; update against current Minecraft mappings.

## BUILD MODEL

- This module compiles common `main`, `vendored`, `api`, and `desktop` sources directly into NeoForge tasks.
- Common main resources are merged through `ProcessResources`; this is not a dependency on the common jar layout.
- `headers` and common outputs are compile-only classpath inputs where configured.
- `compileTestJava` is disabled; there is no meaningful NeoForge behavioral suite here.
- `includeDep` adds a library to both implementation and `jarJar`.
- `includeAdditional` also exposes the dependency on NeoForge's additional runtime classpath.
- GLSL Transformer, JCPP, and ANTLR are JarJar dependencies; Sodium is external runtime/compile input.
- Jar metadata expands `${version}` in `neoforge.mods.toml` and writes output to root `build/libs`.
- The jar task refers to root `LICENSE.md`; verify packaging because the tracked license filename differs.

## CHANGE CHECKLIST

- Re-enable the project before trusting compilation or run results.
- Mixin added or renamed: register it in `mixins.iris.forge.json`.
- Common mixin set changed: update `neoforge.mods.toml` when NeoForge should execute it.
- Platform method changed: update `IrisForgeHelpers` and the Fabric peer together.
- Interface/access requirement changed: update injection or transformer metadata, then inspect the built jar.
- Dependency changed: choose external runtime versus JarJar deliberately; do not bundle Sodium.

## COMMANDS

After re-enabling the module in settings:

```powershell
.\gradlew.bat :neoforge:build
.\gradlew.bat :neoforge:runClient
```

Do not leave `settings.gradle.kts` modified merely to run an exploratory NeoForge task.
