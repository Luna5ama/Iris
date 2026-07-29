package net.irisshaders.iris.vibris;

import dev.luna5ama.vibris.capture.GlArtifactCapture;
import dev.luna5ama.vibris.capture.GlCapturePlanExecutor;
import dev.luna5ama.vibris.capture.GlCaptureMetadata;
import dev.luna5ama.vibris.capture.TextureCatalog;
import dev.luna5ama.vibris.capture.TextureInfo;
import dev.vibris.api.ArtifactSink;
import dev.vibris.api.CancellationToken;
import dev.vibris.api.CapturePlan;
import dev.vibris.api.CaptureResult;
import dev.vibris.api.ResourceCatalog;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.IrisShaderDebugHost;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MinecraftVibrisCapture {
	private final Minecraft minecraft;
	private final IrisShaderDebugHost shaderDebug = new IrisShaderDebugHost();

	MinecraftVibrisCapture(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	ResourceCatalog resourceCatalog(long frameId) {
		List<ResourceCatalog.ResourceDescriptor> resources = new ArrayList<>();
		ResourceCatalog.ResourceDescriptor beauty = textureDescriptor(
			"beauty",
			ResourceCatalog.ResourceKind.FINAL_FRAMEBUFFER,
			minecraft.getMainRenderTarget().getColorTexture().iris$getGlId(),
			frameId);
		if (beauty != null) resources.add(beauty);
		namedTextures().forEach((name, texture) -> {
			ResourceCatalog.ResourceDescriptor descriptor = textureDescriptor(
				name,
				ResourceCatalog.ResourceKind.TEXTURE,
				texture,
				frameId);
			if (descriptor != null) resources.add(descriptor);
		});
		namedBuffers().forEach((name, buffer) -> resources.add(new ResourceCatalog.ResourceDescriptor(
			name,
			ResourceCatalog.ResourceKind.BUFFER,
			0,
			0,
			0,
			0,
			0,
			"binary",
			0,
			ResourceCatalog.ScalarType.UINT8,
			buffer.getSize(),
			frameId,
			name)));
		return new ResourceCatalog(resources);
	}

	CaptureResult capture(
		CapturePlan plan,
		ArtifactSink sink,
		long frameId,
		CancellationToken cancellation
	) {
		int finalFramebuffer = minecraft.getMainRenderTarget().getColorTexture().iris$getGlId();
		Map<String, Integer> textures = namedTextures();
		Map<String, ShaderStorageBuffer> buffers = namedBuffers();
		return GlCapturePlanExecutor.capture(
			plan,
			sink,
			frameId,
			cancellation,
			target -> resolve(target, finalFramebuffer, textures, buffers));
	}

	private static Integer resolve(
		CapturePlan.Target target,
		int finalFramebuffer,
		Map<String, Integer> textures,
		Map<String, ShaderStorageBuffer> buffers
	) {
		return switch (target.kind()) {
			case FINAL_FRAMEBUFFER -> finalFramebuffer;
			case TEXTURE -> textures.get(target.logicalName());
			case BUFFER -> {
				ShaderStorageBuffer buffer = buffers.get(target.logicalName());
				yield buffer == null ? null : buffer.getId();
			}
		};
	}

	private Map<String, Integer> namedTextures() {
		Map<String, Integer> textures = new LinkedHashMap<>();
		TextureCatalog catalog = shaderDebug.textureCatalog();
		for (TextureInfo texture : catalog.getColortex()) {
			textures.put(texture.getName(), texture.getTextureId());
		}
		for (TextureInfo texture : catalog.getCustom()) {
			textures.putIfAbsent(texture.getName(), texture.getTextureId());
		}
		IrisRenderingPipeline pipeline = pipeline();
		RenderTargets targets = pipeline.getRenderTargetsForDebug();
		textures.put("depthtex0", targets.getDepthTexture().iris$getGlId());
		textures.put("depthtex1", targets.getDepthTextureNoTranslucents().iris$getGlId());
		textures.put("depthtex2", targets.getDepthTextureNoHand().iris$getGlId());
		return textures;
	}

	private static Map<String, ShaderStorageBuffer> namedBuffers() {
		Map<String, ShaderStorageBuffer> buffers = new LinkedHashMap<>();
		for (ShaderStorageBuffer buffer : ShaderStorageBufferHolder.getActiveBuffers()) {
			String name = buffer.getName();
			if (name == null || name.isBlank()) name = "ssbo" + buffer.getIndex();
			ShaderStorageBuffer conflict = buffers.putIfAbsent(name, buffer);
			if (conflict != null && conflict.getIndex() != buffer.getIndex()) {
				throw new IllegalStateException("Ambiguous shader storage buffer name: " + name);
			}
		}
		return buffers;
	}

	@Nullable
	private static ResourceCatalog.ResourceDescriptor textureDescriptor(
		String name,
		ResourceCatalog.ResourceKind kind,
		int textureId,
		long frameId
	) {
		GlCaptureMetadata metadata = GlArtifactCapture.describeTextureOrNull(textureId, 0);
		if (metadata == null) return null;
		return new ResourceCatalog.ResourceDescriptor(
			name, kind, metadata.getWidth(), metadata.getHeight(), metadata.getDepth(), 1, 1,
			metadata.getInternalFormat(), metadata.getChannelCount(), metadata.getScalarType(),
			metadata.getByteSize(), frameId, name);
	}

	private static IrisRenderingPipeline pipeline() {
		if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline) {
			return pipeline;
		}
		throw new IllegalStateException("No Iris shader pipeline is active");
	}
}
