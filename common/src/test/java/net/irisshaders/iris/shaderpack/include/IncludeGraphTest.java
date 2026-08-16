package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncludeGraphTest {
	@Test
	void splitsEveryJavaLineBreakWithoutTrailingEmptyLines() {
		assertEquals(ImmutableList.of(""), IncludeGraph.splitLines(""));
		assertEquals(ImmutableList.of(), IncludeGraph.splitLines("\r\n\u2028"));
		assertEquals(
			ImmutableList.of("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth"),
			IncludeGraph.splitLines("first\r\nsecond\nthird\u000Bfourth\ffifth\rsixth\u0085seventh\u2028eighth\u2029")
		);
		assertEquals(ImmutableList.of("first", "", "third"), IncludeGraph.splitLines("first\n\nthird\n\n"));
	}

	@Test
	void acceptsMultipleBranchesWithSharedDependencies(@TempDir Path packRoot) throws Exception {
		Files.writeString(packRoot.resolve("main.glsl"), "#include \"left.glsl\"\n#include \"right.glsl\"\n");
		Files.writeString(packRoot.resolve("left.glsl"), "#include \"shared.glsl\"\n");
		Files.writeString(packRoot.resolve("right.glsl"), "#include \"shared.glsl\"\n");
		Files.writeString(packRoot.resolve("shared.glsl"), "void shared() {}\n");

		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/main.glsl");

		assertDoesNotThrow(() -> new IncludeGraph(packRoot, ImmutableList.of(start), false));
	}

	@Test
	void stillRejectsCyclesReachedThroughSharedDependencies(@TempDir Path packRoot) throws Exception {
		Files.writeString(packRoot.resolve("main.glsl"), "#include \"left.glsl\"\n#include \"right.glsl\"\n");
		Files.writeString(packRoot.resolve("left.glsl"), "#include \"shared.glsl\"\n");
		Files.writeString(packRoot.resolve("right.glsl"), "#include \"shared.glsl\"\n");
		Files.writeString(packRoot.resolve("shared.glsl"), "#include \"right.glsl\"\n");

		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/main.glsl");

		assertThrows(IllegalStateException.class,
			() -> new IncludeGraph(packRoot, ImmutableList.of(start), false));
	}
}
