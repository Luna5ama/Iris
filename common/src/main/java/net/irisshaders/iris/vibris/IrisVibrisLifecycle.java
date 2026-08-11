package net.irisshaders.iris.vibris;

import dev.vibris.core.RenderedFrameClock;
import dev.vibris.core.ThreadBoundVibrisRuntimeAdapter;
import dev.vibris.core.VibrisBootstrap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class IrisVibrisLifecycle {
	private static final Object LOCK = new Object();
	private static final int IDLE_FRAMERATE_LIMIT = 5;
	private static final long IDLE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(15);
	private static final long IDLE_FRAME_NANOS = TimeUnit.SECONDS.toNanos(1) / IDLE_FRAMERATE_LIMIT;
	private static final ThreadLocal<Boolean> IDLE_LIMIT_SELECTED = ThreadLocal.withInitial(() -> false);
	private static RenderedFrameClock frames;
	private static VibrisBootstrap bootstrap;
	private static volatile ThreadBoundVibrisRuntimeAdapter runtimeAdapter;
	private static volatile Thread idleWaitThread;
	private static volatile long idleSinceNanos = Long.MAX_VALUE;
	private static volatile MinecraftVibrisRuntimeHost host;

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
				MinecraftVibrisRuntimeHost candidateHost = new MinecraftVibrisRuntimeHost(gameDirectory);
				adapter = new ThreadBoundVibrisRuntimeAdapter(
					candidateHost, candidateFrames,
					IrisVibrisAutomation::frameWaitComplete,
					IrisVibrisLifecycle::runtimeActivityChanged);
				bootstrap = VibrisBootstrap.start(gameDirectory, adapter);
				if (bootstrap.pendingShadersRoot() != null) {
					candidateHost.configureShaderConfigScratch(bootstrap.pendingShadersRoot());
				}
				frames = candidateFrames;
				idleSinceNanos = System.nanoTime();
				runtimeAdapter = adapter;
				host = candidateHost;
				if (bootstrap.ready()) {
					Iris.logger.info("Vibris control service listening on 127.0.0.1:{}", bootstrap.port());
					IrisVibrisAutomation.serverReady(bootstrap.port(), bootstrap.pendingShadersRoot());
				} else {
					Iris.logger.warn("Vibris control service is listening but not ready; inspect GetStatus errors.");
				}
			} catch (Exception exception) {
				host = null;
				runtimeAdapter = null;
				idleSinceNanos = Long.MAX_VALUE;
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

	public static int idleFramerateLimit(int configuredLimit) {
		boolean idle = shouldThrottleIdle();
		IDLE_LIMIT_SELECTED.set(idle);
		return idle ? IDLE_FRAMERATE_LIMIT : configuredLimit;
	}

	public static void limitDisplayFps(int framerateLimit) {
		boolean idleLimit = IDLE_LIMIT_SELECTED.get();
		IDLE_LIMIT_SELECTED.set(false);
		if (!idleLimit) {
			com.mojang.blaze3d.systems.RenderSystem.limitDisplayFPS(framerateLimit);
			return;
		}

		Thread currentThread = Thread.currentThread();
		idleWaitThread = currentThread;
		try {
			long deadline = System.nanoTime() + IDLE_FRAME_NANOS;
			while (shouldThrottleIdle()) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) return;
				LockSupport.parkNanos(remaining);
				if (currentThread.isInterrupted()) return;
			}
		} finally {
			idleWaitThread = null;
		}
	}

	private static boolean shouldThrottleIdle() {
		ThreadBoundVibrisRuntimeAdapter current = runtimeAdapter;
		long idleSince = idleSinceNanos;
		return current != null && current.isIdle() && idleSince != Long.MAX_VALUE &&
			System.nanoTime() - idleSince >= IDLE_TIMEOUT_NANOS;
	}

	private static void runtimeActivityChanged(boolean active) {
		idleSinceNanos = active ? Long.MAX_VALUE : System.nanoTime();
		if (active) wakeIdleWait();
	}

	private static void wakeIdleWait() {
		Thread current = idleWaitThread;
		if (current != null) LockSupport.unpark(current);
	}

	public static String savePreset(String id) throws Exception {
		MinecraftVibrisRuntimeHost current = host;
		if (current == null) throw new IllegalStateException("Vibris runtime is not initialized");
		return current.savePreset(id);
	}

	public static void close() {
		synchronized (LOCK) {
			VibrisBootstrap current = bootstrap;
			bootstrap = null;
			frames = null;
			runtimeAdapter = null;
			idleSinceNanos = Long.MAX_VALUE;
			host = null;
			wakeIdleWait();
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
