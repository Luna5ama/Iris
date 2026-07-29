package net.irisshaders.iris.vibris;

import dev.vibris.core.VibrisBootstrap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;

import java.nio.file.Path;

public final class IrisVibrisLifecycle {
	private static final Object LOCK = new Object();
	private static IrisVibrisFrameClock frames;
	private static VibrisBootstrap bootstrap;

	private IrisVibrisLifecycle() {
	}

	public static void initializeProbe() {
		try {
			Path gameDirectory = IrisPlatformHelpers.getInstance().getGameDir().toAbsolutePath().normalize();
			IrisVibrisPhase4Probe.initialize(gameDirectory);
		} catch (Exception exception) {
			Iris.logger.error("Failed to initialize the Vibris Phase 4 probe.", exception);
		}
	}

	public static void start() {
		synchronized (LOCK) {
			if (bootstrap != null) return;
			IrisVibrisFrameClock candidateFrames = new IrisVibrisFrameClock();
			IrisVibrisRuntimeAdapter adapter = null;
			try {
				Path gameDirectory = IrisPlatformHelpers.getInstance().getGameDir().toAbsolutePath().normalize();
				adapter = new IrisVibrisRuntimeAdapter(
					new MinecraftVibrisRuntimeHost(gameDirectory), candidateFrames);
				VibrisBootstrap.Config config = new VibrisBootstrap.Config(
					integerProperty("vibris.port", 50051),
					pathProperty("vibris.pendingShadersRoot", Path.of("R:/shaders")),
					pathProperty("vibris.artifactRoot", Path.of("R:/vibris/artifacts")),
					gameDirectory.resolve("shaderpacks/vibris"));
				bootstrap = VibrisBootstrap.start(config, adapter);
				frames = candidateFrames;
				Iris.logger.info("Vibris control service listening on 127.0.0.1:{}", bootstrap.port());
				IrisVibrisPhase4Probe.serverReady(bootstrap.port(), config.pendingShadersRoot());
			} catch (Exception exception) {
				if (adapter != null) adapter.close();
				else candidateFrames.close();
				IrisVibrisPhase4Probe.shutdownComplete();
				Iris.logger.error("Vibris startup failed; the control service will remain unavailable.", exception);
			}
		}
	}

	public static void renderedFrame() {
		IrisVibrisFrameClock current = frames;
		if (current != null) {
			current.renderedFrame();
			IrisVibrisPhase4Probe.frameTail(current.currentFrame());
		}
	}

	public static void clientFrameTail(boolean renderedWorldFrame) {
		if (renderedWorldFrame) renderedFrame();
		IrisVibrisPhase4Probe.clientFrameTail();
	}

	static long currentFrame() {
		IrisVibrisFrameClock current = frames;
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
				IrisVibrisPhase4Probe.shutdownComplete();
			}
		}
	}

	private static int integerProperty(String name, int fallback) {
		String value = System.getProperty(name);
		if (value == null) return fallback;
		int parsed = Integer.parseInt(value);
		if (parsed < 1 || parsed > 65535) throw new IllegalArgumentException(name + " is outside the port range");
		return parsed;
	}

	private static Path pathProperty(String name, Path fallback) {
		String value = System.getProperty(name);
		return (value == null ? fallback : Path.of(value)).toAbsolutePath().normalize();
	}
}
