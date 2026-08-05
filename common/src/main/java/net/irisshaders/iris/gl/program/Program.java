package net.irisshaders.iris.gl.program;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.luna5ama.vibris.capture.GraphicsProgramRegistry;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.shader.ShaderType;
import org.lwjgl.opengl.GL43C;

import java.util.EnumMap;

public final class Program extends GlResource {
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;

	Program(String name, int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images,
			EnumMap<ShaderType, String> sources) {
		super(program);

		this.uniforms = uniforms;
		this.samplers = samplers;
		this.images = images;

		GraphicsProgramRegistry.register(program, name,
			sources.get(ShaderType.VERTEX),
			sources.get(ShaderType.TESSELATION_CONTROL),
			sources.get(ShaderType.TESSELATION_EVAL),
			sources.get(ShaderType.GEOMETRY),
			sources.get(ShaderType.FRAGMENT));
	}

	public static void unbind() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GlStateManager._glUseProgram(0);
	}

	public void use() {
		IrisRenderSystem.memoryBarrier(GL43C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT | GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
		GlStateManager._glUseProgram(getGlId());

		uniforms.update();
		samplers.update();
		images.update();
	}

	public void destroyInternal() {
		GraphicsProgramRegistry.unregister(getGlId());
		GlStateManager.glDeleteProgram(getGlId());
	}

	/**
	 * @return the OpenGL ID of this program.
	 * @deprecated this should be encapsulated eventually
	 */
	@Deprecated
	public int getProgramId() {
		return getGlId();
	}

	public int getActiveImages() {
		return images.getActiveImages();
	}
}
