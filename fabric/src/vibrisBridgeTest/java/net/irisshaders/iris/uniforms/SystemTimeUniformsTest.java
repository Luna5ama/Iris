package net.irisshaders.iris.uniforms;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemTimeUniformsTest {
	private static final float FIXED_FRAME_TIME = 1.0F / 60.0F;

	private SystemTimeUniforms.DeterministicTimeScope activeScope;

	@AfterEach
	void resetGlobals() {
		if (activeScope != null) {
			activeScope.close();
			activeScope = null;
		}
		SystemTimeUniforms.COUNTER.reset();
		SystemTimeUniforms.TIMER.reset();
	}

	@Test
	void deterministicFramesIgnoreRealNanosecondSequence() {
		List<FrameState> first = deterministicSequence(
			100L,
			new long[] {10L, 40_000_000L, 9_000_000_000L},
			new long[] {100L, 101L, 102L});
		List<FrameState> second = deterministicSequence(
			100L,
			new long[] {7_000_000_000L, 3L, Long.MAX_VALUE},
			new long[] {100L, 101L, 102L});

		assertEquals(first, second);
		assertEquals(new FrameState(0, FIXED_FRAME_TIME, FIXED_FRAME_TIME), first.getFirst());
		assertEquals(new FrameState(2, FIXED_FRAME_TIME, FIXED_FRAME_TIME * 3.0F), first.getLast());
	}

	@Test
	void deterministicTimerUsesRenderedFrameOriginWhenRenderRepeats() {
		activeScope = SystemTimeUniforms.beginDeterministicTime(100L);

		SystemTimeUniforms.beginFrame(1L, 100L);
		assertEquals(new FrameState(0, FIXED_FRAME_TIME, FIXED_FRAME_TIME), currentState());

		SystemTimeUniforms.beginFrame(2L, 100L);
		assertEquals(new FrameState(0, FIXED_FRAME_TIME, FIXED_FRAME_TIME), currentState());

		SystemTimeUniforms.beginFrame(3L, 101L);
		assertEquals(new FrameState(1, FIXED_FRAME_TIME, FIXED_FRAME_TIME * 2.0F), currentState());
	}

	@Test
	void deterministicScopePinsPartialTickAndRestoresPassThrough() {
		assertFalse(SystemTimeUniforms.isDeterministicTimeActive());
		assertEquals(0.25F, SystemTimeUniforms.resolveTickDelta(0.25F));

		activeScope = SystemTimeUniforms.beginDeterministicTime(0L);
		assertTrue(SystemTimeUniforms.isDeterministicTimeActive());
		assertEquals(1.0F, SystemTimeUniforms.resolveTickDelta(0.0F));
		assertEquals(1.0F, SystemTimeUniforms.resolveTickDelta(0.75F));

		activeScope.close();
		activeScope = null;
		assertFalse(SystemTimeUniforms.isDeterministicTimeActive());
		assertEquals(0.75F, SystemTimeUniforms.resolveTickDelta(0.75F));
	}

	@Test
	void scopeIsNonNestableAndCloseRestoresRealTimeWithoutResettingFrameCounter() {
		activeScope = SystemTimeUniforms.beginDeterministicTime(0L);
		SystemTimeUniforms.beginFrame(123L, 0L);
		assertThrows(IllegalStateException.class, () -> SystemTimeUniforms.beginDeterministicTime(0L));

		SystemTimeUniforms.DeterministicTimeScope closedScope = activeScope;
		activeScope.close();
		activeScope = null;
		closedScope.close();

		SystemTimeUniforms.beginFrame(5_000_000_000L, 0L);
		assertEquals(new FrameState(1, 0.0F, 0.0F), currentState());
		SystemTimeUniforms.beginFrame(5_025_000_000L, 0L);
		assertEquals(new FrameState(2, 0.025F, 0.025F), currentState());
	}

	private List<FrameState> deterministicSequence(long originFrame, long[] realNanos, long[] renderedFrames) {
		assertEquals(realNanos.length, renderedFrames.length);
		activeScope = SystemTimeUniforms.beginDeterministicTime(originFrame);
		List<FrameState> states = new ArrayList<>();
		for (int i = 0; i < realNanos.length; i++) {
			SystemTimeUniforms.beginFrame(realNanos[i], renderedFrames[i]);
			states.add(currentState());
		}
		activeScope.close();
		activeScope = null;
		return states;
	}

	private FrameState currentState() {
		return new FrameState(
			SystemTimeUniforms.COUNTER.getAsInt(),
			SystemTimeUniforms.TIMER.getLastFrameTime(),
			SystemTimeUniforms.TIMER.getFrameTimeCounter()
		);
	}

	private record FrameState(int frameCounter, float frameTime, float frameTimeCounter) {
	}
}
