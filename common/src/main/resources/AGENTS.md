# COMMON RUNTIME RESOURCES

## SCOPE

This directory is executable packaging input, not passive documentation.
Files land at the root of the common/Fabric runtime classpath unless nested under `assets/` or `META-INF/`.

## WIRING MAP

| File | Consumer | Contract |
|------|----------|----------|
| `fabric.mod.json` | Fabric Loader | Client metadata, entrypoints, mixin list, access widener, injected interfaces |
| `mixins.iris*.json` | Mixin bootstrap | Package-to-class registration and optional config plugins |
| `iris.accesswidener` | Loom/Fabric Loader | Named-namespace access changes required by common code |
| `centerDepth.{vsh,fsh}` | `CenterDepthSampler` | Loaded as `/centerDepth.vsh` and `/centerDepth.fsh` |
| `colorSpace.{vsh,csh}` | Color-space converters | Loaded as `/colorSpace.vsh` and `/colorSpace.csh` |
| `assets/iris/lang/*.json` | Minecraft plus `MixinClientLanguage` | Namespaced translations; dialect fallback also reads classpath JSON |
| `assets/iris/textures/gui/*` | GUI and image mixins | Packaged textures; keep identifiers under `iris` |
| `META-INF/MANIFEST.MF` | Jar tooling | Base manifest input; Gradle adds the launch-warning main class |

## MIXIN RULES

- A mixin class does nothing until listed in the matching JSON.
- Keep each JSON `package` aligned with the Java package it registers.
- `fabric.mod.json` activates the normal, fantastic, vertex-format, mipmap, Sodium, DH, and max-FPS configs.
- `mixins.iris.devenvironment.json` is inactive, absent from loader metadata, and references a plugin class missing from this tree; do not advertise or activate it without repairing that path.
- `mixins.iris.integrationtest.json` is dormant unless explicitly added to the active mixin list.
- Loader-only mixins belong in the loader module, not in these common configs.

## METADATA RULES

- `${version}` is expanded by `processResources`; preserve the placeholder in source.
- `fabric.mod.json` owns the Fabric Mod Menu and Sodium config entrypoints even though implementations span modules.
- Fabric injected-interface mappings use intermediary names where required; NeoForge has a separate named mapping file.
- Keep loader dependency ranges and incompatibility declarations intentional; they are runtime admission policy.
- When adding a common mixin config, also register it in every loader metadata file that should execute it.

## CLASSPATH RULES

- Root shader filenames are Java API: `getResourceAsStream("/...")` hard-codes them.
- Rename or relocate a shader only with every classpath consumer updated in the same change.
- Missing embedded shaders fail during pipeline construction; packaging checks matter more than source presence.
- Do not move root shaders into `assets/iris/`; Minecraft resource lookup and Java classpath lookup are different paths.
- Translation and texture paths are namespaced resources; preserve lowercase resource identifiers.

## ACCESS CHANGES

- Treat `iris.accesswidener` as code: descriptors must match the current mappings exactly.
- Fabric compilation and runs consume this file through both common and Fabric Loom configuration.
- Where shared code needs equivalent NeoForge access, update `neoforge/.../accesstransformer.cfg` too.
- Interface additions may also require both Fabric injected-interface metadata and NeoForge `interface_injections.json`.

## CHECKS

```powershell
.\gradlew.bat :common:processResources
.\gradlew.bat :fabric:processResources
.\gradlew.bat :fabric:remapJar
```

- Inspect the produced Fabric jar when changing metadata, classpath shaders, mixins, or access rules.
- Do not validate by editing generated files under `build/` or `fabric/run/`.
