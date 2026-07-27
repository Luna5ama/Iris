# SHADER TRANSFORM KNOWLEDGE

## OVERVIEW

GLSL parsing and compatibility rewrite pipeline. This subtree converts program sources using typed parameter objects and a cache whose key correctness depends on stable equality.

## WHERE TO LOOK

| Concern | Location | Notes |
|---------|----------|-------|
| Entry/cache/session | `TransformPatcher.java` | Patch dispatch and cache ownership |
| Compatibility rewrites | `transformer/CompatibilityTransformer.java` | Large ordered AST transformation set |
| Stage-specific transforms | `transformer/` | Composite, vertex, sodium, Distant Horizons paths |
| Cache-key inputs | `parameter/` | Program/profile-specific parameter classes |

## CACHE CONTRACT

- Every object contained in patch parameters must implement equality consistent with its effective shader output.
- Parameter objects must not mutate after a cached patch request.
- Adding a cache-relevant field requires updating both `equals` and `hashCode` through the full parameter hierarchy.
- Deliberate exclusions are semantic: base `Parameters` excludes shader `name`; `SodiumParameters` excludes mutable `alpha`.
- Disable `useCache` while developing patch logic when stale entries would conceal transformation changes.

## TRANSFORM CONTRACT

- Patch inside `indexBuildSession`; parser indexes and AST ownership are scoped to that session.
- Preserve transformer ordering. Compatibility rewrites may depend on declarations or metadata produced earlier.
- At the transform stage, only parsed `#extension` and `#pragma` directives are accepted; unexpected preprocessor directives are errors.
- Keep stage/profile dispatch exhaustive. A new program kind needs an intentional path rather than a silent default.
- Treat AST node moves, declaration replacement, and renamed symbols as structured operations; avoid string rewrites after parsing.

## ANTI-PATTERNS

- Do not place mutable render state in a cache key unless it is snapshotted into an immutable value.
- Do not change one subclass's equality without checking superclass and sibling-key semantics.
- Do not reorder grouped compatibility transformers as cleanup.
- Do not accept malformed directives merely because a downstream GLSL compiler might recover.
- Do not validate only one shader stage or one Sodium/vanilla pathway after shared transformer edits.

## VALIDATION

Use representative vertex/geometry/fragment/compute packs, Sodium terrain paths, and reload twice to expose cache-only regressions. Compiler acceptance alone does not prove equivalent transformed behavior.
