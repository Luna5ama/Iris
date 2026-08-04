package net.irisshaders.iris.shaderpack.preprocessor;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludedSource;
import net.irisshaders.iris.shaderpack.include.ShaderSourceMap;
import net.irisshaders.iris.shaderpack.include.SourceLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JcppSourceMappingTest {
	@Test
	void emitsPortableLineDirectivesAcrossIncludedFilesAndConditionals() {
		AbsolutePackPath main = AbsolutePackPath.fromAbsolutePath("/shaders/main.fsh");
		AbsolutePackPath library = AbsolutePackPath.fromAbsolutePath("/shaders/lib/common.glsl");
		IncludedSource source = new IncludedSource(ImmutableList.of(
			new SourceLine("#version 330", main, 1),
			new SourceLine("vec3 helper() {", library, 1),
			new SourceLine("return vec3(1.0);", library, 2),
			new SourceLine("}", library, 3),
			new SourceLine("#if 0", main, 3),
			new SourceLine("float ignored = broken;", library, 20),
			new SourceLine("#endif", main, 4),
			new SourceLine("void main() { broken(); }", main, 5)
		));

		String processed = JcppProcessor.glslPreprocessSource(source, List.of());

		assertTrue(processed.contains("#line 1 1\nvec3 helper()"));
		assertTrue(processed.contains("#line 5 2\nvoid main()"));
		assertFalse(processed.contains("float ignored"));
		assertFalse(processed.contains("\"<no file>\""));
		assertTrue(processed.contains("// IRIS_SOURCE "));
		assertTrue(ShaderSourceMap.parse(processed).sourcePaths().containsValue("/shaders/lib/common.glsl"));
		assertTrue(ShaderSourceMap.parse(processed).sourcePaths().containsValue("/shaders/main.fsh"));
	}

	@Test
	void cacheKeyIncludesPhysicalSourceOrigins() {
		String shader = "#version 330\nvoid main() {}\n";
		IncludedSource first = sourceAtPath(shader, "/shaders/first.fsh");
		IncludedSource second = sourceAtPath(shader, "/shaders/second.fsh");

		String firstProcessed = JcppProcessor.glslPreprocessSource(first, List.of());
		String secondProcessed = JcppProcessor.glslPreprocessSource(second, List.of());

		assertNotEquals(firstProcessed, secondProcessed);
		assertTrue(ShaderSourceMap.parse(firstProcessed).sourcePaths().containsValue("/shaders/first.fsh"));
		assertTrue(ShaderSourceMap.parse(secondProcessed).sourcePaths().containsValue("/shaders/second.fsh"));
	}

	private static IncludedSource sourceAtPath(String source, String path) {
		AbsolutePackPath packPath = AbsolutePackPath.fromAbsolutePath(path);
		ImmutableList.Builder<SourceLine> lines = ImmutableList.builder();
		String[] split = source.split("\\R");
		for (int i = 0; i < split.length; i++) {
			lines.add(new SourceLine(split[i], packPath, i + 1));
		}
		return new IncludedSource(lines.build());
	}
}
