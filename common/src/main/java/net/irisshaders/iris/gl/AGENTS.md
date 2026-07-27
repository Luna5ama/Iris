# OPENGL LAYER KNOWLEDGE

## OVERVIEW

Low-level OpenGL facade, resource wrappers, programs, textures, samplers, uniforms, buffers, and state helpers. Calls are render-thread sensitive and frequently own native IDs.

## WHERE TO LOOK

| Concern | Location | Notes |
|---------|----------|-------|
| DSA/state facade | `IrisRenderSystem.java` | Core, ARB, and unsupported fallback paths |
| Resource lifetime | `GlResource.java`, resource subpackages | Native ID ownership and destruction |
| Programs/shaders | `program/`, `shader/` | Link/use/delete and sampler/uniform wiring |
| Texture/image state | `texture/`, `image/` | Binding, formats, mipmaps, images |
| Sampler allocation | `sampler/` | Texture-unit ownership and dynamic/default ordering |
| Buffers | `buffer/` | Includes Linux-specific deletion constraints |

## STATE AND OWNERSHIP CONTRACTS

- Use this layer from the render thread unless the called API explicitly documents otherwise.
- Keep core DSA, ARB DSA, and fallback implementations behaviorally aligned when adding an operation.
- Pair every generated GL ID with exactly one owner and one deletion path.
- Restore any binding, active texture, viewport, or global state temporarily changed around vanilla RenderType work.
- `PBRTextureLoader` may only disturb the `GL_TEXTURE_2D` binding; preserve all other global GL state.
- Add the default sampler before any dynamic sampler so texture unit 0 remains available.

## LOCAL HAZARDS

- `ShaderStorageBuffer` is immutable and must not extend `GlResource`.
- Delete shader-storage buffers through `IrisRenderSystem.deleteBuffers`; the GlStateManager path produces Linux GL errors.
- `ProgramSamplers` must preserve the active texture during RenderType setup.
- `Program.getProgramId()` and `ComputeProgram.getProgramId()` are deprecated encapsulation leaks; do not add callers.
- Backup/restore helpers must remain symmetric across exceptions and early exits.

## ANTI-PATTERNS

- Do not call raw GL deletion beside a wrapper that already owns the resource.
- Do not hide unsupported capability paths behind a no-op; surface the capability failure.
- Do not cache binding state across external Minecraft/Sodium calls without revalidation.
- Do not assume Windows driver tolerance proves Linux or core-profile correctness.
- Do not leave program, sampler, or texture IDs bound across shader-pack reload teardown.

## VALIDATION

Exercise resource creation/destruction, shader reload, resize, disabled-shader fallback, and at least one non-default DSA path where available. Check GL debug output, not only visual output.
