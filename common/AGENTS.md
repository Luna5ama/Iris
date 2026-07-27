# COMMON MODULE KNOWLEDGE

## OVERVIEW

Shared Iris implementation and packaged resources. This is the product core, but it is not strictly loader-neutral: it applies Fabric Loom, compiles against selected Fabric APIs, and owns `fabric.mod.json`.

## STRUCTURE

```text
common/src/
├── main/         # Shared runtime code, assets, mixin configs, access widener
├── api/          # Packaged public API v0
├── headers/      # Compile-only external signatures for optional integrations
├── vendored/     # Packaged third-party Odysseus digraph sources
├── desktop/      # Java 8 double-click warning main class
└── disabledTest/ # Dormant JUnit-era tests and shader-pack fixtures
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Startup, reload, global state | `src/main/java/net/irisshaders/iris/Iris.java` | Mixin-driven lifecycle |
| Loader abstraction | `.../platform/IrisPlatformHelpers.java` | ServiceLoader singleton |
| Public API implementation | `.../apiimpl/IrisApiV0Impl.java` | Reached reflectively from `src/api` |
| Loader/runtime metadata | `src/main/resources/` | Child instructions cover registration contracts |
| Historical parser/pack tests | `src/disabledTest/` | Not a configured Gradle source set |

## SOURCE-SET CONTRACTS

- `main` compiles against `headers`, `api`, and `vendored` outputs; only `api` and `vendored` join its runtime classpath.
- `api`, `vendored`, and `desktop` outputs are included in packaged jars.
- `headers` exists only to satisfy compile-time optional APIs. Keep implementation and state out of it.
- `desktop` contains `LaunchWarn`, targets Java 8, and is the jar manifest `Main-Class`; it is not Minecraft startup.
- `disabledTest` has no source-set declaration or JUnit dependencies. Its fixtures are useful evidence, not an executable gate.
- Common Gradle tasks are deliberately hidden from the IntelliJ Gradle task tree by clearing their groups.

## CONVENTIONS

- Shared initialization begins in `MixinOptions_Entrypoint`, continues after RenderSystem initialization, and finishes at the first title screen.
- Keep cross-loader services behind `IrisPlatformHelpers`; change the Fabric and NeoForge providers together.
- Shared mixins and assets belong in `src/main/resources`; loader-only registrations stay in their loader module.
- All Iris-only uniforms are registered through `src/main/java/net/irisshaders/iris/uniforms/IrisExclusiveUniforms.java`;
  inspect macro/define exposure when adding one.
- `Iris.testing` and the fixed macros in `disabledTest` model a legacy harness and must not leak into production behavior.

## ANTI-PATTERNS

- Do not interpret `common` as dependency-free platform code; verify existing Fabric and Minecraft dependencies before moving classes.
- Do not add runtime classes to `headers` or modify vendored packages as if they were first-party code.
- Do not claim `:common:test` provides behavior coverage; no active test source set exists here.
- Do not register a common mixin only in Java. The matching JSON entry is part of the change.

## LOCAL VALIDATION

```powershell
.\gradlew.bat :common:build
.\gradlew.bat :fabric:build
```

The Fabric build is the packaging check for common outputs and resources.
