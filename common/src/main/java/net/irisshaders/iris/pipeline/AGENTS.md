# RENDERING PIPELINE KNOWLEDGE

## OVERVIEW

Stateful frame-rendering core. It owns shader programs, render targets, shadow/composite/final passes, dimension-scoped pipeline caching, and reload teardown.

## WHERE TO LOOK

| Concern | Location | Notes |
|---------|----------|-------|
| Pipeline contract/fallback | `WorldRenderingPipeline.java`, `VanillaRenderingPipeline.java` | Used across mixins and render pathways |
| Shader pipeline hub | `IrisRenderingPipeline.java` | Highest-centrality resource owner |
| Dimension cache/lifecycle | `PipelineManager.java` | One prepared pipeline per dimension |
| Composite passes | `CompositeRenderer.java`, `FinalPassRenderer.java` | Target flips, mipmaps, viewport and sampler state |
| Program descriptors | `programs/` | Graphics/compute program construction and use |
| GLSL AST rewriting | `transform/` | Nested child instructions apply |

## FRAME AND LIFECYCLE CONTRACTS

- Prepare the active dimension pipeline before level rendering; begin, shadow, finalize, and teardown phases are ordered by lifecycle mixins.
- Center-depth sampling occurs before render-target resize/clear work. Reordering changes temporal behavior.
- Render-target clears preserve alpha `1.0`; alpha participates in downstream shader-pack semantics.
- Restore the viewport and framebuffer state after passes that temporarily replace them.
- Dimension transitions destroy and immediately re-prepare pipelines; partial destruction leaves global rendering unusable.
- `VanillaRenderingPipeline` is the required fallback when no valid shader pack is active.

## OWNERSHIP

- Every program, texture, framebuffer, sampler, and renderer created by a pipeline needs a matching teardown path.
- Unbind textures and samplers that may be deleted during reload; stale GL bindings can outlive Java objects.
- `CustomTextureManager` owns every texture in its managed lists and must release each ID from `close`.
- Keep framebuffer flip state synchronized with the program/pass sequence; flips are dataflow, not cosmetic bookkeeping.
- Treat `PipelineManager` destruction as a high-risk boundary. Do not expose an unprepared interval to render callbacks.

## ANTI-PATTERNS

- Do not create GL resources outside an explicit owner and `destroy`/`close` path.
- Do not fold shader and vanilla pipelines together if it weakens the fallback contract.
- Do not reuse a destroyed pipeline after a pack reload or dimension change.
- Do not infer pass ordering from filenames alone; follow `ProgramSet` and renderer construction order.
- Do not treat dormant tests as protection for frame ordering, resource lifetime, or reload behavior.

## VALIDATION

Build packaging, then exercise shader enable/disable, pack reload, dimension change, resize, shadow pass, and return-to-vanilla paths in `:fabric:runClient`.
