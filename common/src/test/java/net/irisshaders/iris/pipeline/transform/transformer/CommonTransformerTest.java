package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.util.Type;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonTransformerTest {
	@Test
	void findsIdentifierPrefixesWithAnExactIdentifierIndex() {
		ASTParser parser = new ASTParser();
		var tree = parser.parseTranslationUnit(RootSupplier.EXACT_UNORDERED, """
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

	@Test
	void checksDeclarationsWithoutAnExternalDeclarationIndex() {
		ASTParser parser = new ASTParser();
		var declaredTree = parser.parseTranslationUnit(RootSupplier.EXACT_UNORDERED, """
			#version 330
			uniform vec4 existing;
			void main() {}
			""");
		int declaredChildren = declaredTree.getChildren().size();

		CommonTransformer.addIfNotExists(declaredTree.getRoot(), parser, declaredTree, "existing",
			Type.F32VEC4, StorageType.UNIFORM);

		assertEquals(declaredChildren, declaredTree.getChildren().size());

		var referencedTree = parser.parseTranslationUnit(RootSupplier.EXACT_UNORDERED, """
			#version 330
			void main() { existing = vec4(1.0); }
			""");
		int referencedChildren = referencedTree.getChildren().size();

		CommonTransformer.addIfNotExists(referencedTree.getRoot(), parser, referencedTree, "existing",
			Type.F32VEC4, StorageType.UNIFORM);

		assertEquals(referencedChildren + 1, referencedTree.getChildren().size());
	}
}
