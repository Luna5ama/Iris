package net.irisshaders.iris.vibris;

import dev.luna5ama.vibris.capture.VibrisPresetCatalog;
import dev.vibris.api.CancellationToken;
import dev.vibris.api.ArtifactSink;
import dev.vibris.api.CapturePlan;
import dev.vibris.api.CaptureResult;
import dev.vibris.api.ContextApplyResult;
import dev.vibris.api.ContextValidationResult;
import dev.vibris.api.ReloadResult;
import dev.vibris.api.ResourceCatalog;
import dev.vibris.api.RuntimeStatus;
import dev.vibris.api.SceneContext;
import dev.vibris.api.ScenePreset;
import dev.vibris.api.TemporalResetResult;
import dev.vibris.core.VibrisRuntimeHost;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

public final class MinecraftVibrisRuntimeHost implements VibrisRuntimeHost {
	private final Minecraft minecraft;
	private final MinecraftContextController contexts;
	private final VibrisPresetCatalog presets;
	private final MinecraftVibrisCapture capture;
	private final Path shaderLink;
	private volatile SceneContext activeContext;

	public MinecraftVibrisRuntimeHost(Path gameDirectory) throws IOException {
		minecraft = Minecraft.getInstance();
		presets = VibrisPresetCatalog.load(gameDirectory.resolve("config/vibris/presets.json"));
		contexts = new MinecraftContextController(minecraft, presets);
		capture = new MinecraftVibrisCapture(minecraft);
		shaderLink = gameDirectory.resolve("shaderpacks/vibris/shaders");
	}

	@Override
	public List<ScenePreset> presets() {
		return presets.presets();
	}

	@Override
	public ContextValidationResult validateContext(SceneContext context) {
		return presets.validate(context);
	}

	String savePreset(String id) throws IOException {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.level == null || minecraft.player == null) {
			throw new IllegalStateException("A singleplayer world must be loaded");
		}
		String weather = minecraft.level.getThunderLevel(1.0f) > 0.0f ? "thunder" :
			minecraft.level.getRainLevel(1.0f) > 0.0f ? "rain" : "clear";
		String dimension = minecraft.level.dimension().identifier().toString();
		String save = MinecraftContextController.runningSave(server);
		return presets.save(new VibrisPresetCatalog.Preset(
			id,
			save,
			save,
			dimension,
			minecraft.player.getX(),
			minecraft.player.getY(),
			minecraft.player.getZ(),
			minecraft.player.getYRot(),
			minecraft.player.getXRot(),
			minecraft.options.fov().get(),
			minecraft.level.getDayTime(),
			weather,
			new SceneContext.Resolution(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()),
			"default"
		));
	}

	@Override
	public boolean isClientThread() {
		return minecraft.isSameThread();
	}

	@Override
	public void executeOnClient(Runnable task) {
		minecraft.execute(task);
	}

	@Override
	public RuntimeStatus status() {
		SceneContext context = activeContext;
		return new RuntimeStatus(
			minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null,
			context == null ? "" : context.saveId(),
			minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString(),
			"");
	}

	@Override
	public CompletionStage<ContextApplyResult> applyContext(SceneContext context, CancellationToken cancellation) {
		return contexts.apply(context, cancellation).thenApply(result -> {
			if (result.successful()) {
				activeContext = result.context();
				IrisVibrisAutomation.contextApplied(result.context(), minecraft);
			}
			return result;
		});
	}

	@Override
	public ReloadResult reload(CancellationToken cancellation) {
		cancellation.throwIfCancellationRequested();
		ReloadResult result = Iris.reloadVibrisShaderpack();
		IrisVibrisAutomation.shaderReloaded(
			result.successful(),
			shaderLink,
			Iris.getPipelineManager().getPipelineNullable(),
			IrisVibrisLifecycle.currentFrame());
		return result;
	}

	@Override
	public TemporalResetResult resetTemporal(CancellationToken cancellation) {
		cancellation.throwIfCancellationRequested();
		SystemTimeUniforms.COUNTER.reset();
		SystemTimeUniforms.TIMER.reset();
		CapturedRenderingState.INSTANCE.resetTextureReloadCount();
		IrisVibrisAutomation.temporalReset();
		return new TemporalResetResult(true);
	}

	@Override
	public ResourceCatalog resourceCatalog(long frameId) {
		return capture.resourceCatalog(frameId);
	}

	@Override
	public CaptureResult capture(
		CapturePlan plan,
		ArtifactSink sink,
		long frameId,
		CancellationToken cancellation
	) {
		CaptureResult result = capture.capture(plan, sink, frameId, cancellation);
		IrisVibrisAutomation.captureComplete(plan, result.frameId());
		return result;
	}

	@Override
	public void close() {
		if ("vibris".equals(Iris.getCurrentPackName()) &&
			Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline) {
			Iris.getPipelineManager().destroyPipeline();
		}
		activeContext = null;
	}
}
