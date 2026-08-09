package net.irisshaders.iris;

import dev.luna5ama.vibris.capture.ShaderDebugHost;
import dev.luna5ama.vibris.capture.StorageBufferInfo;
import dev.luna5ama.vibris.capture.TextureCatalog;
import dev.luna5ama.vibris.capture.TextureInfo;
import dev.vibris.api.ReloadResult;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.pipeline.CustomTextureManager;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class IrisShaderDebugHost implements ShaderDebugHost {
	private final Supplier<ReloadResult> shaderReloader;

	public IrisShaderDebugHost() {
		this(Iris::reloadVibrisShaderpack);
	}

	IrisShaderDebugHost(Supplier<ReloadResult> shaderReloader) {
		this.shaderReloader = shaderReloader;
	}

	@Override
	public String shaderPackName() {
		return Iris.getCurrentPackName();
	}

	@Override
	public void reloadShaders() {
		ReloadResult result = shaderReloader.get();
		if (!result.successful()) {
			String message = result.diagnostics().stream()
				.filter(diagnostic -> diagnostic.severity() == ReloadResult.Severity.ERROR)
				.map(ReloadResult.Diagnostic::message)
				.findFirst()
				.orElse("Fixed Vibris shaderpack reload failed");
			throw new IllegalStateException(message);
		}
	}

	@Override
	public Path gameDirectory() {
		return IrisPlatformHelpers.getInstance().getGameDir();
	}

	@Override
	public boolean debugShadersEnabled() {
		return Iris.getIrisConfig().areDebugOptionsEnabled();
	}

	@Override
	public List<StorageBufferInfo> storageBuffers() {
		return ShaderStorageBufferHolder.getActiveBuffers().stream()
			.map(buffer -> new StorageBufferInfo(buffer.getIndex(), buffer.getId()))
			.toList();
	}

	@Override
	public TextureCatalog textureCatalog() {
		IrisRenderingPipeline pipeline = pipeline();
		RenderTargets targets = pipeline.getRenderTargetsForDebug();
		List<TextureInfo> colortex = new ArrayList<>();
		for (int index = 0; index < targets.getRenderTargetCount(); index++) {
			RenderTarget target = targets.get(index);
			if (target != null) {
				colortex.add(new TextureInfo(
					"colortex" + index,
					target.getMainTexture(),
					target.getWidth(),
					target.getHeight()
				));
			}
		}

		Map<String, Integer> customIds = customTextures(pipeline.getCustomTextureManager());
		List<TextureInfo> custom = customIds.entrySet().stream()
			.map(entry -> new TextureInfo(entry.getKey(), entry.getValue(), null, null))
			.toList();
		return new TextureCatalog(colortex, custom);
	}

	@Override
	public Integer resolveTexture(String name) {
		IrisRenderingPipeline pipeline = pipeline();
		if (name.startsWith("colortex")) {
			try {
				int index = Integer.parseInt(name.substring("colortex".length()));
				RenderTargets targets = pipeline.getRenderTargetsForDebug();
				if (index >= 0 && index < targets.getRenderTargetCount()) {
					RenderTarget target = targets.get(index);
					return target == null ? null : target.getMainTexture();
				}
			} catch (NumberFormatException ignored) {
				return null;
			}
		}

		CustomTextureManager manager = pipeline.getCustomTextureManager();
		Integer custom = customTextures(manager).get(name);
		if (custom != null) return custom;
		if (name.equals("noisetex")) return textureId(manager.getNoiseTexture());
		return null;
	}

	private static IrisRenderingPipeline pipeline() {
		if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline) {
			return pipeline;
		}
		throw new IllegalStateException("No Iris shader pipeline is active");
	}

	private static Map<String, Integer> customTextures(CustomTextureManager manager) {
		Map<String, Integer> textures = new LinkedHashMap<>();
		manager.getCustomTextureIdMap().values().forEach(stage ->
			stage.forEach((name, texture) -> textures.putIfAbsent(name, textureId(texture))));
		manager.getIrisCustomTextures().forEach((name, texture) -> textures.putIfAbsent(name, textureId(texture)));
		return textures;
	}

	private static int textureId(TextureAccess texture) {
		return texture.getTextureId().getAsInt();
	}

}
