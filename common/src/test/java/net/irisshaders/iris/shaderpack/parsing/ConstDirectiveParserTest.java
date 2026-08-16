package net.irisshaders.iris.shaderpack.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstDirectiveParserTest {
	@Test
	void findsDirectivesAcrossAllNewlineSequences() {
		String source = "ordinary shader line\n"
			+ "const int first = 1;\r\n"
			+ "const float second = 2.5;\u000B"
			+ "const vec2 third = vec2(3);\f"
			+ "const ivec3 fourth = ivec3(4);\u0085"
			+ "const vec4 fifth = vec4(5);\u2028"
			+ "const bool sixth = true;\u2029"
			+ "not const bool ignored = false;";

		var directives = ConstDirectiveParser.findDirectives(source);

		assertEquals(6, directives.size());
		assertEquals("first", directives.get(0).getKey());
		assertEquals("1", directives.get(0).getValue());
		assertEquals(ConstDirectiveParser.Type.BOOL, directives.get(5).getType());
		assertEquals("true", directives.get(5).getValue());
	}

	@Test
	void preservesSingleLineParsingBehavior() {
		var directive = ConstDirectiveParser.findDirectiveInLine("  const vec4 color = vec4(1.0); trailing").orElseThrow();

		assertEquals(ConstDirectiveParser.Type.VEC4, directive.getType());
		assertEquals("color", directive.getKey());
		assertEquals("vec4(1.0)", directive.getValue());
	}
}
