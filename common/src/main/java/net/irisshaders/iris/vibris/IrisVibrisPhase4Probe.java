package net.irisshaders.iris.vibris;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vibris.api.CapturePlan;
import dev.vibris.api.SceneContext;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

public final class IrisVibrisPhase4Probe {
	private static final Gson GSON = new Gson();
	private static final Object LOCK = new Object();
	private static volatile State state;

	private IrisVibrisPhase4Probe() {
	}

	public static void initialize(Path actualGameDirectory) throws IOException {
		String runId = System.getProperty("vibris.phase4.runId");
		if (runId == null) return;
		UUID parsed = UUID.fromString(runId);
		if (!parsed.toString().equals(runId)) throw new IOException("Phase 4 run ID is not canonical");
		Path gameDirectory = actualGameDirectory.toAbsolutePath().normalize();
		Path expected = propertyPath("vibris.phase4.gameDir");
		if (!gameDirectory.equals(expected)) throw new IOException("Phase 4 game directory does not match");
		Path eventFile = ownedFile("vibris.phase4.eventFile", gameDirectory);
		Path receiptFile = ownedFile("vibris.phase4.receiptFile", gameDirectory);
		Path commandFile = ownedFile("vibris.phase4.commandFile", gameDirectory);
		Files.createDirectories(gameDirectory);
		Files.createDirectories(eventFile.getParent());
		State candidate = new State(runId, gameDirectory, eventFile, receiptFile, commandFile);
		writeReceipt(candidate);
		state = candidate;
	}

	public static boolean enabled() {
		return state != null;
	}

	public static void serverReady(int port, Path pendingShadersRoot) {
		JsonObject event = event("server_ready");
		if (event == null) return;
		event.addProperty("address", "127.0.0.1:" + port);
		event.addProperty("pending_shaders_root", pendingShadersRoot.toAbsolutePath().normalize().toString());
		append(event);
	}

	public static void contextApplied(SceneContext context, Minecraft minecraft) {
		JsonObject event = event("context_applied");
		if (event == null || minecraft.level == null || minecraft.player == null) return;
		JsonObject actual = contextJson(context);
		actual.addProperty("day_time", minecraft.level.getDayTime());
		actual.addProperty("rain_level", minecraft.level.getRainLevel(1.0f));
		actual.addProperty("thunder_level", minecraft.level.getThunderLevel(1.0f));
		JsonObject camera = new JsonObject();
		camera.addProperty("x", minecraft.player.getX());
		camera.addProperty("y", minecraft.player.getY());
		camera.addProperty("z", minecraft.player.getZ());
		camera.addProperty("yaw", minecraft.player.getYRot());
		camera.addProperty("pitch", minecraft.player.getXRot());
		actual.add("camera", camera);
		event.add("context", actual);
		append(event);
	}

	public static void shaderReloaded(boolean successful, Path link, Object pipeline, long frameId) {
		State current = state;
		if (current == null) return;
		String source = sourceUuid(link);
		if (!successful) {
			current.failedSource = source;
			current.failedLink = link;
			return;
		}
		JsonObject event;
		if (current.failedSource != null && current.activeSource != null && current.activeSource.equals(source)) {
			event = event("source_rollback");
			event.addProperty("failed_source_uuid", current.failedSource);
			event.addProperty("restored_source_uuid", source);
			current.failedSource = null;
			current.failedLink = null;
		} else {
			event = event("source_active");
			event.addProperty("source_uuid", source);
			current.activeSource = source;
		}
		event.addProperty("link_target", linkTarget(link));
		event.addProperty("pipeline_id", pipelineId(pipeline));
		event.addProperty("frame_id", frameId);
		append(event);
	}

	public static void frameTail(long frameId) {
		State current = state;
		JsonObject event = event("frame_tail");
		if (current == null || event == null) return;
		appendObservedRollback(current, frameId);
		Minecraft minecraft = Minecraft.getInstance();
		event.addProperty("frame_id", frameId);
		event.addProperty("world_ready", minecraft.level != null && minecraft.player != null &&
			minecraft.getSingleplayerServer() != null);
		event.addProperty("source_uuid", current.activeSource == null ? "" : current.activeSource);
		event.addProperty("pipeline_id", current.pipelineId);
		append(event);
	}

	private static void appendObservedRollback(State current, long frameId) {
		Path link = current.failedLink;
		if (link == null || current.activeSource == null || !current.activeSource.equals(sourceUuid(link))) return;
		JsonObject event = event("source_rollback");
		if (event == null) return;
		event.addProperty("failed_source_uuid", current.failedSource);
		event.addProperty("restored_source_uuid", current.activeSource);
		event.addProperty("link_target", linkTarget(link));
		event.addProperty("pipeline_id", current.pipelineId);
		event.addProperty("frame_id", frameId);
		current.failedSource = null;
		current.failedLink = null;
		append(event);
	}

	public static void clientFrameTail() {
		pollCommand();
	}

	public static void frameWaitComplete(long startFrame, long endFrame) {
		State current = state;
		JsonObject event = event("frame_wait_complete");
		if (current == null || event == null) return;
		event.addProperty("source_uuid", current.activeSource == null ? "" : current.activeSource);
		event.addProperty("pipeline_id", current.pipelineId);
		event.addProperty("start_frame", startFrame);
		event.addProperty("end_frame", endFrame);
		event.addProperty("count", endFrame - startFrame);
		append(event);
	}

	public static void temporalReset() {
		State current = state;
		JsonObject event = event("temporal_reset");
		if (current == null || event == null) return;
		event.addProperty("source_uuid", current.activeSource == null ? "" : current.activeSource);
		append(event);
	}

	public static void captureComplete(CapturePlan plan, long frameId) {
		State current = state;
		JsonObject event = event("capture_complete");
		if (current == null || event == null) return;
		event.addProperty("source_uuid", current.activeSource == null ? "" : current.activeSource);
		event.addProperty("frame_id", frameId);
		JsonArray targets = new JsonArray();
		plan.targets().forEach(target -> targets.add(target.artifactName()));
		event.add("targets", targets);
		append(event);
	}

	public static void shutdownComplete() {
		JsonObject event = event("shutdown_complete");
		if (event != null) append(event);
		state = null;
	}

	private static JsonObject contextJson(SceneContext context) {
		JsonObject result = new JsonObject();
		result.addProperty("save_id", context.saveId());
		result.addProperty("dimension_id", context.dimensionId());
		result.addProperty("time_preset_id", context.timePresetId());
		result.addProperty("weather_preset_id", context.weatherPresetId());
		result.addProperty("camera_preset_id", context.cameraPresetId());
		result.addProperty("settings_preset_id", context.settingsPresetId());
		result.addProperty("fov", context.fov());
		JsonObject resolution = new JsonObject();
		resolution.addProperty("width", context.resolution().width());
		resolution.addProperty("height", context.resolution().height());
		result.add("resolution", resolution);
		return result;
	}

	private static void pollCommand() {
		State current = state;
		if (current == null || current.stopRequested || !Files.isRegularFile(current.commandFile)) return;
		try {
			JsonObject command = JsonParser.parseString(Files.readString(current.commandFile)).getAsJsonObject();
			if (!current.runId.equals(command.get("run_id").getAsString())) return;
			if (!"stop".equals(command.get("command").getAsString())) return;
			current.stopRequested = true;
			Files.deleteIfExists(current.commandFile);
			Minecraft.getInstance().stop();
		} catch (Exception exception) {
			Iris.logger.error("Failed to process the Phase 4 probe command.", exception);
		}
	}

	private static void writeReceipt(State current) throws IOException {
		JsonObject receipt = new JsonObject();
		receipt.addProperty("pid", ProcessHandle.current().pid());
		receipt.addProperty("started_at_utc", Instant.now().toString());
		receipt.addProperty("run_id", current.runId);
		receipt.addProperty("game_dir", current.gameDirectory.toString());
		Files.writeString(current.receiptFile, GSON.toJson(receipt), StandardOpenOption.CREATE_NEW);
	}

	private static JsonObject event(String type) {
		State current = state;
		if (current == null) return null;
		JsonObject event = new JsonObject();
		event.addProperty("run_id", current.runId);
		event.addProperty("type", type);
		return event;
	}

	private static void append(JsonObject event) {
		State current = state;
		if (current == null) return;
		synchronized (LOCK) {
			try {
				Files.writeString(current.eventFile, GSON.toJson(event) + System.lineSeparator(),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException exception) {
				Iris.logger.error("Failed to write a Phase 4 probe event.", exception);
			}
		}
	}

	private static Path ownedFile(String property, Path gameDirectory) throws IOException {
		Path path = propertyPath(property);
		if (!path.startsWith(gameDirectory) || path.equals(gameDirectory)) {
			throw new IOException(property + " must be a file below the Phase 4 game directory");
		}
		return path;
	}

	private static Path propertyPath(String name) throws IOException {
		String value = System.getProperty(name);
		if (value == null || value.isBlank()) throw new IOException("Missing Phase 4 property: " + name);
		return Path.of(value).toAbsolutePath().normalize();
	}

	private static String sourceUuid(Path link) {
		Path target = Path.of(linkTarget(link));
		Path name = target.getFileName();
		return name == null ? "" : name.toString();
	}

	private static String linkTarget(Path link) {
		try {
			Path target = Files.readSymbolicLink(link);
			return (target.isAbsolute() ? target : link.getParent().resolve(target)).normalize().toString();
		} catch (IOException exception) {
			return "";
		}
	}

	private static String pipelineId(Object pipeline) {
		String id = pipeline == null ? "" : Integer.toHexString(System.identityHashCode(pipeline));
		State current = state;
		if (current != null) current.pipelineId = id;
		return id;
	}

	private static final class State {
		final String runId;
		final Path gameDirectory;
		final Path eventFile;
		final Path receiptFile;
		final Path commandFile;
		volatile String activeSource;
		volatile String failedSource;
		volatile Path failedLink;
		volatile String pipelineId = "";
		volatile boolean stopRequested;

		State(String runId, Path gameDirectory, Path eventFile, Path receiptFile, Path commandFile) {
			this.runId = runId;
			this.gameDirectory = gameDirectory;
			this.eventFile = eventFile;
			this.receiptFile = receiptFile;
			this.commandFile = commandFile;
		}
	}
}
