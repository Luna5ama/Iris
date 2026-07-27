# FABRIC ADAPTER

## SCOPE

This is the active loader distribution in the current Gradle settings.
Keep shared rendering behavior in `common`; this module owns Fabric APIs, packaging, and loader-specific injections.

## WHERE TO LOOK

| Concern | Location |
|---------|----------|
| Platform service implementation | `src/main/java/net/irisshaders/iris/platform/IrisFabricHelpers.java` |
| ServiceLoader registration | `src/main/resources/META-INF/services/net.irisshaders.iris.platform.IrisPlatformHelpers` |
| Mod Menu screen adapter | `src/main/java/net/irisshaders/iris/compat/modmenu/ModMenuIntegration.java` |
| Fabric/Sodium loader mixins | `src/main/java/net/irisshaders/iris/mixin/fabric/` |
| Mixin registration | `src/main/resources/mixins.iris.fabric.json` |
| Dependency, run, and jar wiring | `build.gradle.kts` |

## PLATFORM SEAM

- `IrisFabricHelpers` is discovered through Java `ServiceLoader`; keep the provider file exact.
- Changes to `IrisPlatformHelpers` must compile here and in NeoForge, even when this is the only active adapter.
- Use Fabric Loader for mod/version/environment/directories and Fabric API for key registration.
- Mod Menu integration is declared by common `fabric.mod.json`; no separate Fabric initializer owns shared startup.
- New loader mixins must live under `mixin.fabric` and be listed in `mixins.iris.fabric.json`.

## BUILD AND PACKAGING

- `processResources` copies all common main resources, then expands the version in `fabric.mod.json`.
- `jar` unpacks the common jar; duplicate entries are excluded.
- Common `main`, `vendored`, and `api` outputs are implementation inputs; `headers` remains compile-only.
- Loom reads `common/src/main/resources/iris.accesswidener` and generates `iris-fabric.refmap.json`.
- `include(...)` embeds selected Fabric API modules and libraries in the distributable jar.
- `modRuntimeOnly(...)` supplies development/runtime modules without embedding them.
- Sodium is a normal mod implementation dependency, not bundled by this module.
- `remapJar` writes final Fabric artifacts to root `build/libs`.
- Preserve deliberate non-transitive includes for Luna5ama, Vibris, Kotlin, and serialization artifacts.

## RUNS AND TESTS

- `compileTestJava` and `test` are disabled; a green build is packaging/compilation evidence only.
- `runClient` uses `fabric/run`; that directory is generated state.
- `runClientWithRenderdoc` contains a machine-specific Linux `LD_PRELOAD`; fix locally before relying on it elsewhere.
- Shared startup remains mixin-driven; do not add an initializer merely to force common initialization.

## CHANGE CHECKLIST

- Provider implementation changed: verify the service file still names the concrete class.
- Mixin added or renamed: update `mixins.iris.fabric.json` in the same change.
- Metadata changed: edit the common resource source, not a processed copy.
- Embedded dependency changed: confirm whether it belongs in the jar or only on runtime classpath.
- Resource changed: verify the merged common-plus-Fabric jar contains one intended copy.

## COMMANDS

```powershell
.\gradlew.bat :fabric:build
.\gradlew.bat :fabric:remapJar
.\gradlew.bat :fabric:runClient
```

Do not add loader-neutral logic here to avoid touching `common`; the platform seam already exists.
