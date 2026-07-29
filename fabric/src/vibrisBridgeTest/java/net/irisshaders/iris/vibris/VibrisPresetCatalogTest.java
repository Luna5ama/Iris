package net.irisshaders.iris.vibris;

import dev.vibris.api.SceneContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VibrisPresetCatalogTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void resolvesOnlyKnownContextIds() throws IOException {
		VibrisPresetCatalog catalog = VibrisPresetCatalog.load(writePresets());
		SceneContext context = context("sunset", "clear", "village-rooftop");

		var resolved = catalog.resolve(context);
		assertEquals("shader-test-world", resolved.saveName());
		assertEquals(12000, resolved.tick());
		assertEquals(124.5, resolved.camera().x());
		assertEquals(137.0f, resolved.camera().yaw());
	}

	@Test
	void rejectsUnknownAndMismatchedPresetIds() throws IOException {
		VibrisPresetCatalog catalog = VibrisPresetCatalog.load(writePresets());

		assertThrows(IllegalArgumentException.class,
			() -> catalog.resolve(context("unknown", "clear", "village-rooftop")));
		assertThrows(IllegalArgumentException.class,
			() -> catalog.resolve(context("sunset", "rain", "village-rooftop")));
		assertThrows(IllegalArgumentException.class,
			() -> catalog.resolve(context("sunset", "clear", "unknown")));
	}

	private SceneContext context(String time, String weather, String camera) {
		return new SceneContext(
			"shader-test-world",
			"minecraft:overworld",
			time,
			weather,
			camera,
			70.0,
			new SceneContext.Resolution(1280, 720),
			"phase4");
	}

	private Path writePresets() throws IOException {
		Path path = temporaryDirectory.resolve("presets.json");
		Files.writeString(path, """
			{
			  "schema_version": 1,
			  "time_presets": [
			    {"id":"sunset","tick":12000,"weather":"clear"}
			  ],
			  "settings_presets": [{"id":"phase4"}],
			  "worlds": [{
			    "id":"shader-test-world",
			    "save_name":"shader-test-world",
			    "dimensions":["minecraft:overworld"],
			    "cameras":[{
			      "id":"village-rooftop",
			      "dimension_id":"minecraft:overworld",
			      "position":[124.5,82.0,-31.5],
			      "yaw":137.0,
			      "pitch":-8.0,
			      "default_fov":70.0
			    }]
			  }]
			}
			""");
		return path;
	}
}
