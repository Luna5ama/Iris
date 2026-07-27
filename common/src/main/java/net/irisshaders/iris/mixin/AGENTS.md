# MIXIN KNOWLEDGE

## OVERVIEW

Minecraft/Sodium injection layer and Iris's effective shared bootstrap. Most classes are short, but class names, targets, injection points, priorities, plugins, and JSON registration form one runtime contract.

## WHERE TO LOOK

| Concern | Location | Notes |
|---------|----------|-------|
| Early startup | `MixinOptions_Entrypoint.java` | Calls `Iris.onEarlyInitialize` once |
| GL-ready startup | `MixinRenderSystem.java` | Initializes Iris GL/samplers and shader pack |
| Loading complete | `MixinTitleScreen.java` | Prewarms first pipeline |
| Frame lifecycle | `MixinLevelRenderer.java` | Begin, shadow, finalize hooks |
| Dimension/reload lifecycle | `MixinMinecraft_PipelineManagement.java` | Pipeline destruction/reprepare |
| Config plugin | `IrisMixinPlugin.java` | Shared mixin plugin/refmap |
| Registration | `common/src/main/resources/mixins.iris*.json` | Required runtime lists |

## REGISTRATION CONTRACT

- A new class must be added to the correct common mixin JSON; discovery is not automatic.
- Shared configs are `required`, use default injector requirement 1, and primary configs allow `maxShiftBy: 2`. Missing injections fail loudly by design.
- Put Fabric-only or NeoForge-only injections in the loader module and its loader config.
- Compatibility mixins belong under their compatibility config/plugin when application depends on a mod being present.
- Keep package moves synchronized with JSON class names, plugins, refmaps, and any mixinterface/interface-injection metadata.

## INJECTION RULES

- Prefer stable semantic anchors over raw ordinal/offset changes; document why a fragile target is required.
- Preserve lifecycle ordering across constructor, RenderSystem, title-screen, level-render, and dimension-change hooks.
- Mixin priorities here are compatibility-specific. Do not normalize them without inspecting the competing injection.
- Loader sibling mixins (`MixinFluidRendererImpl`, `MixinLevelRenderer`, `MixinParticleEngine`) implement analogous behavior but are not mechanically interchangeable.
- Keep one-time startup guards when changing constructor injection paths.

## ANTI-PATTERNS

- Do not add an unregistered mixin or leave a stale JSON entry after a rename/removal.
- Do not move loader imports into common mixins merely to share a few lines.
- Do not weaken `require`/priority values to hide a failing target without verifying runtime behavior.
- Do not assume a successful Java compile proves the injection applied.
- Do not enable `mixins.iris.integrationtest.json` as ordinary production behavior; it is a manual probe.

## VALIDATION

Run a Fabric client with Mixin error checking, exercise startup and rendering, and inspect logs for unapplied or conflicting injections. For compatibility paths, test both with and without the optional mod.
