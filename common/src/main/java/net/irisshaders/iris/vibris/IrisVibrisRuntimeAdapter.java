package net.irisshaders.iris.vibris;

import dev.vibris.api.ArtifactSink;
import dev.vibris.api.CancellationToken;
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
import dev.vibris.api.VibrisRuntimeAdapter;

import java.util.Objects;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class IrisVibrisRuntimeAdapter implements VibrisRuntimeAdapter {
	private final IrisVibrisRuntimeHost host;
	private final IrisVibrisFrameClock frames;
	private final AtomicBoolean closed = new AtomicBoolean();
	private volatile ResourceCatalog catalog = ResourceCatalog.empty();

	public IrisVibrisRuntimeAdapter(IrisVibrisRuntimeHost host, IrisVibrisFrameClock frames) {
		this.host = Objects.requireNonNull(host, "host");
		this.frames = Objects.requireNonNull(frames, "frames");
	}

	@Override
	public CompletionStage<RuntimeStatus> getStatus() {
		return onClient(() -> {
			RuntimeStatus status = host.status();
			try {
				catalog = host.resourceCatalog(frames.currentFrame());
			} catch (IllegalStateException ignored) {
				catalog = ResourceCatalog.empty();
			}
			return status;
		}, CancellationToken.none());
	}

	@Override
	public CompletionStage<List<ScenePreset>> listPresets() {
		return onClient(host::presets, CancellationToken.none());
	}

	@Override
	public CompletionStage<ContextValidationResult> validateContext(SceneContext context) {
		return onClient(() -> host.validateContext(context), CancellationToken.none());
	}

	@Override
	public CompletionStage<ContextApplyResult> ensureWorldAndContext(
		SceneContext context,
		CancellationToken cancellation
	) {
		return onClientStage(() -> host.applyContext(context, cancellation), cancellation);
	}

	@Override
	public CompletionStage<ReloadResult> reloadVibrisShaderpack(CancellationToken cancellation) {
		return onClient(() -> {
			ReloadResult result = host.reload(cancellation);
			if (result.successful()) catalog = host.resourceCatalog(frames.currentFrame());
			return result;
		}, cancellation);
	}

	@Override
	public CompletionStage<TemporalResetResult> resetTemporalState(CancellationToken cancellation) {
		return onClient(() -> host.resetTemporal(cancellation), cancellation);
	}

	@Override
	public CompletionStage<Long> waitRenderedFrames(int frameCount, CancellationToken cancellation) {
		if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Vibris runtime is closed"));
		long start = frames.currentFrame();
		return frames.waitRenderedFrames(frameCount, cancellation).thenApply(end -> {
			IrisVibrisPhase4Probe.frameWaitComplete(start, end);
			return end;
		});
	}

	@Override
	public ResourceCatalog getResourceCatalog() {
		return catalog;
	}

	@Override
	public CompletionStage<CaptureResult> capture(
		CapturePlan plan,
		ArtifactSink sink,
		CancellationToken cancellation
	) {
		if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Vibris runtime is closed"));
		return frames.captureAtNextFrame(cancellation, frameId -> {
			CaptureResult result = host.capture(plan, sink, frameId, cancellation);
			catalog = host.resourceCatalog(frameId);
			return result;
		});
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		frames.close();
		if (host.isClientThread()) {
			host.close();
			return;
		}
		CompletableFuture<Void> complete = new CompletableFuture<>();
		host.executeOnClient(() -> {
			try {
				host.close();
				complete.complete(null);
			} catch (Throwable throwable) {
				complete.completeExceptionally(throwable);
			}
		});
		complete.join();
	}

	private <T> CompletionStage<T> onClient(Supplier<T> action, CancellationToken cancellation) {
		if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Vibris runtime is closed"));
		CompletableFuture<T> result = new CompletableFuture<>();
		Runnable task = () -> {
			try {
				cancellation.throwIfCancellationRequested();
				result.complete(action.get());
			} catch (Throwable throwable) {
				result.completeExceptionally(throwable);
			}
		};
		if (host.isClientThread()) task.run();
		else host.executeOnClient(task);
		return result;
	}

	private <T> CompletionStage<T> onClientStage(
		Supplier<CompletionStage<T>> action,
		CancellationToken cancellation
	) {
		return onClient(action, cancellation).thenCompose(stage -> stage);
	}
}
