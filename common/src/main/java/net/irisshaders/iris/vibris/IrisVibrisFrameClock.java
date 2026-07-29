package net.irisshaders.iris.vibris;

import dev.vibris.api.CancellationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

public final class IrisVibrisFrameClock implements AutoCloseable {
	private final List<Waiter> waiters = new ArrayList<>();
	private final List<CaptureTask<?>> captures = new ArrayList<>();
	private long renderedFrames;
	private boolean closed;

	public synchronized CompletionStage<Long> waitRenderedFrames(int count, CancellationToken cancellation) {
		if (count < 0) throw new IllegalArgumentException("Frame count must not be negative");
		if (closed || cancellation.isCancellationRequested()) return cancelled();
		if (count == 0) return CompletableFuture.completedFuture(renderedFrames);

		CompletableFuture<Long> result = new CompletableFuture<>();
		Waiter waiter = new Waiter(renderedFrames + count, cancellation, result);
		waiters.add(waiter);
		pollCancellation(waiter);
		return result;
	}

	public <T> CompletionStage<T> captureAtNextFrame(
		CancellationToken cancellation,
		LongFunction<T> action
	) {
		synchronized (this) {
			if (closed || cancellation.isCancellationRequested()) return cancelled();
			CompletableFuture<T> result = new CompletableFuture<>();
			CaptureTask<T> capture = new CaptureTask<>(cancellation, action, result);
			captures.add(capture);
			pollCaptureCancellation(capture);
			return result;
		}
	}

	public void renderedFrame() {
		List<CaptureTask<?>> current;
		long frameId;
		synchronized (this) {
			if (closed) return;
			renderedFrames++;
			frameId = renderedFrames;
			waiters.removeIf(waiter -> complete(waiter));
			current = List.copyOf(captures);
			captures.clear();
		}
		current.forEach(capture -> capture.run(frameId));
	}

	public synchronized long currentFrame() {
		return renderedFrames;
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;
		waiters.forEach(waiter -> waiter.result.completeExceptionally(
			new CancellationException("Vibris frame clock closed")));
		waiters.clear();
		captures.forEach(capture -> capture.result.completeExceptionally(
			new CancellationException("Vibris frame clock closed")));
		captures.clear();
	}

	private boolean complete(Waiter waiter) {
		if (waiter.cancellation.isCancellationRequested()) {
			waiter.result.completeExceptionally(new CancellationException("Vibris frame wait cancelled"));
			return true;
		}
		if (renderedFrames < waiter.target) return false;
		waiter.result.complete(renderedFrames);
		return true;
	}

	private void pollCancellation(Waiter waiter) {
		CompletableFuture.delayedExecutor(10, TimeUnit.MILLISECONDS).execute(() -> {
			synchronized (this) {
				if (waiter.result.isDone() || closed || !waiters.contains(waiter)) return;
				if (waiter.cancellation.isCancellationRequested()) {
					waiters.remove(waiter);
					waiter.result.completeExceptionally(
						new CancellationException("Vibris frame wait cancelled"));
					return;
				}
			}
			pollCancellation(waiter);
		});
	}

	private void pollCaptureCancellation(CaptureTask<?> capture) {
		CompletableFuture.delayedExecutor(10, TimeUnit.MILLISECONDS).execute(() -> {
			synchronized (this) {
				if (capture.result.isDone() || closed || !captures.contains(capture)) return;
				if (capture.cancellation.isCancellationRequested()) {
					captures.remove(capture);
					capture.result.completeExceptionally(
						new CancellationException("Vibris capture cancelled"));
					return;
				}
			}
			pollCaptureCancellation(capture);
		});
	}

	private static <T> CompletionStage<T> cancelled() {
		return CompletableFuture.failedFuture(new CancellationException("Vibris frame wait cancelled"));
	}

	private record Waiter(long target, CancellationToken cancellation, CompletableFuture<Long> result) {
	}

	private record CaptureTask<T>(
		CancellationToken cancellation,
		LongFunction<T> action,
		CompletableFuture<T> result
	) {
		void run(long frameId) {
			try {
				cancellation.throwIfCancellationRequested();
				result.complete(action.apply(frameId));
			} catch (Throwable throwable) {
				result.completeExceptionally(throwable);
			}
		}
	}
}
