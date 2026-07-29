package net.irisshaders.iris.vibris;

import dev.vibris.api.CancellationToken;
import dev.vibris.api.ContextApplyResult;
import dev.vibris.api.ReloadResult;
import dev.vibris.api.RuntimeStatus;
import dev.vibris.api.SceneContext;
import dev.vibris.api.TemporalResetResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisVibrisRuntimeAdapterTest {
	@Test
	void activateContextAndWaitRenderedFrames() {
		ControlledHost host = new ControlledHost();
		IrisVibrisFrameClock frames = new IrisVibrisFrameClock();
		IrisVibrisRuntimeAdapter adapter = new IrisVibrisRuntimeAdapter(host, frames);
		SceneContext expected = new SceneContext(
			"shader-test-world",
			"minecraft:overworld",
			"sunset",
			"clear",
			"village-rooftop",
			70.0,
			new SceneContext.Resolution(1280, 720),
			"phase4"
		);

		var context = adapter.ensureWorldAndContext(expected, CancellationToken.none()).toCompletableFuture();
		assertFalse(context.isDone(), "context work must be queued to the Minecraft client thread");
		host.runClientTasks();
		assertEquals(expected, context.join().context());
		assertEquals(expected, host.appliedContext);

		var wait = adapter.waitRenderedFrames(32, CancellationToken.none()).toCompletableFuture();
		for (int frame = 0; frame < 31; frame++) frames.renderedFrame();
		assertFalse(wait.isDone(), "31 render-tail notifications must not satisfy a 32-frame wait");
		frames.renderedFrame();
		assertEquals(32L, wait.join());
		assertTrue(host.clientThreadCalls > 0);
	}

	@Test
	void cancelledFrameWaitCompletesWithoutAnotherFrame() throws Exception {
		IrisVibrisFrameClock frames = new IrisVibrisFrameClock();
		CancellationToken.Source cancellation = CancellationToken.source();
		var wait = frames.waitRenderedFrames(32, cancellation.token()).toCompletableFuture();

		cancellation.cancel();
		assertThrows(CancellationException.class, () -> wait.get(1, TimeUnit.SECONDS));
	}

	private static final class ControlledHost implements IrisVibrisRuntimeHost {
		private final Queue<Runnable> clientTasks = new ArrayDeque<>();
		private SceneContext appliedContext;
		private boolean clientThread;
		private int clientThreadCalls;

		@Override
		public boolean isClientThread() {
			return clientThread;
		}

		@Override
		public void executeOnClient(Runnable task) {
			clientTasks.add(task);
		}

		@Override
		public RuntimeStatus status() {
			requireClientThread();
			return new RuntimeStatus(true, "shader-test-world", "minecraft:overworld", "source-a");
		}

		@Override
		public CompletableFuture<ContextApplyResult> applyContext(
			SceneContext context,
			CancellationToken cancellation
		) {
			requireClientThread();
			appliedContext = context;
			return CompletableFuture.completedFuture(ContextApplyResult.success(context));
		}

		@Override
		public ReloadResult reload(CancellationToken cancellation) {
			requireClientThread();
			return ReloadResult.success(List.of());
		}

		@Override
		public TemporalResetResult resetTemporal(CancellationToken cancellation) {
			requireClientThread();
			return new TemporalResetResult(true);
		}

		@Override
		public void close() {
		}

		void runClientTasks() {
			clientThread = true;
			try {
				while (!clientTasks.isEmpty()) clientTasks.remove().run();
			} finally {
				clientThread = false;
			}
		}

		private void requireClientThread() {
			assertTrue(clientThread);
			clientThreadCalls++;
		}
	}
}
