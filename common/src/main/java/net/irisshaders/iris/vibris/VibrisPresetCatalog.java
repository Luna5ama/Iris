package net.irisshaders.iris.vibris;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vibris.api.ContextValidationResult;
import dev.vibris.api.SceneContext;
import dev.vibris.api.ScenePreset;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class VibrisPresetCatalog {
	private final Map<String, TimePreset> times;
	private final Map<String, WorldPreset> worlds;
	private final Set<String> settings;

	private VibrisPresetCatalog(
		Map<String, TimePreset> times,
		Map<String, WorldPreset> worlds,
		Set<String> settings
	) {
		this.times = Map.copyOf(times);
		this.worlds = Map.copyOf(worlds);
		this.settings = Set.copyOf(settings);
	}

	static VibrisPresetCatalog load(Path path) throws IOException {
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (integer(root, "schema_version") != 1) throw new IOException("Unsupported Vibris preset schema");
			return new VibrisPresetCatalog(parseTimes(root), parseWorlds(root), parseSettings(root));
		} catch (RuntimeException exception) {
			throw new IOException("Invalid Vibris preset file: " + path, exception);
		}
	}

	ResolvedContext resolve(SceneContext context) {
		WorldPreset world = require(worlds, context.saveId(), "save");
		if (!world.dimensions.contains(context.dimensionId())) {
			throw new IllegalArgumentException("Unknown dimension preset: " + context.dimensionId());
		}
		CameraPreset camera = require(world.cameras, context.cameraPresetId(), "camera");
		if (!camera.dimensionId.equals(context.dimensionId())) {
			throw new IllegalArgumentException("Camera preset belongs to another dimension");
		}
		TimePreset time = require(times, context.timePresetId(), "time");
		if (!time.weather.equals(context.weatherPresetId())) {
			throw new IllegalArgumentException("Weather preset does not match the selected time preset");
		}
		if (!settings.contains(context.settingsPresetId())) {
			throw new IllegalArgumentException("Unknown settings preset: " + context.settingsPresetId());
		}
		return new ResolvedContext(world.saveName, time.tick, time.weather, camera);
	}

	List<ScenePreset> presets() {
		List<ScenePreset> result = new ArrayList<>();
		var worldEntries = worlds.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
		var timeEntries = times.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
		var settingIds = settings.stream().sorted().toList();
		for (var worldEntry : worldEntries) {
			WorldPreset world = worldEntry.getValue();
			var cameras = world.cameras.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
			for (String dimension : world.dimensions.stream().sorted().toList()) {
				for (var camera : cameras) {
					if (!camera.getValue().dimensionId.equals(dimension)) continue;
					for (var time : timeEntries) {
						for (String setting : settingIds) {
							String id = String.join("/", worldEntry.getKey(), dimension, time.getKey(),
								camera.getKey(), setting);
							SceneContext context = new SceneContext(worldEntry.getKey(), dimension, time.getKey(),
								time.getValue().weather, camera.getKey(), 70.0,
								SceneContext.Resolution.unspecified(), setting);
							result.add(new ScenePreset(id, id, context));
						}
					}
				}
			}
		}
		return List.copyOf(result);
	}

	ContextValidationResult validate(SceneContext context) {
		try {
			WorldPreset world = require(worlds, context.saveId(), "save");
			if (!world.dimensions.contains(context.dimensionId())) {
				throw new IllegalArgumentException("Unknown dimension preset: " + context.dimensionId());
			}
			CameraPreset camera = require(world.cameras, context.cameraPresetId(), "camera");
			if (!camera.dimensionId.equals(context.dimensionId())) {
				throw new IllegalArgumentException("Camera preset belongs to another dimension");
			}
			TimePreset time = require(times, context.timePresetId(), "time");
			if (!context.weatherPresetId().isEmpty() && !time.weather.equals(context.weatherPresetId())) {
				throw new IllegalArgumentException("Weather preset does not match the selected time preset");
			}
			if (!context.settingsPresetId().isEmpty() && !settings.contains(context.settingsPresetId())) {
				throw new IllegalArgumentException("Unknown settings preset: " + context.settingsPresetId());
			}
			return ContextValidationResult.accepted();
		} catch (IllegalArgumentException exception) {
			return ContextValidationResult.invalid(exception.getMessage());
		}
	}

	private static Map<String, TimePreset> parseTimes(JsonObject root) {
		Map<String, TimePreset> result = new HashMap<>();
		for (JsonElement element : array(root, "time_presets")) {
			JsonObject value = element.getAsJsonObject();
			String id = string(value, "id");
			putUnique(result, id, new TimePreset(longValue(value, "tick"), string(value, "weather")));
		}
		return result;
	}

	private static Map<String, WorldPreset> parseWorlds(JsonObject root) {
		Map<String, WorldPreset> result = new HashMap<>();
		for (JsonElement element : array(root, "worlds")) {
			JsonObject value = element.getAsJsonObject();
			Set<String> dimensions = new HashSet<>();
			for (JsonElement dimension : array(value, "dimensions")) dimensions.add(dimension.getAsString());
			Map<String, CameraPreset> cameras = new HashMap<>();
			for (JsonElement cameraElement : array(value, "cameras")) {
				JsonObject camera = cameraElement.getAsJsonObject();
				JsonArray position = array(camera, "position");
				if (position.size() != 3) throw new IllegalArgumentException("Camera position must have three values");
				putUnique(cameras, string(camera, "id"), new CameraPreset(
					string(camera, "dimension_id"),
					position.get(0).getAsDouble(),
					position.get(1).getAsDouble(),
					position.get(2).getAsDouble(),
					camera.get("yaw").getAsFloat(),
					camera.get("pitch").getAsFloat()));
			}
			putUnique(result, string(value, "id"), new WorldPreset(
				string(value, "save_name"), Set.copyOf(dimensions), Map.copyOf(cameras)));
		}
		return result;
	}

	private static Set<String> parseSettings(JsonObject root) {
		Set<String> result = new HashSet<>();
		for (JsonElement element : array(root, "settings_presets")) {
			if (element.isJsonPrimitive()) result.add(element.getAsString());
			else result.add(string(element.getAsJsonObject(), "id"));
		}
		return result;
	}

	private static JsonArray array(JsonObject object, String name) {
		JsonElement value = object.get(name);
		if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Missing array: " + name);
		return value.getAsJsonArray();
	}

	private static String string(JsonObject object, String name) {
		String value = object.get(name).getAsString();
		if (value.isBlank()) throw new IllegalArgumentException("Blank preset field: " + name);
		return value;
	}

	private static int integer(JsonObject object, String name) {
		return object.get(name).getAsInt();
	}

	private static long longValue(JsonObject object, String name) {
		return object.get(name).getAsLong();
	}

	private static <T> T require(Map<String, T> values, String id, String kind) {
		T value = values.get(id);
		if (value == null) throw new IllegalArgumentException("Unknown " + kind + " preset: " + id);
		return value;
	}

	private static <T> void putUnique(Map<String, T> values, String id, T value) {
		if (values.putIfAbsent(id, value) != null) throw new IllegalArgumentException("Duplicate preset id: " + id);
	}

	record ResolvedContext(String saveName, long tick, String weather, CameraPreset camera) {
	}

	record CameraPreset(String dimensionId, double x, double y, double z, float yaw, float pitch) {
	}

	private record TimePreset(long tick, String weather) {
	}

	private record WorldPreset(String saveName, Set<String> dimensions, Map<String, CameraPreset> cameras) {
	}
}
