package net.irisshaders.iris.vibris;

import dev.vibris.api.CancellationToken;
import dev.vibris.api.ContextApplyResult;
import dev.vibris.api.SceneContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

final class MinecraftContextController {
	private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
	private final Minecraft minecraft;
	private final VibrisPresetCatalog presets;

	MinecraftContextController(Minecraft minecraft, VibrisPresetCatalog presets) {
		this.minecraft = minecraft;
		this.presets = presets;
	}

	CompletionStage<ContextApplyResult> apply(SceneContext context, CancellationToken cancellation) {
		cancellation.throwIfCancellationRequested();
		VibrisPresetCatalog.ResolvedContext resolved;
		try {
			resolved = presets.resolve(context);
		} catch (IllegalArgumentException exception) {
			return CompletableFuture.completedFuture(ContextApplyResult.failure(context, exception.getMessage()));
		}
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) return applyLoadedSave(server, context, resolved, cancellation);
		if (!minecraft.getLevelSource().levelExists(resolved.saveName())) {
			return failed(context, "The configured singleplayer save does not exist.");
		}

		CompletableFuture<ContextApplyResult> result = new CompletableFuture<>();
		try {
			minecraft.createWorldOpenFlows().openWorld(resolved.saveName(), () -> result.complete(
				ContextApplyResult.failure(context, "Minecraft rejected the configured singleplayer save.")));
			pollForSave(context, resolved, cancellation, System.nanoTime() + TIMEOUT_NANOS, result);
		} catch (Exception exception) {
			result.complete(ContextApplyResult.failure(context, failureMessage(exception)));
		}
		return result;
	}

	private CompletionStage<ContextApplyResult> applyLoadedSave(
		IntegratedServer server,
		SceneContext context,
		VibrisPresetCatalog.ResolvedContext resolved,
		CancellationToken cancellation
	) {
		String runningSave = runningSave(server);
		if (!runningSave.equals(resolved.saveName())) {
			return failed(context, "Another singleplayer save is running: expected " + resolved.saveName() +
				", got " + runningSave + ".");
		}
		CompletableFuture<ContextApplyResult> result = new CompletableFuture<>();
		applyOnServer(server, context, resolved, cancellation).whenComplete((ignored, failure) ->
			minecraft.execute(() -> {
				if (failure != null) {
					result.complete(ContextApplyResult.failure(context, failureMessage(failure)));
					return;
				}
				applyClientOptions(context);
				pollForContext(context, resolved, cancellation, System.nanoTime() + TIMEOUT_NANOS, result);
			}));
		return result;
	}

	private CompletableFuture<Void> applyOnServer(
		IntegratedServer server,
		SceneContext context,
		VibrisPresetCatalog.ResolvedContext resolved,
		CancellationToken cancellation
	) {
		CompletableFuture<Void> completion = new CompletableFuture<>();
		server.execute(() -> {
			try {
				cancellation.throwIfCancellationRequested();
				ResourceKey<Level> dimension = ResourceKey.create(
					Registries.DIMENSION, Identifier.parse(context.dimensionId()));
				ServerLevel level = server.getLevel(dimension);
				ServerPlayer player = server.getPlayerList().getPlayer(minecraft.player.getUUID());
				if (level == null || player == null) throw new IllegalStateException("Preset dimension is unavailable");
				applyWeather(level, resolved.tick(), resolved.weather());
				var camera = resolved.camera();
				if (!player.teleportTo(
					level, camera.x(), camera.y(), camera.z(), Set.of(), camera.yaw(), camera.pitch(), true)) {
					throw new IllegalStateException("Player teleport was rejected");
				}
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.setDeltaMovement(0.0, 0.0, 0.0);
				completion.complete(null);
			} catch (Throwable throwable) {
				completion.completeExceptionally(throwable);
			}
		});
		return completion;
	}

	private void pollForSave(
		SceneContext context,
		VibrisPresetCatalog.ResolvedContext resolved,
		CancellationToken cancellation,
		long deadline,
		CompletableFuture<ContextApplyResult> result
	) {
		pollLater(result, () -> {
			if (cancelled(cancellation, result)) return;
			IntegratedServer server = minecraft.getSingleplayerServer();
			if (server != null && minecraft.player != null && runningSave(server).equals(resolved.saveName())) {
				applyLoadedSave(server, context, resolved, cancellation)
					.whenComplete((value, failure) -> complete(result, value, failure));
			} else if (System.nanoTime() >= deadline) {
				result.complete(ContextApplyResult.failure(context, "Timed out loading the configured save."));
			} else {
				pollForSave(context, resolved, cancellation, deadline, result);
			}
		});
	}

	private void pollForContext(
		SceneContext context,
		VibrisPresetCatalog.ResolvedContext resolved,
		CancellationToken cancellation,
		long deadline,
		CompletableFuture<ContextApplyResult> result
	) {
		pollLater(result, () -> {
			if (cancelled(cancellation, result)) return;
			String mismatch = clientMismatch(context, resolved);
			if (mismatch == null) result.complete(ContextApplyResult.success(context));
			else if (System.nanoTime() >= deadline) result.complete(
				ContextApplyResult.failure(context, "Timed out synchronizing the client context: " + mismatch));
			else pollForContext(context, resolved, cancellation, deadline, result);
		});
	}

	private String clientMismatch(SceneContext context, VibrisPresetCatalog.ResolvedContext resolved) {
		if (minecraft.level == null || minecraft.player == null) return "client world is not ready";
		String dimension = minecraft.level.dimension().identifier().toString();
		if (!dimension.equals(context.dimensionId())) return "dimension is " + dimension;
		long dayTime = minecraft.level.getDayTime();
		if (dayTime != resolved.tick()) return "day time is " + dayTime;
		float rain = minecraft.level.getRainLevel(1.0f);
		float thunder = minecraft.level.getThunderLevel(1.0f);
		float expectedRain = resolved.weather().equals("clear") ? 0.0f : 1.0f;
		float expectedThunder = resolved.weather().equals("thunder") ? 1.0f : 0.0f;
		if (rain != expectedRain) return "rain level is " + rain;
		if (thunder != expectedThunder) return "thunder level is " + thunder;
		var camera = resolved.camera();
		if (minecraft.player.position().distanceToSqr(camera.x(), camera.y(), camera.z()) > 0.01) {
			return "player position is " + minecraft.player.position();
		}
		if (angleDifference(minecraft.player.getYRot(), camera.yaw()) > 0.1f) {
			return "player yaw is " + minecraft.player.getYRot();
		}
		if (Math.abs(minecraft.player.getXRot() - camera.pitch()) > 0.1f) {
			return "player pitch is " + minecraft.player.getXRot();
		}
		int fov = minecraft.options.fov().get();
		if (fov != (int) Math.round(context.fov())) return "field of view is " + fov;
		if (context.resolution().isSpecified() &&
			(minecraft.getWindow().getWidth() != context.resolution().width() ||
				minecraft.getWindow().getHeight() != context.resolution().height())) {
			return "window size is " + minecraft.getWindow().getWidth() + "x" + minecraft.getWindow().getHeight();
		}
		return null;
	}

	private void applyClientOptions(SceneContext context) {
		minecraft.options.fov().set((int) Math.round(context.fov()));
		if (context.resolution().isSpecified()) {
			minecraft.getWindow().setWindowed(context.resolution().width(), context.resolution().height());
			minecraft.resizeDisplay();
		}
	}

	private void pollLater(CompletableFuture<?> result, Runnable check) {
		if (!result.isDone()) {
			CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(() -> minecraft.execute(check));
		}
	}

	private static void applyWeather(ServerLevel level, long tick, String weather) {
		level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
		level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, level.getServer());
		level.setDayTime(tick);
		boolean rain = !weather.equals("clear");
		boolean thunder = weather.equals("thunder");
		if (!weather.equals("clear") && !weather.equals("rain") && !thunder) {
			throw new IllegalArgumentException("Unknown weather preset: " + weather);
		}
		level.setWeatherParameters(rain ? 0 : 6000, rain ? 6000 : 0, rain, thunder);
		level.setRainLevel(rain ? 1.0f : 0.0f);
		level.setThunderLevel(thunder ? 1.0f : 0.0f);
	}

	private static boolean cancelled(
		CancellationToken cancellation,
		CompletableFuture<ContextApplyResult> result
	) {
		if (!cancellation.isCancellationRequested()) return false;
		result.cancel(false);
		return true;
	}

	private static String runningSave(IntegratedServer server) {
		Path save = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
		return save == null ? "" : save.toString();
	}

	private static float angleDifference(float first, float second) {
		float difference = (first - second) % 360.0f;
		if (difference > 180.0f) difference -= 360.0f;
		if (difference < -180.0f) difference += 360.0f;
		return Math.abs(difference);
	}

	private static CompletionStage<ContextApplyResult> failed(SceneContext context, String message) {
		return CompletableFuture.completedFuture(ContextApplyResult.failure(context, message));
	}

	private static String failureMessage(Throwable failure) {
		Throwable cause = failure.getCause() == null ? failure : failure.getCause();
		return "Failed to apply the Minecraft context: " + cause.getMessage();
	}

	private static <T> void complete(CompletableFuture<T> result, T value, Throwable failure) {
		if (failure == null) result.complete(value);
		else result.completeExceptionally(failure);
	}
}
