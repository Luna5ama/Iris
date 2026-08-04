package net.irisshaders.iris.pipeline.transform;

import net.irisshaders.iris.shaderpack.include.ShaderSourceMap;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransformPatcherSourceMapTest {
	@Test
	void sourceMapMetadataKeepsNullShaderStagesNull() {
		Map<PatchShaderType, String> transformed = new EnumMap<>(PatchShaderType.class);
		transformed.put(PatchShaderType.VERTEX, "#version 330 core\nvoid main() {}\n");
		transformed.put(PatchShaderType.GEOMETRY, null);
		transformed.put(PatchShaderType.TESS_CONTROL, null);
		transformed.put(PatchShaderType.TESS_EVAL, null);
		Map<PatchShaderType, ShaderSourceMap> sourceMaps = new EnumMap<>(PatchShaderType.class);
		sourceMaps.put(PatchShaderType.VERTEX, ShaderSourceMap.parse(transformed.get(PatchShaderType.VERTEX)));

		assertDoesNotThrow(() -> TransformPatcher.appendSourceMapMetadata(transformed, sourceMaps));

		assertNull(transformed.get(PatchShaderType.GEOMETRY));
		assertNull(transformed.get(PatchShaderType.TESS_CONTROL));
		assertNull(transformed.get(PatchShaderType.TESS_EVAL));
	}
}
