package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.vertices.ImmediateState;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap = false)
public class MixinGlRenderDevice {
	@Redirect(method = "multiDrawElementsBaseVertex", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"))
	private void iris$captureMultiDrawElementsBaseVertex(
		int mode, long counts, int type, long offsets, int drawCount, long baseVertices
	) {
		if (!Iris.getCaptureManager().multiDrawElementsBaseVertex(
			mode, counts, type, offsets, drawCount, baseVertices
		)) {
			GL32C.nglMultiDrawElementsBaseVertex(mode, counts, type, offsets, drawCount, baseVertices);
		}
	}

	@Redirect(method = "multiDrawElementsBaseVertex", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlPrimitiveType;getId()I"))
	private int replaceId(GlPrimitiveType instance) {
		if (ImmediateState.usingTessellation) return GL43C.GL_PATCHES;

		return instance.getId();
	}
}
