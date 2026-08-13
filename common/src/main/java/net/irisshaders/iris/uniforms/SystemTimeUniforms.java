package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.client.DeltaTracker;

import java.util.OptionalLong;
import java.util.function.IntSupplier;

/**
 * Implements uniforms relating the system time (as opposed to the world time)
 *
 * @see <a href="https://github.com/IrisShaders/ShaderDoc/blob/master/uniforms.md#system-time">Uniforms: System time</a>
 */
public final class SystemTimeUniforms {
	private static final float DETERMINISTIC_FRAME_TIME_SECONDS = 1.0F / 60.0F;

	public static final Timer TIMER = new Timer();
	public static final FrameCounter COUNTER = new FrameCounter();

	private static DeterministicTimeScope deterministicTimeScope;

	private SystemTimeUniforms() {
	}

	/**
	 * Makes system time uniforms available to the given program
	 *
	 * @param uniforms the program to make the uniforms available to
	 */
	public static void addSystemTimeUniforms(UniformHolder uniforms) {
		uniforms
			.uniform1i(UniformUpdateFrequency.PER_FRAME, "frameCounter", COUNTER)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "frameTime", TIMER::getLastFrameTime)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "frameTimeCounter", TIMER::getFrameTimeCounter);
	}

	/**
	 * Advances all shader-visible system time values for one rendered frame.
	 *
	 * @param realNanos the real monotonic time at the start of the frame
	 */
	public static synchronized void beginFrame(long realNanos) {
		COUNTER.beginFrame();
		if (deterministicTimeScope == null) {
			TIMER.beginRealFrame(realNanos);
		} else {
			TIMER.beginDeterministicFrame();
		}
	}

	/**
	 * Starts a non-nestable deterministic shader-time scope at a fresh temporal origin.
	 */
	public static synchronized DeterministicTimeScope beginDeterministicTime() {
		if (deterministicTimeScope != null) {
			throw new IllegalStateException("Deterministic shader time is already active");
		}

		DeterministicTimeScope scope = new DeterministicTimeScope();
		deterministicTimeScope = scope;
		COUNTER.reset();
		TIMER.reset();
		return scope;
	}

	public static synchronized boolean isDeterministicTimeActive() {
		return deterministicTimeScope != null;
	}

	/**
	 * Resolves a Minecraft render partial tick against the active deterministic capture phase.
	 *
	 * <p>Minecraft uses a partial tick of {@code 1.0} while its game clock is frozen. Using the
	 * same value here keeps shader-visible celestial and interpolation state fixed throughout a
	 * deterministic capture without changing ordinary rendering.</p>
	 */
	public static synchronized float resolveTickDelta(float realTickDelta) {
		return deterministicTimeScope == null ? realTickDelta : 1.0F;
	}

	/**
	 * Uses Minecraft's fixed full-tick render state while deterministic capture is active.
	 */
	public static synchronized DeltaTracker resolveDeltaTracker(DeltaTracker realDeltaTracker) {
		return deterministicTimeScope == null ? realDeltaTracker : DeltaTracker.ONE;
	}

	private static synchronized void endDeterministicTime(DeterministicTimeScope scope) {
		if (scope.closed) {
			return;
		}
		if (deterministicTimeScope != scope) {
			throw new IllegalStateException("Deterministic shader time scope is not active");
		}

		deterministicTimeScope = null;
		scope.closed = true;
		TIMER.reset();
	}

	public static final class DeterministicTimeScope implements AutoCloseable {
		private boolean closed;

		private DeterministicTimeScope() {
		}

		@Override
		public void close() {
			endDeterministicTime(this);
		}
	}

	/**
	 * A simple frame counter. On each frame, it is incremented by 1, and it wraps around every 720720 frames. It starts
	 * at zero and goes from there.
	 */
	public static class FrameCounter implements IntSupplier {
		private int count;

		private FrameCounter() {
			this.count = 0;
		}

		@Override
		public int getAsInt() {
			return count;
		}

		private void beginFrame() {
			count = (count + 1) % 720720;
		}

		public void reset() {
			count = 0;
		}
	}

	/**
	 * Keeps track of the time that the last frame took to render as well as the number of milliseconds since the start
	 * of the first frame to the start of the current frame. Updated at the start of each frame.
	 */
	public static final class Timer {
		private float frameTimeCounter;
		private float lastFrameTime;

		// Disabling this because OptionalLong provides a nice wrapper around (boolean valid, long value)
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
		private OptionalLong lastStartTime;

		public Timer() {
			reset();
		}

		private void beginRealFrame(long frameStartTime) {
			// Track how much time passed since the last time we began rendering a frame.
			// If this is the first frame, then use a value of 0.
			long diffNs = frameStartTime - lastStartTime.orElse(frameStartTime);
			// Convert to milliseconds
			long diffMs = (diffNs / 1000) / 1000;

			// Convert to seconds with a resolution of 1 millisecond, and store as the time taken for the last frame to complete.
			advance(diffMs / 1000.0F);

			// Finally, update the "last start time" value.
			lastStartTime = OptionalLong.of(frameStartTime);
		}

		private void beginDeterministicFrame() {
			advance(DETERMINISTIC_FRAME_TIME_SECONDS);
		}

		private void advance(float elapsedSeconds) {
			lastFrameTime = elapsedSeconds;
			frameTimeCounter += lastFrameTime;

			// Prevent the frameTimeCounter from getting too large, since that causes issues with some shaderpacks
			// This means that it should reset every hour.
			if (frameTimeCounter >= 3600.0F) {
				frameTimeCounter = 0.0F;
			}
		}

		public float getFrameTimeCounter() {
			return frameTimeCounter;
		}

		public float getLastFrameTime() {
			return lastFrameTime;
		}

		public void reset() {
			frameTimeCounter = 0.0F;
			lastFrameTime = 0.0F;
			lastStartTime = OptionalLong.empty();
		}
	}
}
