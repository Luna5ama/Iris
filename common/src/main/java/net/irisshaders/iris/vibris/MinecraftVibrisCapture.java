package net.irisshaders.iris.vibris;

import dev.luna5ama.vibris.capture.GlArtifactCapture;
import dev.luna5ama.vibris.capture.GlCaptureMetadata;
import dev.luna5ama.vibris.capture.TextureCatalog;
import dev.luna5ama.vibris.capture.TextureInfo;
import dev.vibris.api.ArtifactSink;
import dev.vibris.api.CancellationToken;
import dev.vibris.api.CapturePlan;
import dev.vibris.api.CaptureResourceNotFoundException;
import dev.vibris.api.CaptureResult;
import dev.vibris.api.ResourceCatalog;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.IrisShaderDebugHost;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
		resources.add(textureDescriptor(
			"beauty",
			ResourceCatalog.ResourceKind.FINAL_FRAMEBUFFER,
			minecraft.getMainRenderTarget().getColorTexture().iris$getGlId(),
			frameId));
		namedTextures().forEach((name, texture) -> resources.add(textureDescriptor(
			name,
			ResourceCatalog.ResourceKind.TEXTURE,
			texture.id,
			frameId)));
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
		Map<String, TextureHandle> textures = namedTextures();
		Map<String, ShaderStorageBuffer> buffers = namedBuffers();
		List<ResolvedTarget> targets = plan.targets().stream()
			.map(target -> resolve(target, textures, buffers))
			.toList();
		Map<String, ResourceCatalog.ResourceDescriptor> captured = new LinkedHashMap<>();
		for (ResolvedTarget resolved : targets) {
			cancellation.throwIfCancellationRequested();
			CapturePlan.Target target = resolved.target;
			GlCaptureMetadata metadata;
			try (OutputStream output = sink.open(target.fileName())) {
				metadata = resolved.buffer == null
					? GlArtifactCapture.captureTexture(
						resolved.textureId,
						target.mipLevel(),
						target.layer(),
						target.format(),
						output)
					: GlArtifactCapture.captureBuffer(resolved.buffer.getId(), output);
			} catch (IOException exception) {
				throw new UncheckedIOException(exception);
			}
			ResourceCatalog.ResourceDescriptor descriptor = descriptor(target, metadata, frameId);
			captured.put(target.artifactName(), descriptor);
			if (target.format() == CapturePlan.ArtifactFormat.RAW ||
				target.format() == CapturePlan.ArtifactFormat.BIN) {
				writeMetadata(sink, target.metadataFileName(), descriptor);
			}
		}
		return new CaptureResult(frameId, captured);
	}

	private ResolvedTarget resolve(
		CapturePlan.Target target,
		Map<String, TextureHandle> textures,
		Map<String, ShaderStorageBuffer> buffers
	) {
		return switch (target.kind()) {
			case FINAL_FRAMEBUFFER -> new ResolvedTarget(
				target, minecraft.getMainRenderTarget().getColorTexture().iris$getGlId(), null);
			case TEXTURE -> {
				TextureHandle texture = textures.get(target.logicalName());
				if (texture == null) throw new CaptureResourceNotFoundException(target.logicalName());
				yield new ResolvedTarget(target, texture.id, null);
			}
			case BUFFER -> {
				ShaderStorageBuffer buffer = buffers.get(target.logicalName());
				if (buffer == null) throw new CaptureResourceNotFoundException(target.logicalName());
				yield new ResolvedTarget(target, 0, buffer);
			}
		};
	}

	private Map<String, TextureHandle> namedTextures() {
		Map<String, TextureHandle> textures = new LinkedHashMap<>();
		TextureCatalog catalog = shaderDebug.textureCatalog();
		for (TextureInfo texture : catalog.getColortex()) {
			textures.put(texture.getName(), new TextureHandle(texture.getTextureId()));
		}
		for (TextureInfo texture : catalog.getCustom()) {
			textures.putIfAbsent(texture.getName(), new TextureHandle(texture.getTextureId()));
		}
		IrisRenderingPipeline pipeline = pipeline();
		RenderTargets targets = pipeline.getRenderTargets();
		textures.put("depthtex0", new TextureHandle(targets.getDepthTexture().iris$getGlId()));
		textures.put("depthtex1", new TextureHandle(targets.getDepthTextureNoTranslucents().iris$getGlId()));
		textures.put("depthtex2", new TextureHandle(targets.getDepthTextureNoHand().iris$getGlId()));
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

	private static ResourceCatalog.ResourceDescriptor descriptor(
		CapturePlan.Target target,
		GlCaptureMetadata metadata,
		long frameId
	) {
		return new ResourceCatalog.ResourceDescriptor(
			target.logicalName(),
			target.kind(),
			metadata.getWidth(),
			metadata.getHeight(),
			metadata.getDepth(),
			1,
			1,
			metadata.getInternalFormat(),
			metadata.getChannelCount(),
			metadata.getScalarType(),
			metadata.getByteSize(),
			frameId,
			target.logicalName());
	}

	private static ResourceCatalog.ResourceDescriptor textureDescriptor(
		String name,
		ResourceCatalog.ResourceKind kind,
		int textureId,
		long frameId
	) {
		GlCaptureMetadata metadata = GlArtifactCapture.describeTexture(textureId, 0);
		return new ResourceCatalog.ResourceDescriptor(
			name, kind, metadata.getWidth(), metadata.getHeight(), metadata.getDepth(), 1, 1,
			metadata.getInternalFormat(), metadata.getChannelCount(), metadata.getScalarType(),
			metadata.getByteSize(), frameId, name);
	}

	private static void writeMetadata(
		ArtifactSink sink,
		String fileName,
		ResourceCatalog.ResourceDescriptor resource
	) {
		String json = "{\"logical_name\":\"" + escape(resource.logicalName()) +
			"\",\"kind\":\"" + resource.kind() +
			"\",\"width\":" + resource.width() +
			",\"height\":" + resource.height() +
			",\"depth\":" + resource.depth() +
			",\"internal_format\":\"" + escape(resource.internalFormat()) +
			"\",\"channel_count\":" + resource.channelCount() +
			",\"scalar_type\":\"" + resource.scalarType() +
			"\",\"byte_size\":" + resource.byteSize() +
			",\"frame_id\":" + resource.frameId() + "}";
		try (OutputStream output = sink.open(fileName)) {
			output.write(json.getBytes(StandardCharsets.UTF_8));
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static IrisRenderingPipeline pipeline() {
		if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline) {
			return pipeline;
		}
		throw new IllegalStateException("No Iris shader pipeline is active");
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record TextureHandle(int id) {
	}

	private record ResolvedTarget(CapturePlan.Target target, int textureId, ShaderStorageBuffer buffer) {
	}
}
