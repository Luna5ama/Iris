# PROJECT KNOWLEDGE BASE

**Generated:** 2026-07-26
**Commit:** 4c9e4f042
**Branch:** 1.21.11-shaderdev

## OVERVIEW

Iris is a client-side Minecraft shader-pack implementation. This checkout targets Minecraft 1.21.11 with Java 21, Gradle 9.2.1, shared rendering code in `common`, and an active Fabric distribution.

## STRUCTURE

```text
Iris/
├── common/        # Shared implementation, public API, resources, vendored code, dormant tests
├── fabric/        # Active Fabric loader adapter, packaging, and development runs
├── neoforge/      # Tracked NeoForge adapter; excluded from the current Gradle settings
├── docs/          # User/developer notes and historical changelogs
├── custom_sodium/ # Ignored local Sodium binaries required by this checkout
└── ../vibris/     # Required composite build providing capture + MCP dependencies and bridge
```

Root `src/` is not canonical. Maintained source lives under the loader modules above.

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Global lifecycle, config, shader-pack reload | `common/src/main/java/net/irisshaders/iris/Iris.java` | Early init is triggered by mixins, not a Fabric initializer |
| Frame/pipeline lifecycle | `common/src/main/java/net/irisshaders/iris/pipeline/` | Rendering state, targets, programs, composites, teardown |
| Shader-pack loading and compatibility | `common/src/main/java/net/irisshaders/iris/shaderpack/` | Untrusted external pack input and OptiFine-compatible behavior |
| OpenGL abstraction and resources | `common/src/main/java/net/irisshaders/iris/gl/` | Render-thread state and explicit ownership |
| Minecraft injections | `common/src/main/java/net/irisshaders/iris/mixin/` | Classes must be registered in the matching mixin JSON |
| Distant Horizons / Sodium integration | `common/src/main/java/net/irisshaders/iris/compat/` | Runtime-gated compatibility paths |
| Uniforms and expression functions | `common/src/main/java/net/irisshaders/iris/uniforms/`, `common/src/main/java/net/irisshaders/iris/parsing/`, `common/src/main/java/kroppeb/stareval/` | Cross-package custom-uniform implementation |
| Loader platform seam | `common/src/main/java/net/irisshaders/iris/platform/IrisPlatformHelpers.java` | ServiceLoader implementations live in Fabric and NeoForge |
| Public API | `common/src/api/java/net/irisshaders/iris/api/v0/` | Shipped, versioned compatibility surface |
| Fabric-specific code | `fabric/src/main/` | Provider, loader mixins, Mod Menu adapter |
| NeoForge-specific code | `neoforge/src/main/` | Dormant in this checkout until settings are changed |

## CODE MAP

| Symbol | Type | Location | Reach | Role |
|--------|------|----------|-------|------|
| `Iris` | class | `common/.../Iris.java` | Global hub | Config, pack filesystems, reload, pipeline creation |
| `IrisRenderingPipeline` | class | `common/.../pipeline/` | 40 caller files | Main shader rendering state machine and resource owner |
| `WorldRenderingPipeline` | interface | `common/.../pipeline/` | 46 caller files | Cross-cutting render lifecycle contract |
| `ShaderPack` | class | `common/.../shaderpack/` | 15 measured refs | Loads pack sources, properties, options, textures, dimensions |
| `ProgramSet` | class | `common/.../shaderpack/programs/` | Central pack consumer | Resolves graphics and compute program sources |
| `IrisRenderSystem` | class | `common/.../gl/` | 32 importing files | Render-thread GL facade and DSA backends |
| `IrisPlatformHelpers` | interface | `common/.../platform/` | 15 direct singleton callers | Fabric/NeoForge service seam |

## CONVENTIONS

- `.editorconfig` is the formatting authority: LF, UTF-8, final newline, trimmed trailing whitespace; Java uses tabs and continuation indent 4; JSON and properties use 2 spaces.
- Put cross-loader behavior in `common`; keep loader registration, metadata, and loader-only mixins in `fabric` or `neoforge`.
- A new common mixin is inert until added to the appropriate `common/src/main/resources/mixins.iris*.json` file.
- Changes to `IrisPlatformHelpers` require both implementations and both `META-INF/services` provider files to stay synchronized.
- Root version constants and Minecraft/loader dependency versions live in `build.gradle.kts` extras.
- Final Fabric artifacts are redirected to root `build/libs`; the root `jar` task itself is disabled.

## ANTI-PATTERNS (THIS PROJECT)

- Do not treat `neoforge` as part of the default build; it is commented out in `settings.gradle.kts`.
- Do not treat `common/src/disabledTest` or embedded `main()` diagnostics as an active test suite.
- Do not add runtime logic to `common/src/headers`; those classes are compile-only external signatures.
- Do not edit generated/runtime trees such as `.gradle/`, `build/`, `fabric/run/`, `out/`, or ignored `custom_sodium/` as source.
- Do not assume a clean standalone Iris checkout builds: `../vibris` and the ignored local Sodium Fabric JAR are required.

## UNIQUE STYLES

- Shared startup is mixin-driven: `MixinOptions_Entrypoint` calls `Iris.onEarlyInitialize`, then RenderSystem and title-screen mixins complete GL and loading initialization.
- `common` is shared implementation, not a perfectly platform-free module; it applies Fabric Loom and owns `fabric.mod.json`.
- `api`, `vendored`, and `desktop` outputs are packaged; `headers` is compile-only; `desktop` targets Java 8 while the normal toolchain targets Java 21.
- Shader-pack compatibility often preserves established OptiFine quirks. Compatibility behavior is part of the external contract, not cleanup debt by default.

## COMMANDS

```powershell
.\gradlew.bat build
.\gradlew.bat :fabric:build
.\gradlew.bat :fabric:remapJar
.\gradlew.bat :fabric:runClient
.\gradlew.bat :common:build
.\gradlew.bat -Pbuild.release=true build
```

CI uses `./gradlew build` on Java 21. The current build compiles/packages code but does not provide meaningful behavioral test coverage.

## NOTES

- `:fabric:runClientWithRenderdoc` contains a machine-specific Linux `LD_PRELOAD`; inspect it before use elsewhere.
- Release logic checks a Gradle project property (`-Pbuild.release=true`); the checked-in release workflow currently passes `-Dbuild.release=true`.
- Common and NeoForge packaging refer to `LICENSE.md`, while the tracked file is `LICENSE`; verify archive contents when touching packaging.
- `jitpack.yml` and parts of the release documentation describe older tooling and should not override the current Gradle scripts.
