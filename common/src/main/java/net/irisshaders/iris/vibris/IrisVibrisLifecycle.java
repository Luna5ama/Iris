package net.irisshaders.iris.vibris;

import dev.vibris.core.RenderedFrameClock;
import dev.vibris.core.ThreadBoundVibrisRuntimeAdapter;
import dev.vibris.core.VibrisBootstrap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;

import java.nio.file.Path;

public final class IrisVibrisLifecycle {
	private static final Object LOCK = new Object();
	private static RenderedFrameClock frames;
	private static VibrisBootstrap bootstrap;

	private IrisVibrisLifecycle() {
	}

	public static void initializeAutomation() {
		try {
			Path gameDirectory = IrisPlatformHelpers.getInstance().getGameDir().toAbsolutePath().normalize();
			IrisVibrisAutomation.initialize(gameDirectory);
		} catch (Exception exception) {
			Iris.logger.error("Failed to initialize Vibris automation.", exception);
		}
	}

	public static void start() {
		synchronized (LOCK) {
			if (bootstrap != null) return;
			RenderedFrameClock candidateFrames = new RenderedFrameClock();
			ThreadBoundVibrisRuntimeAdapter adapter = null;
			try {
				Path gameDirectory = IrisPlatformHelpers.getInstance().getGameDir().toAbsolutePath().normalize();
				adapter = new ThreadBoundVibrisRuntimeAdapter(
					new MinecraftVibrisRuntimeHost(gameDirectory), candidateFrames,
					IrisVibrisAutomation::frameWaitComplete);
				bootstrap = VibrisBootstrap.start(gameDirectory, adapter);
				frames = candidateFrames;
				if (bootstrap.ready()) {
					Iris.logger.info("Vibris control service listening on 127.0.0.1:{}", bootstrap.port());
					IrisVibrisAutomation.serverReady(bootstrap.port(), bootstrap.pendingShadersRoot());
				} else {
					Iris.logger.warn("Vibris control service is listening but not ready; inspect GetStatus errors.");
				}
			} catch (Exception exception) {
				if (adapter != null) adapter.close();
				else candidateFrames.close();
				IrisVibrisAutomation.shutdownComplete();
				Iris.logger.error("Vibris startup failed; the control service will remain unavailable.", exception);
			}
		}
	}

	public static void renderedFrame() {
		RenderedFrameClock current = frames;
		if (current != null) {
			current.renderedFrame();
			IrisVibrisAutomation.frameTail(current.currentFrame());
		}
	}

	public static void clientFrameTail(boolean renderedWorldFrame) {
		if (renderedWorldFrame) renderedFrame();
		IrisVibrisAutomation.clientFrameTail();
	}

	static long currentFrame() {
		RenderedFrameClock current = frames;
		return current == null ? 0 : current.currentFrame();
	}

	public static void close() {
		synchronized (LOCK) {
			VibrisBootstrap current = bootstrap;
			bootstrap = null;
			frames = null;
			if (current == null) return;
			try {
				current.close();
			} catch (Exception exception) {
				Iris.logger.error("Vibris shutdown did not complete cleanly.", exception);
			} finally {
				IrisVibrisAutomation.shutdownComplete();
			}
		}
	}
}
