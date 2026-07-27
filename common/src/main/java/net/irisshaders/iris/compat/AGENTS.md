# COMPATIBILITY LAYER KNOWLEDGE

## OVERVIEW

Runtime integrations for Sodium, Distant Horizons, and smaller optional mods. Availability, version-specific APIs, mixin gating, and rendering ownership cross module boundaries here.

## STRUCTURE

```text
compat/
├── sodium/ # Renderer API, terrain/vertex hooks, config, twenty registered mixins
├── dh/     # Distant Horizons API and gated mixins
└── [small integrations] # Focused optional-mod bridges
```

## WHERE TO LOOK

| Concern | Location | Notes |
|---------|----------|-------|
| DH lifecycle/state | `dh/DHCompat.java` | Used by Iris startup, pipelines, shadows, uniforms |
| DH mixin gating | `dh/DHMixinConfigPlugin.java` | Checks platform mod availability |
| Sodium mixins | `sodium/mixin/` | Listed in `mixins.iris.compat.sodium.json` |
| Sodium config hook | `sodium/config/IrisConfig.java` | Declared as Fabric metadata entrypoint |
| Loader availability | `../platform/IrisPlatformHelpers.java` | Service-backed mod/platform queries |

## CONVENTIONS

- Keep optional-mod class loading behind availability gates; absent integrations must not link their classes during normal startup.
- Register DH and Sodium mixins only in their compatibility configs, not the primary common list.
- Inspect pipeline, shadow, uniform, target, and vertex effects together when changing a central compatibility seam.
- Keep shared compatibility logic here and loader-specific registration/provider code in the loader module.
- Match the currently pinned optional-mod API shape; `headers` supplies compile-only signatures and is not runtime code.
- Treat Sodium terrain/vertex formats and DH render phases as external contracts whose versions may evolve independently.

## ANTI-PATTERNS

- Do not catch linkage errors as a blanket substitute for correct gating and dependency declarations.
- Do not load optional classes from static initializers that run before the plugin/platform check.
- Do not register a compatibility mixin in the always-on config.
- Do not assume the Fabric path is the only consumer when changing `IrisPlatformHelpers`, even though NeoForge is currently excluded.
- Do not duplicate core render state in an integration; translate into pipeline/GL ownership contracts.

## VALIDATION

Test each touched integration in two environments: dependency present at the pinned version, and dependency absent. For Sodium/DH rendering changes, include shader reload, dimension transition, shadows, resize, and return-to-vanilla behavior.
