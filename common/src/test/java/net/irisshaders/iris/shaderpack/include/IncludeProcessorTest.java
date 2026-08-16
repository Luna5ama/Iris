package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IncludeProcessorTest {
	@Test
	void skipsRepeatedGuardedIncludesWithoutReaddingSourceRanges(@TempDir Path packRoot) throws Exception {
		Path shaders = Files.createDirectories(packRoot.resolve("shaders/lib"));
		Files.writeString(packRoot.resolve("shaders/main.csh"), """
			#version 430
			#include "/shaders/lib/common.glsl"
			layout(local_size_x = 1) in;
			#include "/shaders/lib/common.glsl"
			void main() {}
			""");
		Files.writeString(shaders.resolve("common.glsl"), """
			#ifndef COMMON_GLSL
			#define COMMON_GLSL
			const uint VALUE = 1u;
			#endif
			""");

		AbsolutePackPath rootPath = AbsolutePackPath.fromAbsolutePath("/shaders/main.csh");
		IncludeGraph graph = new IncludeGraph(packRoot, ImmutableList.of(rootPath), false);

		assertEquals(
			ImmutableList.of(
				"#version 430",
				"#ifndef COMMON_GLSL",
				"#define COMMON_GLSL",
				"const uint VALUE = 1u;",
				"#endif",
				"layout(local_size_x = 1) in;",
				"void main() {}"
			),
			new IncludeProcessor(graph).getIncludedFile(rootPath)
		);
	}

	@Test
	void preservesPhysicalSourceLocationsWhenExpandingIncludes(@TempDir Path packRoot) throws Exception {
		Path shaders = Files.createDirectories(packRoot.resolve("shaders/lib"));
		Files.writeString(packRoot.resolve("shaders/main.fsh"), """
			#version 330
			#include "/shaders/lib/common.glsl"
			void main() {
				broken();
			}
			""");
		Files.writeString(shaders.resolve("common.glsl"), """
			vec3 helper() {
				return vec3(1.0);
			}
			""");

		AbsolutePackPath rootPath = AbsolutePackPath.fromAbsolutePath("/shaders/main.fsh");
		IncludeGraph graph = new IncludeGraph(packRoot, ImmutableList.of(rootPath), false);

		IncludedSource source = new IncludeProcessor(graph).getIncludedSource(rootPath);

		assertEquals(7, source.lines().size());
		assertEquals("#version 330", source.lines().get(0).text());
		assertEquals("/shaders/main.fsh", source.lines().get(0).path().getPathString());
		assertEquals(1, source.lines().get(0).line());
		assertEquals("vec3 helper() {", source.lines().get(1).text());
		assertEquals("/shaders/lib/common.glsl", source.lines().get(1).path().getPathString());
		assertEquals(1, source.lines().get(1).line());
		assertEquals("void main() {", source.lines().get(4).text());
		assertEquals("/shaders/main.fsh", source.lines().get(4).path().getPathString());
		assertEquals(3, source.lines().get(4).line());

		IncludedSource secondExpansion = new IncludeProcessor(graph).getIncludedSource(rootPath);
		for (int i = 0; i < source.lines().size(); i++) {
			assertSame(source.lines().get(i), secondExpansion.lines().get(i));
		}
	}
}
