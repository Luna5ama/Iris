package net.irisshaders.iris.vibris;

import dev.vibris.api.CancellationToken;
import dev.vibris.api.ArtifactSink;
import dev.vibris.api.CapturePlan;
import dev.vibris.api.CaptureResult;
import dev.vibris.api.ContextApplyResult;
import dev.vibris.api.ReloadResult;
import dev.vibris.api.ResourceCatalog;
import dev.vibris.api.RuntimeStatus;
import dev.vibris.api.SceneContext;
import dev.vibris.api.TemporalResetResult;

import java.util.concurrent.CompletionStage;

public interface IrisVibrisRuntimeHost extends AutoCloseable {
	boolean isClientThread();

	void executeOnClient(Runnable task);

	RuntimeStatus status();

	CompletionStage<ContextApplyResult> applyContext(SceneContext context, CancellationToken cancellation);

	ReloadResult reload(CancellationToken cancellation);

	TemporalResetResult resetTemporal(CancellationToken cancellation);

	ResourceCatalog resourceCatalog(long frameId);

	CaptureResult capture(
		CapturePlan plan,
		ArtifactSink sink,
		long frameId,
		CancellationToken cancellation
	);

	@Override
	void close();
}
