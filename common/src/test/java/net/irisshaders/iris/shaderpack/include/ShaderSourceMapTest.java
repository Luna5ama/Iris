package net.irisshaders.iris.shaderpack.include;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShaderSourceMapTest {
	@Test
	void preservesUnmappedSourceWithoutCopyingIt() {
		String source = new String("#version 330\nvoid main() {}\n");

		assertSame(source, ShaderSourceMap.parse(source).sourceWithoutMetadata());
	}

	@Test
	void reusesParsedMetadataForTheSameSourceInstance() {
		String source = ShaderSourceMap.appendMetadata("#version 330\n", Map.of(1, "/shaders/main.fsh"));

		assertSame(ShaderSourceMap.parse(source), ShaderSourceMap.parse(source));
	}

	@Test
	void removesMetadataWithCrLfLineEndings() {
		String encodedPath = Base64.getUrlEncoder().withoutPadding()
			.encodeToString("/shaders/main.fsh".getBytes(StandardCharsets.UTF_8));
		String source = "#version 330\r\n// IRIS_SOURCE 1 " + encodedPath + "\r\nvoid main() {}\r\n";

		ShaderSourceMap sourceMap = ShaderSourceMap.parse(source);

		assertEquals("#version 330\r\nvoid main() {}\r\n", sourceMap.sourceWithoutMetadata());
		assertEquals(Map.of(1, "/shaders/main.fsh"), sourceMap.sourcePaths());
	}

	@Test
	void preservesMarkerTextThatIsNotACompleteMetadataLine() {
		String source = "#version 330\n// mention // IRIS_SOURCE 1 invalid! here\n";

		assertSame(source, ShaderSourceMap.parse(source).sourceWithoutMetadata());
	}

	@Test
	void rewritesCommonDriverLocationsAndRemovesMetadataBeforeCompilation() {
		String source = ShaderSourceMap.appendMetadata("#version 330\nvoid main() {}\n", Map.of(
			1, "/shaders/lib/common.glsl",
			2, "/shaders/main.fsh"
		));
		ShaderSourceMap sourceMap = ShaderSourceMap.parse(source);
		String log = """
			ERROR: 1:17: 'broken' : undeclared identifier
			2(5) : error C0000: syntax error
			1:21(4): error: unexpected token
			""";

		assertEquals("""
			ERROR: /shaders/lib/common.glsl:17: 'broken' : undeclared identifier
			/shaders/main.fsh:5 : error C0000: syntax error
			/shaders/lib/common.glsl:21(4): error: unexpected token

			Shader source map:
			  1 = /shaders/lib/common.glsl
			  2 = /shaders/main.fsh""", sourceMap.remapLog(log));
		assertFalse(sourceMap.sourceWithoutMetadata().contains("IRIS_SOURCE"));
	}

	@Test
	void doesNotTreatAColumnLocationAsAnotherSourceLocation() {
		String source = ShaderSourceMap.appendMetadata("#version 330\n", Map.of(
			1, "/shaders/main.fsh",
			17, "/shaders/unrelated.glsl"
		));

		String remapped = ShaderSourceMap.parse(source).remapLog("1:17(4): error: unexpected token");

		assertEquals("""
			/shaders/main.fsh:17(4): error: unexpected token

			Shader source map:
			  1 = /shaders/main.fsh""", remapped);
	}

	@Test
	void labelsGeneratedShaderCodeSeparatelyFromPackSources() {
		ShaderSourceMap sourceMap = ShaderSourceMap.parse("#version 330\n")
			.withSourcePath(1_000_000_000, "<Iris-generated shader code>");

		assertEquals("""
			ERROR: <Iris-generated shader code>:1: generated declaration failed

			Shader source map:
			  1000000000 = <Iris-generated shader code>""",
			sourceMap.remapLog("ERROR: 1000000000:1: generated declaration failed"));
	}
}
