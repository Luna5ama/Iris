package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncludeProcessorTest {
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
	}
}
