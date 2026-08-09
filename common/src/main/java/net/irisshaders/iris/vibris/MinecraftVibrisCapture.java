package net.irisshaders.iris.vibris;

import dev.luna5ama.vibris.capture.GlArtifactCapture;
import dev.luna5ama.vibris.capture.GlCapturePlanExecutor;
import dev.luna5ama.vibris.capture.GlCaptureMetadata;
import dev.luna5ama.vibris.capture.StorageBufferInfo;
import dev.luna5ama.vibris.capture.TextureCatalog;
import dev.luna5ama.vibris.capture.TextureInfo;
import dev.vibris.api.ArtifactSink;
import dev.vibris.api.CancellationToken;
import dev.vibris.api.CapturePlan;
import dev.vibris.api.CaptureResult;
import dev.vibris.api.ResourceCatalog;
import net.irisshaders.iris.IrisShaderDebugHost;
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
			frameId,
			"screenshot");
		if (beauty != null) resources.add(beauty);
		shaderDebug.textureCatalog().getTextures().forEach(texture -> {
			ResourceCatalog.ResourceDescriptor descriptor = textureDescriptor(
				texture.getName(),
				ResourceCatalog.ResourceKind.TEXTURE,
				texture.getTextureId(),
				frameId,
				texture.getCategory());
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
			buffer.getSizeBytes(),
			frameId,
			name,
			buffer.getCategory(), "", "", "", 0, "", "")));
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
		Map<String, StorageBufferInfo> buffers = namedBuffers();
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
		Map<String, StorageBufferInfo> buffers
	) {
		return switch (target.kind()) {
			case FINAL_FRAMEBUFFER -> finalFramebuffer;
			case TEXTURE -> textures.get(target.logicalName());
			case BUFFER -> {
				StorageBufferInfo buffer = buffers.get(target.logicalName());
				yield buffer == null ? null : buffer.getGlId();
			}
			case PATCHED_SHADERS -> throw new IllegalArgumentException(
				"Patched shaders require directory artifact capture");
		};
	}

	private Map<String, Integer> namedTextures() {
		Map<String, Integer> textures = new LinkedHashMap<>();
		TextureCatalog catalog = shaderDebug.textureCatalog();
		for (TextureInfo texture : catalog.getTextures()) {
			textures.put(texture.getName(), texture.getTextureId());
		}
		return textures;
	}

	private Map<String, StorageBufferInfo> namedBuffers() {
		Map<String, StorageBufferInfo> buffers = new LinkedHashMap<>();
		for (StorageBufferInfo buffer : shaderDebug.storageBuffers()) {
			StorageBufferInfo conflict = buffers.putIfAbsent(buffer.getName(), buffer);
			if (conflict != null && conflict.getGlId() != buffer.getGlId()) {
				throw new IllegalStateException("Ambiguous shader storage buffer name: " + buffer.getName());
			}
		}
		return buffers;
	}

	@Nullable
	private static ResourceCatalog.ResourceDescriptor textureDescriptor(
		String name,
		ResourceCatalog.ResourceKind kind,
		int textureId,
		long frameId,
		String category
	) {
		GlCaptureMetadata metadata = GlArtifactCapture.describeTextureOrNull(textureId, 0);
		if (metadata == null) return null;
		return new ResourceCatalog.ResourceDescriptor(
			name, kind, metadata.getWidth(), metadata.getHeight(), metadata.getDepth(), metadata.getMipLevels(), 1,
			metadata.getInternalFormat(), metadata.getChannelCount(), metadata.getScalarType(),
			metadata.getByteSize(), frameId, name, category, metadata.getTextureTarget(),
			metadata.getChannelLayout(), metadata.getNumericClass(), metadata.getComponentBits(),
			metadata.getReadbackFormat(), metadata.getReadbackType());
	}
}
