package net.irisshaders.iris.vibris;

import dev.vibris.api.CancellationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public final class IrisVibrisFrameClock implements AutoCloseable {
	private final List<Waiter> waiters = new ArrayList<>();
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

	public synchronized void renderedFrame() {
		if (closed) return;
		renderedFrames++;
		waiters.removeIf(waiter -> complete(waiter));
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

	private static CompletionStage<Long> cancelled() {
		return CompletableFuture.failedFuture(new CancellationException("Vibris frame wait cancelled"));
	}

	private record Waiter(long target, CancellationToken cancellation, CompletableFuture<Long> result) {
	}
}
