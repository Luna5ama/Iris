# PUBLIC API KNOWLEDGE

## OVERVIEW

Versioned, packaged API surface for external Iris consumers. These classes are a compatibility boundary, not a convenient home for internal helpers.

## WHERE TO LOOK

| Concern | Location | Notes |
|---------|----------|-------|
| Public singleton facade | `java/net/irisshaders/iris/api/v0/IrisApi.java` | Reflectively obtains the internal implementation |
| API implementation bridge | `common/src/main/java/net/irisshaders/iris/apiimpl/IrisApiV0Impl.java` | Runtime implementation lives outside this source set |
| Shader-pack state/listeners | `java/net/irisshaders/iris/api/v0/IrisApi.java` and sibling v0 types | External integrations compile against these signatures |

## CONVENTIONS

- Keep exported packages under `net.irisshaders.iris.api.v0`; a breaking shape belongs in a new versioned namespace.
- Depend on stable Java/Minecraft-facing types only when they are intentionally part of the public contract.
- Keep implementation classes, mutable global state, loader classes, and GL ownership in `common/src/main`.
- Maintain the reflection contract between `IrisApiInternal` and `IrisApiV0Impl` when changing construction or access.
- Document observable behavior and nullability at the API declaration; callers do not see internal assumptions.
- The `api` output is added to both compile/runtime packaging, so signature compatibility matters even without active repository tests.

## ANTI-PATTERNS

- Do not expose `apiimpl`, mixin, pipeline implementation, or loader-only types through public signatures.
- Do not silently repurpose an existing method or enum value when shader mods may already consume it.
- Do not move v0 classes into `common/src/main`; the dedicated source set is the packaging boundary.
- Do not assume NeoForge exclusion makes loader-neutral API behavior optional.

## CHANGE CHECKLIST

1. Compare the public signature and documented behavior before and after the change.
2. Update `IrisApiV0Impl` with the facade contract.
3. Build common and Fabric to confirm the API output is packaged.
4. Inspect both loader paths if the new API behavior reaches platform services.

```powershell
.\gradlew.bat :common:build :fabric:build
```
