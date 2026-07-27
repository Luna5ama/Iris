# SHADER-PACK DOMAIN KNOWLEDGE

## OVERVIEW

Loads untrusted OptiFine-style shader packs: includes, program sources, properties, options, material maps, languages, custom textures, and dimension overrides.

## WHERE TO LOOK

| Concern | Location | Notes |
|---------|----------|-------|
| Pack orchestration | `ShaderPack.java` | Parallel construction and ordered pack semantics |
| Program source sets | `programs/ProgramSet.java` | Graphics/compute/shadow/setup/final discovery |
| Properties | `properties/ShaderProperties.java` | Large external compatibility surface |
| Includes/preprocessing | `include/`, `preprocessor/`, `parsing/` | Source graph, directives, macro processing |
| Options/menus | `option/` | Intentionally mirrors established pack behavior |
| Material/entity/block IDs | `materialmap/`, `IdMap.java` | Pack-to-runtime mapping |

## LOAD CONTRACT

- Build the include graph before deriving options, properties, and fully expanded program sources.
- Preserve property and override ordering. Dimension overrides replace program sets rather than merging arbitrary fragments.
- Resolve every pack path relative to the pack root and guard archive/filesystem boundaries.
- Keep pack construction side effects and parallel work deterministic; `ShaderPack` uses a `ForkJoinPool`.
- Treat missing/invalid optional pack data separately from errors that make the selected pack unusable.
- Program discovery must keep graphics, compute, shadow, setup, deferred/composite, and final stages distinct.

## COMPATIBILITY RULES

- Established OptiFine/ShadersMod behavior is an external contract, including non-obvious option parsing quirks.
- `OptionAnnotatedSource` operates across logical post-include files; do not simplify it to physical-file-only edits.
- Do not run pack option prefixes/suffixes through Minecraft I18n; percent characters in pack text can break formatting.
- `JcppProcessor` hoists version/extension markers for compatibility, but pack authors must not be encouraged to rely on an invalid non-leading `#version`.
- Preserve language-map filtering and ignored-extension behavior represented by dormant fixtures.

## ANTI-PATTERNS

- Do not trust zip entries, include paths, properties, or option tokens as repository-controlled input.
- Do not normalize away pack quirks without checking real packs and compatibility fixtures.
- Do not merge dimension-specific properties/programs unless existing semantics explicitly do so.
- Do not treat compiler acceptance as proof that option, include, material-map, or property behavior stayed equivalent.
- Do not make pack loading depend on iteration order from unordered collections.

## VALIDATION

Use multiple real packs plus directory and zip forms. Cover options, includes, custom textures, language maps, dimension overrides, compute programs, reload, and a deliberately malformed pack.
