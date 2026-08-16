package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonTransformerTest {
	@Test
	void findsIdentifierPrefixesWithAnExactIdentifierIndex() {
		ASTParser parser = new ASTParser();
		var tree = parser.parseTranslationUnit(RootSupplier.EXACT_UNORDERED_ED_EXACT, """
			#version 330
			uniform vec4 gl_MultiTexCoord0;
			uniform vec4 gl_MultiTexCoord7;
			uniform vec4 unrelated;
			void main() { gl_Position = gl_MultiTexCoord0; }
			""");

		Set<String> matches = CommonTransformer.identifiersByPrefix(tree.getRoot(), "gl_MultiTexCoord")
			.map(identifier -> identifier.getName())
			.collect(Collectors.toSet());

		assertEquals(Set.of("gl_MultiTexCoord0", "gl_MultiTexCoord7"), matches);
	}
}
