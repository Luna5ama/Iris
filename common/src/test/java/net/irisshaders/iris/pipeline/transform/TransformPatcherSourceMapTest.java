package net.irisshaders.iris.pipeline.transform;

import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.statement.Statement;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import net.irisshaders.iris.shaderpack.include.ShaderSourceMap;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformPatcherSourceMapTest {
	@Test
	void checksEachUniqueIdentifierNameForInternalPrefixes() {
		var tree = new ASTParser().parseTranslationUnit(RootSupplier.EXACT_UNORDERED, """
			#version 330
			void main() {
				vec4 allowed = vec4(0.0);
				iris_internal = allowed + allowed + allowed;
			}
			""");

		assertEquals("iris_internal", TransformPatcher.findInternalIdentifier(tree.getRoot()));

		var allowedTree = new ASTParser().parseTranslationUnit(RootSupplier.EXACT_UNORDERED, """
			#version 330
			void main() { vec4 allowed = vec4(0.0); }
			""");
		assertNull(TransformPatcher.findInternalIdentifier(allowedTree.getRoot()));
	}

	@Test
	void marksOnlyGeneratedDeclarationsAndStatementsWithOneSharedLocation() {
		var tree = new ASTParser().parseTranslationUnit(RootSupplier.EXACT_UNORDERED_ED_EXACT, """
			#version 330
			void main() {
				if (true) { gl_Position = vec4(0.0); }
			}
			""");

		TransformPatcher.markGeneratedSourceLocations(tree);

		Object generatedLocation = null;
		for (var entry : tree.getRoot().nodeIndex.index.entrySet()) {
			if (!ExternalDeclaration.class.isAssignableFrom(entry.getKey())
				&& !Statement.class.isAssignableFrom(entry.getKey())) {
				continue;
			}
			for (var node : entry.getValue()) {
				assertTrue(node.getSourceLocation().canPrint());
				if (generatedLocation == null) {
					generatedLocation = node.getSourceLocation();
				} else {
					assertSame(generatedLocation, node.getSourceLocation());
				}
			}
		}
	}

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
