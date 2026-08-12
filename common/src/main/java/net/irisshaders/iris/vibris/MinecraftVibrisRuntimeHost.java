package net.irisshaders.iris.vibris;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.luna5ama.vibris.capture.CaptureActionExecutor;
import dev.luna5ama.vibris.capture.VibrisPresetCatalog;
import dev.vibris.api.CancellationToken;
import dev.vibris.api.ArtifactSink;
import dev.vibris.api.CapturePlan;
import dev.vibris.api.CaptureResourceNotFoundException;
import dev.vibris.api.CaptureResult;
import dev.vibris.api.CompileCatalog;
import dev.vibris.api.ContextApplyResult;
import dev.vibris.api.ContextValidationResult;
import dev.vibris.api.DeterministicTemporalCaptureOutcome;
import dev.vibris.api.DeterministicTemporalCapturePlanner;
import dev.vibris.api.DeterministicTemporalCapturePlanning;
import dev.vibris.api.DeterministicTemporalCaptureReloaded;
import dev.vibris.api.DeterministicTemporalCaptureRequest;
import dev.vibris.api.RuntimeAction;
import dev.vibris.api.ReloadResult;
import dev.vibris.api.ResourceCatalog;
import dev.vibris.api.RuntimeEnvironment;
import dev.vibris.api.RuntimeStatus;
import dev.vibris.api.SceneContext;
import dev.vibris.api.ScenePreset;
import dev.vibris.api.TemporalResetResult;
import dev.vibris.core.ArtifactManager;
import dev.vibris.core.DeterministicTemporalCaptureScheduler;
import dev.vibris.core.RenderedFrameClock;
import dev.vibris.core.ShaderConfigFile;
import dev.vibris.core.VibrisRuntimeHost;
import net.irisshaders.iris.BuildConfig;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class MinecraftVibrisRuntimeHost implements VibrisRuntimeHost {
	private final Minecraft minecraft;
	private final MinecraftContextController contexts;
	private final VibrisPresetCatalog presets;
	private final MinecraftVibrisCapture capture;
	private final CaptureActionExecutor actions;
	private final Path shaderLink;
	private final Path shaderConfigTarget;
	private volatile Path shaderConfigScratch;
	private volatile SceneContext activeContext;
	private volatile boolean closed;

	public MinecraftVibrisRuntimeHost(Path gameDirectory) throws IOException {
		minecraft = Minecraft.getInstance();
		presets = VibrisPresetCatalog.load(gameDirectory.resolve("config/vibris/presets.json"));
		contexts = new MinecraftContextController(minecraft, presets);
		capture = new MinecraftVibrisCapture(minecraft);
		actions = new CaptureActionExecutor(gameDirectory, Iris.getCaptureManager(), Iris.getShaderDebugControl());
		shaderLink = gameDirectory.resolve("shaderpacks/vibris/shaders");
		shaderConfigTarget = gameDirectory.resolve("shaderpacks/vibris.txt");
		shaderConfigScratch = gameDirectory.resolve("vibris/config/vibris.txt");
	}

	void configureShaderConfigScratch(Path pendingRoot) {
		shaderConfigScratch = pendingRoot.resolveSibling("config").resolve("vibris.txt");
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
		GLFW.glfwPostEmptyEvent();
	}

	@Override
	public RuntimeEnvironment runtimeEnvironment() {
		if (!isClientThread()) {
			throw new IllegalStateException("Runtime environment must be queried on the Minecraft client thread");
		}

		GpuDevice device = RenderSystem.getDevice();
		String glVersion = device.getVersion();
		return runtimeEnvironment(
			SharedConstants.getCurrentVersion().name(),
			Iris.getVersion(),
			BuildConfig.VIBRIS_VERSION,
			Runtime.version().toString(),
			Platform.get().getName() + " " + Platform.getArchitecture(),
			device.getVendor(),
			device.getRenderer(),
			glVersion,
			glVersion
		);
	}

	static RuntimeEnvironment runtimeEnvironment(
		String minecraftVersion,
		String irisVersion,
		String vibrisVersion,
		String javaVersion,
		String operatingSystem,
		String gpuVendor,
		String gpuRenderer,
		String openglVersion,
		String driverVersion
	) {
		return new RuntimeEnvironment(
			minecraftVersion,
			irisVersion,
			vibrisVersion,
			javaVersion,
			operatingSystem,
			gpuVendor,
			gpuRenderer,
			openglVersion,
			driverVersion
		);
	}

	@Override
	public RuntimeStatus status() {
		SceneContext context = activeContext;
		return new RuntimeStatus(
			!closed,
			context == null ? "" : context.saveId(),
			minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString(),
			"");
	}

	@Override
	public CompletionStage<String> executeAction(RuntimeAction action) {
		return actions.execute(action);
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
	public ReloadResult reload(Map<String, String> config, CancellationToken cancellation) {
		cancellation.throwIfCancellationRequested();
		minecraft.setScreen(null);
		minecraft.options.hideGui = true;
		if (config != null) {
			writeShaderConfig(config);
		}
		ReloadResult result = Iris.reloadVibrisShaderpack(config);
		IrisVibrisAutomation.shaderReloaded(
			result.successful(),
			shaderLink,
			Iris.getPipelineManager().getPipelineNullable(),
			IrisVibrisLifecycle.currentFrame());
		return result;
	}

	private void writeShaderConfig(Map<String, String> config) {
		ShaderConfigFile.write(shaderConfigTarget, shaderConfigScratch, config);
	}

	@Override
	public CompileCatalog compileCatalog(CancellationToken cancellation) {
		cancellation.throwIfCancellationRequested();
		return IrisVibrisCompileCatalog.current();
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
	public CompletionStage<DeterministicTemporalCaptureOutcome> captureDeterministicTemporalPhase(
		DeterministicTemporalCaptureRequest request,
		DeterministicTemporalCapturePlanner planner,
		ArtifactSink sink,
		DeterministicTemporalCaptureScheduler scheduler,
		CancellationToken cancellation
	) {
		CompletableFuture<DeterministicTemporalCaptureOutcome> result = new CompletableFuture<>();
		CompletionStage<ContextApplyResult> contextStage;
		try {
			contextStage = applyContext(request.context(), cancellation);
		} catch (Throwable failure) {
			result.complete(contextRejected(request, failure));
			return result;
		}

		contextStage.whenComplete((context, contextFailure) -> runOnClient(result, () -> {
			if (result.isDone()) return;
			if (contextFailure != null) {
				result.complete(contextRejected(request, unwrap(contextFailure)));
				return;
			}
			if (!context.successful()) {
				result.complete(new DeterministicTemporalCaptureOutcome.ContextRejected(
					context,
					failure(DeterministicTemporalCaptureOutcome.FailureKind.OPERATION_FAILED, context.message())
				));
				return;
			}
			startDeterministicTemporalPhase(request, planner, sink, scheduler, cancellation, context, result);
		}));
		return result;
	}

	private void startDeterministicTemporalPhase(
		DeterministicTemporalCaptureRequest request,
		DeterministicTemporalCapturePlanner planner,
		ArtifactSink sink,
		DeterministicTemporalCaptureScheduler scheduler,
		CancellationToken cancellation,
		ContextApplyResult context,
		CompletableFuture<DeterministicTemporalCaptureOutcome> result
	) {
		if (result.isDone()) return;
		ReloadResult reload;
		try {
			reload = reload(request.preserveCurrentSettings() ? null : request.settings(), cancellation);
		} catch (Throwable failure) {
			Throwable cause = unwrap(failure);
			ReloadResult rejected = ReloadResult.failure(List.of(new ReloadResult.Diagnostic(
				ReloadResult.Severity.ERROR, "shaderpack", 0, failureMessage(cause))));
			result.complete(new DeterministicTemporalCaptureOutcome.ReloadRejected(
				context, rejected, failure(cause, false)));
			return;
		}
		if (!reload.successful()) {
			String message = reload.diagnostics().stream()
				.map(ReloadResult.Diagnostic::message)
				.filter(candidate -> candidate != null && !candidate.isBlank())
				.findFirst()
				.orElse("Shader reload was rejected");
			result.complete(new DeterministicTemporalCaptureOutcome.ReloadRejected(
				context,
				reload,
				failure(DeterministicTemporalCaptureOutcome.FailureKind.OPERATION_FAILED, message)
			));
			return;
		}

		long reloadCompletedAtUnixMs = currentUnixMs();
		ResourceCatalog resources;
		CompileCatalog compile;
		try {
			cancellation.throwIfCancellationRequested();
			resources = resourceCatalog(scheduler.currentFrame());
			compile = compileCatalog(cancellation);
		} catch (Throwable failure) {
			result.completeExceptionally(unwrap(failure));
			return;
		}
		DeterministicTemporalCaptureReloaded reloaded = new DeterministicTemporalCaptureReloaded(
			context, reload, reloadCompletedAtUnixMs, resources, compile);
		DeterministicTemporalCapturePlanning planning;
		try {
			cancellation.throwIfCancellationRequested();
			planning = planner.plan(resources, compile);
		} catch (Throwable failure) {
			result.complete(new DeterministicTemporalCaptureOutcome.PlanningRejected(
				reloaded, failure(unwrap(failure), false)));
			return;
		}
		if (planning instanceof DeterministicTemporalCapturePlanning.Rejected rejected) {
			result.complete(new DeterministicTemporalCaptureOutcome.PlanningRejected(
				reloaded, rejected.failure()));
			return;
		}
		CapturePlan plan = ((DeterministicTemporalCapturePlanning.Planned) planning).plan();

		TemporalResetResult reset;
		try {
			reset = resetTemporal(cancellation);
		} catch (Throwable failure) {
			result.complete(new DeterministicTemporalCaptureOutcome.ResetRejected(
				reloaded, plan, new TemporalResetResult(false), failure(unwrap(failure), false)));
			return;
		}
		if (!reset.successful()) {
			result.complete(new DeterministicTemporalCaptureOutcome.ResetRejected(
				reloaded,
				plan,
				reset,
				failure(DeterministicTemporalCaptureOutcome.FailureKind.OPERATION_FAILED,
					"Temporal reset was rejected")
			));
			return;
		}

		long resetCompletedAtUnixMs = currentUnixMs();
		long anchorFrame;
		long warmupEndFrame;
		long captureFrame;
		try {
			anchorFrame = scheduler.currentFrame();
			warmupEndFrame = Math.addExact(anchorFrame, request.warmupFrames());
			captureFrame = Math.incrementExact(warmupEndFrame);
		} catch (Throwable failure) {
			result.completeExceptionally(unwrap(failure));
			return;
		}

		SystemTimeUniforms.DeterministicTimeScope timeScope;
		try {
			timeScope = SystemTimeUniforms.beginDeterministicTime();
		} catch (Throwable failure) {
			completeRejectedBeforeSchedule(
				result, reloaded, plan, reset, resetCompletedAtUnixMs, request.warmupFrames(),
				anchorFrame, warmupEndFrame, captureFrame, unwrap(failure), null);
			return;
		}

		DeterministicTemporalCaptureScheduler.ScheduledCapture scheduled;
		try {
			scheduled = scheduler.schedule(
				request.warmupFrames(),
				cancellation,
				frameId -> capture(plan, sink, frameId, cancellation));
		} catch (Throwable failure) {
			Throwable cleanupFailure = closeTimeScope(timeScope);
			completeRejectedBeforeSchedule(
				result, reloaded, plan, reset, resetCompletedAtUnixMs, request.warmupFrames(),
				anchorFrame, warmupEndFrame, captureFrame, unwrap(failure), cleanupFailure);
			return;
		}

		scheduled.capture().whenComplete((captured, operationFailure) -> {
			Runnable finish = () -> completeScheduledCapture(
				result, reloaded, plan, reset, resetCompletedAtUnixMs, scheduled, timeScope,
				captured, operationFailure == null ? null : unwrap(operationFailure));
			try {
				if (isClientThread()) finish.run();
				else executeOnClient(finish);
			} catch (Throwable submissionFailure) {
				completeScheduledCapture(
					result, reloaded, plan, reset, resetCompletedAtUnixMs, scheduled, timeScope,
					captured, operationFailure == null ? unwrap(submissionFailure) : unwrap(operationFailure));
			}
		});
	}

	static void completeScheduledCapture(
		CompletableFuture<DeterministicTemporalCaptureOutcome> result,
		DeterministicTemporalCaptureReloaded reloaded,
		CapturePlan plan,
		TemporalResetResult reset,
		long resetCompletedAtUnixMs,
		DeterministicTemporalCaptureScheduler.ScheduledCapture scheduled,
		SystemTimeUniforms.DeterministicTimeScope timeScope,
		CaptureResult captured,
		Throwable operationFailure
	) {
		long terminalFrame;
		try {
			terminalFrame = scheduled.terminalFrame().toCompletableFuture().join();
		} catch (Throwable terminalFailure) {
			closeTimeScope(timeScope);
			result.completeExceptionally(unwrap(terminalFailure));
			return;
		}
		Throwable cleanupFailure = closeTimeScope(timeScope);
		if (operationFailure != null || cleanupFailure != null) {
			completeRejected(
				result, reloaded, plan, reset, resetCompletedAtUnixMs, scheduled.warmupFrames(),
				scheduled.anchorFrame(), scheduled.warmupEndFrame(), scheduled.captureFrame(), terminalFrame,
				operationFailure, cleanupFailure);
			return;
		}

		try {
			result.complete(new DeterministicTemporalCaptureOutcome.Captured(
				reloaded,
				plan,
				reset,
				resetCompletedAtUnixMs,
				scheduled.warmupFrames(),
				scheduled.anchorFrame(),
				scheduled.warmupEndFrame(),
				captured
			));
		} catch (Throwable invalidCapture) {
			completeRejected(
				result, reloaded, plan, reset, resetCompletedAtUnixMs, scheduled.warmupFrames(),
				scheduled.anchorFrame(), scheduled.warmupEndFrame(), scheduled.captureFrame(), terminalFrame,
				new InvalidDeterministicCaptureException(failureMessage(invalidCapture), invalidCapture), null);
		}
	}

	private static void completeRejectedBeforeSchedule(
		CompletableFuture<DeterministicTemporalCaptureOutcome> result,
		DeterministicTemporalCaptureReloaded reloaded,
		CapturePlan plan,
		TemporalResetResult reset,
		long resetCompletedAtUnixMs,
		int warmupFrames,
		long anchorFrame,
		long warmupEndFrame,
		long captureFrame,
		Throwable operationFailure,
		Throwable cleanupFailure
	) {
		completeRejected(
			result, reloaded, plan, reset, resetCompletedAtUnixMs, warmupFrames,
			anchorFrame, warmupEndFrame, captureFrame, anchorFrame, operationFailure, cleanupFailure);
	}

	private static void completeRejected(
		CompletableFuture<DeterministicTemporalCaptureOutcome> result,
		DeterministicTemporalCaptureReloaded reloaded,
		CapturePlan plan,
		TemporalResetResult reset,
		long resetCompletedAtUnixMs,
		int warmupFrames,
		long anchorFrame,
		long warmupEndFrame,
		long captureFrame,
		long terminalFrame,
		Throwable operationFailure,
		Throwable cleanupFailure
	) {
		Throwable primary = operationFailure == null ? cleanupFailure : operationFailure;
		if (operationFailure != null && cleanupFailure != null) operationFailure.addSuppressed(cleanupFailure);
		DeterministicTemporalCaptureOutcome.Failure detail = failure(primary, operationFailure == null);
		if (warmupFrames > 0 && terminalFrame < warmupEndFrame) {
			result.complete(new DeterministicTemporalCaptureOutcome.WarmupRejected(
				reloaded,
				plan,
				reset,
				resetCompletedAtUnixMs,
				warmupFrames,
				anchorFrame,
				Math.toIntExact(terminalFrame - anchorFrame),
				terminalFrame,
				detail
			));
			return;
		}
		result.complete(new DeterministicTemporalCaptureOutcome.CaptureRejected(
			reloaded,
			plan,
			reset,
			resetCompletedAtUnixMs,
			warmupFrames,
			anchorFrame,
			warmupEndFrame,
			captureFrame,
			terminalFrame,
			detail
		));
	}

	private static DeterministicTemporalCaptureOutcome.ContextRejected contextRejected(
		DeterministicTemporalCaptureRequest request,
		Throwable failure
	) {
		Throwable cause = unwrap(failure);
		ContextApplyResult context = ContextApplyResult.failure(request.context(), failureMessage(cause));
		return new DeterministicTemporalCaptureOutcome.ContextRejected(context, failure(cause, false));
	}

	private void runOnClient(CompletableFuture<?> result, Runnable task) {
		try {
			if (isClientThread()) task.run();
			else executeOnClient(task);
		} catch (Throwable failure) {
			result.completeExceptionally(unwrap(failure));
		}
	}

	private static Throwable closeTimeScope(SystemTimeUniforms.DeterministicTimeScope timeScope) {
		try {
			timeScope.close();
			return null;
		} catch (Throwable failure) {
			return unwrap(failure);
		}
	}

	private static DeterministicTemporalCaptureOutcome.Failure failure(
		Throwable failure,
		boolean cleanupOnly
	) {
		DeterministicTemporalCaptureOutcome.FailureKind kind = cleanupOnly ?
			DeterministicTemporalCaptureOutcome.FailureKind.CLEANUP_FAILED : failureKind(failure);
		return failure(kind, failureMessage(failure));
	}

	private static DeterministicTemporalCaptureOutcome.FailureKind failureKind(Throwable failure) {
		Throwable cause = failure;
		while (cause != null) {
			if (cause instanceof CancellationException) {
				return DeterministicTemporalCaptureOutcome.FailureKind.CANCELLED;
			}
			if (cause instanceof RenderedFrameClock.TargetMissedException ||
				cause instanceof RenderedFrameClock.AnchorMismatchException) {
				return DeterministicTemporalCaptureOutcome.FailureKind.MISSED_TARGET;
			}
			if (cause instanceof CaptureResourceNotFoundException) {
				return DeterministicTemporalCaptureOutcome.FailureKind.RESOURCE_NOT_FOUND;
			}
			if (cause instanceof ArtifactManager.JobTooLargeException || cause instanceof ArithmeticException) {
				return DeterministicTemporalCaptureOutcome.FailureKind.ARTIFACT_TOO_LARGE;
			}
			if (cause instanceof ArtifactManager.QuotaExceededException) {
				return DeterministicTemporalCaptureOutcome.FailureKind.ARTIFACT_QUOTA_EXCEEDED;
			}
			if (cause instanceof InvalidDeterministicCaptureException) {
				return DeterministicTemporalCaptureOutcome.FailureKind.INVALID_CAPTURE;
			}
			if (cause == cause.getCause()) break;
			cause = cause.getCause();
		}
		return DeterministicTemporalCaptureOutcome.FailureKind.OPERATION_FAILED;
	}

	private static DeterministicTemporalCaptureOutcome.Failure failure(
		DeterministicTemporalCaptureOutcome.FailureKind kind,
		String message
	) {
		String detail = message == null || message.isBlank() ? "Vibris operation failed" : message;
		return new DeterministicTemporalCaptureOutcome.Failure(kind, detail);
	}

	private static String failureMessage(Throwable failure) {
		StringBuilder message = new StringBuilder();
		String primary = failure.getMessage();
		message.append(primary == null || primary.isBlank() ? failure.getClass().getSimpleName() : primary);
		for (Throwable suppressed : failure.getSuppressed()) {
			message.append("; suppressed: ").append(
				suppressed.getMessage() == null ? suppressed.getClass().getSimpleName() : suppressed.getMessage());
		}
		return message.toString();
	}

	private static Throwable unwrap(Throwable failure) {
		Throwable current = failure;
		while (current instanceof CompletionException && current.getCause() != null) current = current.getCause();
		return current;
	}

	private static long currentUnixMs() {
		return Math.max(1L, System.currentTimeMillis());
	}

	private static final class InvalidDeterministicCaptureException extends IllegalStateException {
		private InvalidDeterministicCaptureException(String message, Throwable cause) {
			super(message, cause);
		}
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
	public CompletionStage<CapturePlan.AfterPassReceipt> captureAfterPass(
		CapturePlan.AfterPassRequest request,
		ArtifactSink sink,
		CancellationToken cancellation
	) {
		return capture.captureAfterPass(request, sink, cancellation);
	}

	@Override
	public CompletionStage<CaptureResult> capturePatchedShaders(
		String artifactName,
		ArtifactSink sink,
		long frameId,
		CancellationToken cancellation
	) {
		return actions.capturePatchedShaders(artifactName, sink, frameId, cancellation);
	}

	@Override
	public void close() {
		closed = true;
		if ("vibris".equals(Iris.getCurrentPackName()) &&
			Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline) {
			Iris.getPipelineManager().destroyPipeline();
		}
		activeContext = null;
	}
}
